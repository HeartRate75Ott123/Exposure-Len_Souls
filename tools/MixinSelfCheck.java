import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.spongepowered.asm.mixin.injection.selectors.TargetSelector;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mixin 自检（交付前必跑）——把「mixin 写错了但线上看不出来」的两类事故挡在交付之前：
 *
 * <ol>
 *   <li><b>@At target 选择器语法</b>：用 Mixin 自己的 {@code TargetSelector.parseAndValidate} 校验。
 *       最常踩的坑：<b>照抄 javap 的显示格式</b> {@code name:(desc)ret}（带冒号）。
 *       Mixin 的方法选择器是 {@code Lowner;name(desc)ret}（<b>无冒号</b>），字段才是 {@code name:Desc}。
 *       写错的后果是 {@code InvalidMemberDescriptorException: Invalid name: xxx:}，整个 mixin apply 失败
 *       ——而镜头里看起来只是「改了跟没改一样」。</li>
 *   <li><b>注入点是否真的存在 / ordinal 数错</b>：把 target 里的 {@code Lowner;name(desc)ret} 拿到
 *       mixin 目标类的对应方法里数一数匹配的 INVOKE 条数，并检查 {@code ordinal} 是否越界。
 *       注意<b>源码树 ≠ 运行 jar</b>：一定要按 jar 的字节码数（本工具就是数 jar 的）。</li>
 * </ol>
 *
 * 用法（在仓库根执行）：
 * <pre>
 *   javac -cp "&lt;sponge-mixin.jar&gt;;&lt;asm.jar&gt;;&lt;asm-tree.jar&gt;" -d build/mixincheck tools/MixinSelfCheck.java
 *   java  -cp "&lt;同上&gt;;build/mixincheck" MixinSelfCheck build/libs/lensouls-X.Y.Z.jar libs/legendary_monsters.jar libs/cataclysm.jar
 * </pre>
 * 第 1 个参数是待检的模组 jar（自动遍历其中 {@code com/plumejade/lensouls/mixin/} 下的所有 mixin 类），
 * 其余参数是「解析目标类用」的 classpath（jar 或目录），解析不到的类会跳过存在性检查、只做语法校验。
 */
public class MixinSelfCheck {

    /** 需要逐个检查 target 的注入注解（按 descriptor 匹配，避免依赖注解类的加载） */
    private static final List<String> INJECTOR_ANNOTATIONS = List.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/Slice;");

    private static final Map<String, ZipFile> ZIP_CACHE = new HashMap<>();
    private static final List<Path> LOOKUP = new ArrayList<>();

    // ------------------------------------------------------------------ classpath

    static byte[] findClass(String internalName) {
        String entry = internalName + ".class";
        for (Path p : LOOKUP) {
            try {
                if (Files.isDirectory(p)) {
                    Path f = p.resolve(entry);
                    if (Files.isRegularFile(f)) return Files.readAllBytes(f);
                } else {
                    ZipFile zf = ZIP_CACHE.computeIfAbsent(p.toString(), k -> {
                        try {
                            return new ZipFile(k);
                        } catch (Exception e) {
                            return null;
                        }
                    });
                    if (zf == null) continue;
                    ZipEntry e = zf.getEntry(entry);
                    if (e != null) {
                        try (InputStream in = zf.getInputStream(e)) {
                            return in.readAllBytes();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ ASM 小工具

    static String str(Object v) {
        return v instanceof String s ? s : null;
    }

    static List<String> strList(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof String s) {
            out.add(s);
        } else if (v instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof String s) out.add(s);
            }
        }
        return out;
    }

    static AnnotationNode annotation(List<AnnotationNode> list, String desc) {
        if (list == null) return null;
        for (AnnotationNode a : list) {
            if (desc.equals(a.desc)) return a;
        }
        return null;
    }

    /**
     * 合并 RuntimeVisible 与 RuntimeInvisible 注解。
     * <p>坑：Mixin 的 {@code @Mixin} 是 <b>CLASS 保留</b>（落在 RuntimeInvisibleAnnotations），
     * 而 {@code @Inject}/{@code @ModifyArg}/{@code @At} 是 RUNTIME。只读 visible 会「一条都读不到」。
     */
    static List<AnnotationNode> allAnnotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> out = new ArrayList<>();
        if (visible != null) out.addAll(visible);
        if (invisible != null) out.addAll(invisible);
        return out;
    }

    static Object value(AnnotationNode a, String key) {
        if (a == null || a.values == null) return null;
        for (int i = 0; i + 1 < a.values.size(); i += 2) {
            if (key.equals(a.values.get(i))) return a.values.get(i + 1);
        }
        return null;
    }

    /** 统计 classBytes.methodName（可能是 "name" 或 "name(desc)"）里 owner.name(desc) 的 INVOKE 条数 */
    static int countInvokes(byte[] classBytes, String methodSpec, String owner, String name, String desc) {
        ClassNode cn = new ClassNode();
        new ClassReader(classBytes).accept(cn, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        String wantName = methodSpec;
        String wantDesc = null;
        int par = methodSpec.indexOf('(');
        if (par > 0) {
            wantName = methodSpec.substring(0, par);
            wantDesc = methodSpec.substring(par);
        }
        int n = 0;
        for (MethodNode mn : cn.methods) {
            if (!mn.name.equals(wantName)) continue;
            if (wantDesc != null && !mn.desc.equals(wantDesc)) continue;
            for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode mi
                        && mi.owner.equals(owner) && mi.name.equals(name) && mi.desc.equals(desc)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 解析 Lowner;name(args)ret → [owner, name, 完整描述符(含返回类型)] */
    static String[] parseMemberRef(String target) {
        if (target == null || !target.startsWith("L")) return null;
        int semi = target.indexOf(';');
        int paren = target.indexOf('(');
        if (semi < 0 || paren < 0 || paren < semi) return null;
        String owner = target.substring(1, semi);
        String name = target.substring(semi + 1, paren);
        // 坑：方法描述符是 "(参数)返回类型"，返回类型必须一起带上，截到 ')' 会导致全部误判为「找不到」
        return new String[]{owner, name, target.substring(paren)};
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        Path modJar = Path.of(args[0]);
        for (int i = 1; i < args.length; i++) LOOKUP.add(Path.of(args[i]));

        int syntaxFails = 0;
        int pointFails = 0;
        int checked = 0;
        int skipped = 0;

        try (ZipFile zf = new ZipFile(modJar.toFile())) {
            List<String> mixinClasses = new ArrayList<>();
            var en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.getName().startsWith("com/plumejade/lensouls/mixin/") && e.getName().endsWith(".class")) {
                    mixinClasses.add(e.getName());
                }
            }
            mixinClasses.sort(String::compareTo);

            for (String entry : mixinClasses) {
                byte[] bytes;
                try (InputStream in = zf.getInputStream(zf.getEntry(entry))) {
                    bytes = in.readAllBytes();
                }
                ClassNode cn = new ClassNode();
                new ClassReader(bytes).accept(cn, ClassReader.SKIP_FRAMES);

                String[] mixinTargets = strList(value(annotation(allAnnotations(cn.visibleAnnotations, cn.invisibleAnnotations),
                        "Lorg/spongepowered/asm/mixin/Mixin;"), "targets")).toArray(new String[0]);
                if (mixinTargets.length == 0) continue;   // 用 @Mixin(SomeClass.class) 的按类引用，交给编译器

                List<String> problems = new ArrayList<>();
                List<String> notes = new ArrayList<>();

                for (MethodNode mn : cn.methods) {
                    for (AnnotationNode ann : allAnnotations(mn.visibleAnnotations, mn.invisibleAnnotations)) {
                        if (!INJECTOR_ANNOTATIONS.contains(ann.desc)) continue;
                        List<String> methodSpecs = strList(value(ann, "method"));
                        List<AnnotationNode> ats = new ArrayList<>();
                        Object at = value(ann, "at");
                        if (at instanceof AnnotationNode an) ats.add(an);
                        if (at instanceof List<?> l) {
                            for (Object o : l) if (o instanceof AnnotationNode an) ats.add(an);
                        }
                        for (AnnotationNode atNode : ats) {
                            for (String target : strList(value(atNode, "target"))) {
                                checked++;
                                // ① 语法
                                try {
                                    TargetSelector.parseAndValidate(target, null);
                                } catch (Throwable t) {
                                    syntaxFails++;
                                    problems.add("[语法] " + mn.name + " @" + shorthand(ann.desc)
                                            + " target 非法: " + t.getClass().getSimpleName() + ": " + t.getMessage()
                                            + "\n        target = " + target);
                                    continue;
                                }
                                // ② 注入点存在性 + ordinal
                                String[] ref = parseMemberRef(target);
                                if (ref == null || methodSpecs.isEmpty()) continue;
                                byte[] targetCls = findClass(mixinTargets[0].replace('.', '/'));
                                if (targetCls == null) {
                                    skipped++;
                                    continue;
                                }
                                boolean foundOwner = findClass(ref[0]) != null;
                                int count = 0;
                                int perMethod = -1;
                                for (String spec : methodSpecs) {
                                    int c = countInvokes(targetCls, spec, ref[0], ref[1], ref[2]);
                                    if (c > 0) perMethod = c;
                                    count += c;
                                }
                                Object ordObj = value(atNode, "ordinal");
                                int ordinal = ordObj instanceof Integer oi ? oi : -1;
                                if (count == 0) {
                                    pointFails++;
                                    problems.add("[注入点] " + mn.name + " @" + shorthand(ann.desc)
                                            + " 在 " + mixinTargets[0] + "." + String.join(",", methodSpecs)
                                            + " 里找不到 " + ref[0] + "." + ref[1] + ref[2]
                                            + (foundOwner ? "" : "（注意：owner 类未提供到 classpath，结论不完整）"));
                                } else if (ordinal >= 0 && ordinal >= count) {
                                    pointFails++;
                                    problems.add("[ordinal] " + mn.name + " @" + shorthand(ann.desc)
                                            + " ordinal=" + ordinal + " 越界（该方法里只匹配到 " + count + " 条）");
                                } else {
                                    notes.add(shorthand(ann.desc) + " " + ref[1] + " 命中 " + count
                                            + " 条" + (ordinal >= 0 ? "，ordinal=" + ordinal + "（该条序号 " + (ordinal + 1) + "）" : "")
                                            + (perMethod >= 0 && perMethod != count ? "（按方法细分：" + perMethod + "）" : ""));
                                }
                            }
                        }
                    }
                }

                if (!problems.isEmpty()) {
                    System.out.println("✗ " + entry.replace('/', '.').replace(".class", ""));
                    for (String p : problems) System.out.println("   " + p);
                } else if (!notes.isEmpty() && System.getenv("MIXIN_CHECK_VERBOSE") != null) {
                    System.out.println("✓ " + entry.replace('/', '.').replace(".class", ""));
                    for (String n : notes) System.out.println("   " + n);
                }
            }
        }

        System.out.println();
        System.out.printf("target 选择器校验 %d 条：语法错误 %d、注入点错误 %d、跳过存在性检查 %d%n",
                checked, syntaxFails, pointFails, skipped);
        if (syntaxFails + pointFails > 0) {
            System.out.println("结论：存在会在 apply 期失败（或静默不生效）的 mixin，别交付。");
            System.exit(1);
        }
        System.out.println("结论：全部通过（apply 期不会因 target 写法/序号失败）。");
    }

    static String shorthand(String annotationDesc) {
        int slash = annotationDesc.lastIndexOf('/');
        return annotationDesc.substring(slash + 1, annotationDesc.length() - 1);
    }

    static { Opcodes.class.getName(); }   // 保持 asm 依赖显式
}
