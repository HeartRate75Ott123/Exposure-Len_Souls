import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 从 BOSS 镜魂物品贴图 PNG 中自动提取 4 个主色 → 生成 BossOutlineColors Java 代码。
 * <p>
 * 用法（在项目根目录）：
 *   javac tools/PaletteExtractor.java -d tools/
 *   java -cp tools/ PaletteExtractor /path/to/texture.png BossName
 * <p>
 * 例：java -cp tools/ PaletteExtractor src/main/resources/assets/lensouls/textures/item/ignis_soul.png IGNIS
 */
public class PaletteExtractor {

    record Rgb(int r, int g, int b) {
        /** 量化到 32 级/通道（共 32768 色）防止噪点干扰 */
        Rgb quantize() {
            return new Rgb(r >> 3 << 3, g >> 3 << 3, b >> 3 << 3);
        }

        float hue() {
            float rn = r / 255f, gn = g / 255f, bn = b / 255f;
            float max = Math.max(rn, Math.max(gn, bn));
            float min = Math.min(rn, Math.min(gn, bn));
            float d = max - min;
            if (d == 0) return 0;
            float h = 0;
            if (max == rn) h = (gn - bn) / d + (gn < bn ? 6 : 0);
            else if (max == gn) h = (bn - rn) / d + 2;
            else h = (rn - gn) / d + 4;
            return h / 6;
        }

        float luminance() {
            return 0.299f * r + 0.587f * g + 0.114f * b;
        }

        String hex() {
            return String.format("#%02x%02x%02x", r, g, b);
        }

        String javaSource() {
            return String.format("hex(0x%02x%02x%02x)", r, g, b);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("用法: PaletteExtractor <png-path> <BOSS_NAME>");
            System.err.println("例:   PaletteExtractor ignis_soul.png IGNIS");
            System.exit(1);
        }

        BufferedImage img = ImageIO.read(new File(args[0]));
        String name = args[1].toUpperCase();

        // 读取所有像素 → 量化 → 统计频率
        Map<Rgb, Integer> freq = new HashMap<>();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgba = img.getRGB(x, y);
                int a = (rgba >> 24) & 0xFF;
                if (a < 128) continue; // 跳过透明像素
                int r = (rgba >> 16) & 0xFF;
                int g = (rgba >> 8) & 0xFF;
                int b = rgba & 0xFF;
                Rgb c = new Rgb(r, g, b).quantize();
                freq.merge(c, 1, Integer::sum);
            }
        }

        if (freq.isEmpty()) {
            System.err.println("错误: 没有找到不透明像素");
            System.exit(1);
        }

        // 按频率排序取前 8 → 再按色相均匀选取 4 个
        var top = freq.entrySet().stream()
                .sorted(Map.Entry.<Rgb, Integer>comparingByValue().reversed())
                .limit(8)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 按 luminance 排序 → 隔点取（明→暗→明→暗 交错排得到好渐变）
        top.sort(Comparator.comparingDouble(Rgb::luminance));

        List<Rgb> palette = new ArrayList<>();
        if (top.size() >= 4) {
            // 取 luminance 两端各两个 + 中间一两个
            palette.add(top.get(0));
            palette.add(top.get(top.size() / 3));
            palette.add(top.get(top.size() * 2 / 3));
            palette.add(top.get(top.size() - 1));
        } else {
            palette.addAll(top);
            while (palette.size() < 4) palette.add(palette.get(palette.size() - 1));
        }

        // 按色相排序成好看的渐变循环
        palette.sort(Comparator.comparingDouble(Rgb::hue));

        // ===== 输出 =====
        System.out.println("// ====== " + name + " — 配色方案 ======");
        System.out.println("来源: " + args[0]);
        System.out.println("提取色: " + palette.stream().map(Rgb::hex).collect(Collectors.joining(", ")));
        System.out.println();
        System.out.println("/** " + name + " — TODO: 主题名称 */");
        System.out.println("public static final BossOutlineColors " + name + " = new BossOutlineColors(");
        for (int i = 0; i < palette.size(); i++) {
            String comma = (i < palette.size() - 1) ? "," : ");";
            System.out.println("        " + palette.get(i).javaSource() + comma);
        }
        System.out.println("        1.0f, 3.0f");
        System.out.println(");");
        System.out.println();
        System.out.println("// 复制以上代码到 BossOutlineColors.java");
    }
}
