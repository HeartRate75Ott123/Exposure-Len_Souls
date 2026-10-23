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
 * <h3>为什么是 TRIANGLE_STRIP 而不是一段一个四边形</h3>
 * 一段一个 QUAD 时，相邻两段在接缝处各自的宽度方向略有差异，会互相重叠出一个小楔形；
 * 加色混合下重叠区亮度翻倍 —— 视觉上就是"线上一颗颗亮斑 / 衔接不自然"。
 * 改成三角带后，接缝处的两个顶点是<b>共用</b>的，既没有缝隙也没有重叠，
 * 整条线亮度连续。多条带之间用"重复首对顶点"制造退化三角形来断开。
 */
public final class WorldFx {

    /** 世界特效着色器（RegisterShadersEvent 注册）。 */
    public static ShaderInstance shader;

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

    /** 设好 uniform，返回可写顶点的 BufferBuilder（TRIANGLE_STRIP；多条带用退化三角形断开）。 */
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
        return Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP,
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

    /** 折线 → 三角带（UV0.x 沿线，UV0.y 横向，接缝顶点共用 → 无重叠亮斑）。 */
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
        int valid = 0;
        for (int i = 0; i < n; i++) {
            Vector3f f = frames[i];
            if (f == null || !finite(f) || !finite(points[i])) continue;
            Vector3f offset = new Vector3f(f).mul(halfWidth);
            left[i] = new Vector3f(points[i]).sub(offset);
            right[i] = new Vector3f(points[i]).add(offset);
            valid++;
        }
        if (valid < 2) return;

        // 退化三角形断开上一条带
        int first = -1;
        for (int i = 0; i < n; i++) {
            if (left[i] != null) { first = i; break; }
        }
        if (first < 0) return;
        emitPair(b, pose, left[first], right[first], 0F, r, g, bl, alphaOf(a, alphaFactors, first));
        emitPair(b, pose, left[first], right[first], 0F, r, g, bl, alphaOf(a, alphaFactors, first));

        int emitted = 0;
        for (int i = first; i < n; i++) {
            if (left[i] == null) continue;
            float u = n <= 1 ? 0F : (float) i / (n - 1);
            emitPair(b, pose, left[i], right[i], u, r, g, bl, alphaOf(a, alphaFactors, i));
            emitted++;
        }
        if (emitted < 2) return;
    }

    /** 圆环（UV.x 沿圆周 → 脉冲绕圈跑）。 */
    public static void ring(BufferBuilder b, Matrix4f pose, Vector3f center,
                            Vector3f axisA, Vector3f axisB, float radius, float halfWidth,
                            int r, int g, int bl, int a, int segments) {
        int count = Math.max(8, segments);
        Vector3f firstInner = null;
        Vector3f firstOuter = null;

        for (int i = 0; i <= count; i++) {
            int idx = i % count;                       // 末点重复首点 → 闭合
            double t = Math.PI * 2.0 * idx / count;
            Vector3f dir = new Vector3f(axisA).mul((float) Math.cos(t))
                    .add(new Vector3f(axisB).mul((float) Math.sin(t)));
            Vector3f inner = new Vector3f(center).add(new Vector3f(dir).mul(radius - halfWidth));
            Vector3f outer = new Vector3f(center).add(new Vector3f(dir).mul(radius + halfWidth));
            if (i == 0) {
                firstInner = inner;
                firstOuter = outer;
                emitPair(b, pose, inner, outer, 0F, r, g, bl, a);
                emitPair(b, pose, inner, outer, 0F, r, g, bl, a);
                continue;
            }
            emitPair(b, pose, inner, outer, (float) idx / count, r, g, bl, a);
        }
        if (firstInner == null) return;
    }

    /** 单段发光带。 */
    public static void segment(BufferBuilder b, Matrix4f pose, Vector3f from, Vector3f to,
                               float halfWidth, int r, int g, int bl, int a) {
        polyline(b, pose, new Vector3f[]{from, to}, null, halfWidth, r, g, bl, a);
    }

    // =====================================================================
    //  内部
    // =====================================================================

    private static void emitPair(BufferBuilder b, Matrix4f pose, Vector3f left, Vector3f right,
                                 float u, int r, int g, int bl, int a) {
        if (!finite(left) || !finite(right)) return;
        b.addVertex(pose, left.x, left.y, left.z).setUv(u, 0F).setColor(r, g, bl, a);
        b.addVertex(pose, right.x, right.y, right.z).setUv(u, 1F).setColor(r, g, bl, a);
    }

    private static int alphaOf(int base, float[] factors, int index) {
        if (factors == null || index >= factors.length) return base;
        float f = Math.max(0F, Math.min(1F, factors[index]));
        return (int) (base * f);
    }

    /**
     * 求每点的带宽方向：<b>朝相机</b>（billboard）。
     * <p>
     * 几何都是相机相对坐标（相机在原点），所以带宽方向取 {@code normalize(cross(tangent, -point))}：
     * 这样无论从哪个角度看，缎带都正对镜头 —— 第一人称"顺着光束方向往下看"时不会因为
     * 侧面朝向而变成一条看不见的细线（第三人称侧看反而一直是满宽）。
     * 退化情况（切线平行视线）回退到平行传输。
     */
    private static Vector3f[] cameraFacingFrames(Vector3f[] points) {
        int n = points.length;
        Vector3f[] frames = new Vector3f[n];
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

            Vector3f right = tangent.cross(view);
            if (right.lengthSquared() < 1.0E-8F) {
                right = tangent.cross(leastParallelAxis(tangent));
            }
            if (right.lengthSquared() < 1.0E-8F) continue;
            frames[i] = right.normalize();
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
