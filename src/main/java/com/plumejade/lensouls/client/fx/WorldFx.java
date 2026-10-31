package com.plumejade.lensouls.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 世界空间发光带 / 圆环（自定义着色器版）。
 *
 * <h3>为什么是 TRIANGLES（每段两个三角形）而不是 TRIANGLE_STRIP</h3>
 * 一段一个 QUAD 时，相邻两段在接缝处各自的宽度方向略有差异，会互相重叠出一个小楔形；
 * 加色混合下重叠区亮度翻倍 —— 视觉上就是"线上一颗颗亮斑 / 衔接不自然"。
 * 解法是<b>相邻截面共用同一份 left/right</b>（{@link #polyline} 先算好整条线的截面，
 * 再逐段补两个三角形），这样既没有缝隙也没有重叠。
 * <p>
 * 不再用 TRIANGLE_STRIP：一次 draw 里要画好几条互不相连的缎带（磁力线三股、磁笼三环、
 * 多目标并存…），strip 只能靠"退化三角形"把它们缝起来，而桥接只要顶点选得不对就会
 * 产生<b>真实的三角形</b>，把上一条的末尾和新一条的开头连成一张斜跨薄片
 * （2026-10-31「光束里一道硬边」就是这么来的）。TRIANGLES 模式下各段互不依赖。
 */
public final class WorldFx {

    /** 世界特效着色器（RegisterShadersEvent 注册）。 */
    public static ShaderInstance shader;

    /**
     * 带宽方向的沿程平滑轮数（每轮 = 与左右邻居各取一半权重再归一化）。
     * 3 轮足以把"顺着光束看"时的 37°~63° 突转摊到与固有扭转同量级（约 12°~22°/段）。
     */

    /** 相邻截面之间允许的最大扭转角（弧度）：24°。超过就压回来，避免出现"宽平行四边形"色块。 */

    /** 一次 draw 的着色器参数。 */
    public static final class Params {
        public float time;
        public float pulseSpeed = 0.6F;
        public float pulseSharp = 0.055F;
        public float pulseCount = 1.0F;
        public float edgeSoft = 1.35F;
        public float coreBoost = 1.0F;
        public float flicker = 0.35F;
        public float grow = 1.0F;
        public float taper = 0.0F;
        public float alpha = 1.0F;
    }

    private WorldFx() {
    }

    public static Params params() {
        return new Params();
    }

    // =====================================================================
    //  draw
    // =====================================================================

    /**
     * 设好 uniform，返回可写顶点的 BufferBuilder（TRIANGLES；每段两个三角形）。
     * <p>
     * <b>为什么不再用 TRIANGLE_STRIP</b>（2026-10-31 修「光束里一道斜跨的硬边薄片」）：
     * 一次 draw 里要画好几条互不相连的缎带（磁力线 3 股、磁笼 3 环、多目标 …），
     * strip 只能用"退化三角形"把前一条的末尾与新一条的开头缝起来。旧实现只重复了<b>新条带</b>
     * 的首对顶点，于是接缝处 T1=(上条末 L, 上条末 R, 新条首 L)、T2=(上条末 R, 新条首 L, 新条首 R)
     * 是<b>两个真实三角形</b>——把上一条的终点和新一条的起点连成一张薄片（磁力线首尾各在天线两端，
     * 于是斜跨整条光束），加色混合下就是一道硬边。TRIANGLES 模式下各段互不依赖，无需任何桥接，
     * 段与段之间仍共用同一帧（同一条 left/right），既无缝隙也无重叠亮斑。
     */
    public static BufferBuilder begin(Params p) {
        if (shader != null) {
            set("FxTime", p.time);
            set("FxPulseSpeed", p.pulseSpeed);
            set("FxPulseSharp", p.pulseSharp);
            set("FxPulseCount", p.pulseCount);
            set("FxEdgeSoft", p.edgeSoft);
            set("FxCoreBoost", p.coreBoost);
            set("FxFlicker", p.flicker);
            set("FxGrow", p.grow);
            set("FxTaper", p.taper);
            set("FxAlpha", p.alpha);
        }
        return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    public static void flush(BufferBuilder buffer) {
        if (shader == null || buffer == null) return;
        MeshData mesh = buffer.build();
        if (mesh == null) return;
        RenderSystem.setShader(() -> shader);
        BufferUploader.drawWithShader(mesh);
    }

    private static void set(String name, float value) {
        if (shader == null) return;
        var uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    // =====================================================================
    //  几何
    // =====================================================================

    /** 折线 → 独立四边形（UV0.x 沿线，UV0.y 横向，相邻截面共用边 → 无重叠亮斑也无桥接薄片）。 */
    public static void polyline(BufferBuilder b, Matrix4f pose, Vector3f[] points, Vector3f[] rights,
                                float halfWidth, int r, int g, int bl, int a) {
        polyline(b, pose, points, rights, halfWidth, r, g, bl, a, null);
    }

    public static void polyline(BufferBuilder b, Matrix4f pose, Vector3f[] points, Vector3f[] rights,
                                float halfWidth, int r, int g, int bl, int a, float[] alphaFactors) {
        int n = points.length;
        if (n < 2) return;

        Vector3f[] frames = rights;
        if (frames == null) {
            // 没有显式带宽方向 = 自由发光带（磁力线 / 魂丝 / 光柱）→ 一律朝相机
            frames = cameraFacingFrames(points);
        }

        Vector3f[] left = new Vector3f[n];
        Vector3f[] right = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            Vector3f f = frames[i];
            if (f == null || !finite(f) || !finite(points[i])) continue;
            Vector3f offset = new Vector3f(f).mul(halfWidth);
            left[i] = new Vector3f(points[i]).sub(offset);
            right[i] = new Vector3f(points[i]).add(offset);
        }

        int prev = -1;
        for (int i = 0; i < n; i++) {
            if (left[i] == null) continue;
            if (prev >= 0) {
                float u0 = (float) prev / (n - 1);
                float u1 = (float) i / (n - 1);
                quad(b, pose, left[prev], right[prev], left[i], right[i], u0, u1,
                        r, g, bl, alphaOf(a, alphaFactors, prev), alphaOf(a, alphaFactors, i));
            }
            prev = i;
        }
    }

    /** 圆环（UV.x 沿圆周 → 脉冲绕圈跑）。 */
    public static void ring(BufferBuilder b, Matrix4f pose, Vector3f center,
                            Vector3f axisA, Vector3f axisB, float radius, float halfWidth,
                            int r, int g, int bl, int a, int segments) {
        int count = Math.max(8, segments);
        Vector3f[] inner = new Vector3f[count];
        Vector3f[] outer = new Vector3f[count];
        for (int i = 0; i < count; i++) {
            double t = Math.PI * 2.0 * i / count;
            Vector3f dir = new Vector3f(axisA).mul((float) Math.cos(t))
                    .add(new Vector3f(axisB).mul((float) Math.sin(t)));
            inner[i] = new Vector3f(center).add(new Vector3f(dir).mul(radius - halfWidth));
            outer[i] = new Vector3f(center).add(new Vector3f(dir).mul(radius + halfWidth));
        }
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;                    // 末段接回首点 → 闭合
            // 收尾那段的 u 走到 1.0（而不是跳回 0），否则脉冲会在接缝处被拉成一道长拖尾
            float u1 = (i + 1 == count) ? 1.0F : (float) (i + 1) / count;
            quad(b, pose, inner[i], outer[i], inner[j], outer[j], (float) i / count, u1, r, g, bl, a, a);
        }
    }

    /** 单段发光带。 */
    public static void segment(BufferBuilder b, Matrix4f pose, Vector3f from, Vector3f to,
                               float halfWidth, int r, int g, int bl, int a) {
        polyline(b, pose, new Vector3f[]{from, to}, null, halfWidth, r, g, bl, a);
    }

    // =====================================================================
    //  内部
    // =====================================================================

    /**
     * 一段缎带 = 两个三角形。相邻截面共用同一条 left/right，所以逐段拼出来既没有缝隙，
     * 也不会在接缝处重叠（加色混合下重叠区会亮一截）。
     */
    private static void quad(BufferBuilder b, Matrix4f pose,
                             Vector3f l0, Vector3f r0, Vector3f l1, Vector3f r1,
                             float u0, float u1, int r, int g, int bl, int a0, int a1) {
        if (!finite(l0) || !finite(r0) || !finite(l1) || !finite(r1)) return;
        // 三角形 1：(L0, R0, L1)
        vertex(b, pose, l0, u0, 0F, r, g, bl, a0);
        vertex(b, pose, r0, u0, 1F, r, g, bl, a0);
        vertex(b, pose, l1, u1, 0F, r, g, bl, a1);
        // 三角形 2：(R0, L1, R1)
        vertex(b, pose, r0, u0, 1F, r, g, bl, a0);
        vertex(b, pose, l1, u1, 0F, r, g, bl, a1);
        vertex(b, pose, r1, u1, 1F, r, g, bl, a1);
    }

    private static void vertex(BufferBuilder b, Matrix4f pose, Vector3f p,
                               float u, float v, int r, int g, int bl, int a) {
        b.addVertex(pose, p.x, p.y, p.z).setUv(u, v).setColor(r, g, bl, a);
    }

    private static int alphaOf(int base, float[] factors, int index) {
        if (factors == null || index >= factors.length) return base;
        float f = Math.max(0F, Math.min(1F, factors[index]));
        return (int) (base * f);
    }

    /**
     * 求每点的带宽方向：<b>严格 billboard</b>。
     * <p>
     * 相机在原点（几何都是相机相对坐标），带宽方向取 {@code normalize(cross(tangent, view))}：
     * 它同时垂直于切线与视线，因此缎带永远正对镜头、<b>投影宽度恒为满宽</b>，
     * 第一人称顺着光束看也不会缩成细线。
     * <p>
     * <b>为什么没有平滑/扭转上限</b>（2026-10-31 走过一次弯路，务必别再试）：
     * "垂直切线" + "垂直视线"两个约束<b>已经把方向定死（只差正负号）</b>，不存在可平滑的自由度；
     * 任何沿程平滑、向邻居插值、"扭转速率上限"都会把缎带转出视线平面 —— 实测偏离 13°~21°，
     * 投影宽度损失 2.6%~6.9%，表现就是光束中间被"捏细/咬掉一块"的缺口。严格 billboard 实测偏离 0.00°。
     * <p>
     * 这里只做两件必要的事：
     * <ol>
     *   <li><b>退化回退</b>：正顺着光束看时 {@code |cross| ≈ 0}，方向无定义 → 沿用上一点；</li>
     *   <li><b>符号连续性</b>：与上一点点积为负就取反。缎带剖面对称，取反不改变外观，
     *       但能避免相邻截面的 left/right 错位 —— 那是"四边形自穿成蝴蝶结、中间豁出一个楔形洞"的成因。</li>
     * </ol>
     */
    private static Vector3f[] cameraFacingFrames(Vector3f[] points) {
        int n = points.length;
        Vector3f[] frames = new Vector3f[n];
        Vector3f prev = null;
        for (int i = 0; i < n; i++) {
            Vector3f tangent = new Vector3f();
            if (i == 0) tangent.set(points[1]).sub(points[0]);
            else if (i == n - 1) tangent.set(points[i]).sub(points[i - 1]);
            else tangent.set(points[i + 1]).sub(points[i - 1]);
            if (tangent.lengthSquared() < 1.0E-10F) continue;
            tangent.normalize();

            Vector3f view = new Vector3f(-points[i].x, -points[i].y, -points[i].z);
            if (view.lengthSquared() < 1.0E-8F) continue;
            view.normalize();

            Vector3f right;
            Vector3f raw = tangent.cross(view);
            if (raw.lengthSquared() > 1.0E-10F) {
                right = raw.normalize();
            } else if (prev != null) {
                right = new Vector3f(prev);      // 退化：沿用上一点（≈仍在视线平面内）
            } else {
                right = tangent.cross(leastParallelAxis(tangent));
                if (right.lengthSquared() < 1.0E-8F) continue;
                right.normalize();
            }

            if (prev != null && right.dot(prev) < 0.0F) right.negate();
            frames[i] = right;
            prev = right;
        }
        // 个别点算不出来时，用相邻点补
        for (int i = 0; i < n; i++) {
            if (frames[i] != null) continue;
            for (int d = 1; d < n; d++) {
                int a = i - d;
                int b = i + d;
                if (a >= 0 && frames[a] != null) { frames[i] = new Vector3f(frames[a]); break; }
                if (b < n && frames[b] != null) { frames[i] = new Vector3f(frames[b]); break; }
            }
        }
        return frames;
    }

    /** 平行传输（旧实现，保留给退化情况与球面网格之外的用途）。 */
    private static Vector3f[] transportFrames(Vector3f[] points) {
        int n = points.length;
        Vector3f[] frames = new Vector3f[n];
        Vector3f tangent0 = new Vector3f(points[1]).sub(points[0]);
        if (tangent0.lengthSquared() < 1.0E-10F) tangent0.set(0F, 0F, 1F);
        tangent0.normalize();
        frames[0] = tangent0.cross(leastParallelAxis(tangent0));
        if (frames[0].lengthSquared() < 1.0E-8F) frames[0] = new Vector3f(1F, 0F, 0F);
        frames[0].normalize();

        for (int i = 1; i < n; i++) {
            Vector3f tangent = new Vector3f(points[i]).sub(points[i - 1]);
            if (tangent.lengthSquared() < 1.0E-10F) {
                frames[i] = new Vector3f(frames[i - 1]);
                continue;
            }
            tangent.normalize();
            Vector3f prev = frames[i - 1];
            Vector3f projected = new Vector3f(prev).sub(new Vector3f(tangent).mul(prev.dot(tangent)));
            if (projected.lengthSquared() < 1.0E-8F) {
                projected = tangent.cross(leastParallelAxis(tangent));
            }
            frames[i] = projected.normalize();
        }
        return frames;
    }

    private static Vector3f leastParallelAxis(Vector3f dir) {
        float ax = Math.abs(dir.x);
        float ay = Math.abs(dir.y);
        float az = Math.abs(dir.z);
        if (ay <= ax && ay <= az) return new Vector3f(0F, 1F, 0F);
        if (ax <= az) return new Vector3f(1F, 0F, 0F);
        return new Vector3f(0F, 0F, 1F);
    }

    private static boolean finite(Vector3f v) {
        return Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z);
    }
}
