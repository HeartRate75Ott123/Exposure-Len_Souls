package com.plumejade.lensouls.client.fx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.plumejade.lensouls.particle.ModParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * N公司2级员工（{@code level2_staff_boss}）的世界空间特效（纯客户端）。
 * <p>
 * 与 {@link WorldFxBursts} 同一条管线：全部在 {@code AFTER_LEVEL} 由 {@link WorldFxPass} 驱动，
 * 几何是相机相对坐标，着色器是 {@code lensouls:fx_ribbon}（Iris 下只有这个时机不会被抢帧缓冲）。
 * <ul>
 *   <li>{@link #spike} —— 远程投掷：员工身上银白「十字环」聚焦收缩 → 向外扩散消散，
 *       随后一条深紫魔法轨迹按服务端射线的速度飞向目标；</li>
 *   <li>{@link #cameraBurst} —— 拍照：员工身上爆出一圈银白发光魂尘（与夺魂索命同一套粒子形态）。</li>
 * </ul>
 * 近战刀光已按需求<b>放弃</b>（曾实现过撒手圆弧版本，不要重新加回来）。
 */
@OnlyIn(Dist.CLIENT)
public final class Level2StaffFx {

    private interface Burst {
        boolean update(float dt);

        void render(Matrix4f pose, Vec3 camera, float time);
    }

    private static final List<Burst> BURSTS = new ArrayList<>();

    private Level2StaffFx() {
    }

    public static void clear() {
        BURSTS.clear();
    }

    public static boolean wantsRender() {
        return !BURSTS.isEmpty();
    }

    // =====================================================================
    //  入口
    // =====================================================================

    /** 远程投掷：银白十字环 + 深紫魔法轨迹。 */
    public static void spike(Vec3 origin, Vec3 dir, float range) {
        if (origin == null || dir == null) return;
        if (dir.lengthSqr() < 1.0E-6) return;
        BURSTS.add(new SpikeBurst(origin, dir.normalize(), Math.max(1.5F, range)));
    }

    /** 拍照：员工位置爆出银白发光魂尘（高低各一圈，与夺魂索命的观感对齐）。 */
    public static void cameraBurst(ClientLevel level, Vec3 feet, float height) {
        if (level == null || feet == null) return;
        float h = Math.max(1.0F, height);

        // 身体各处上浮的散尘
        int wisps = 28;
        for (int i = 0; i < wisps; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 0.85;
            double oz = (level.random.nextDouble() - 0.5) * 0.85;
            double oy = 0.08 + level.random.nextDouble() * h;
            level.addParticle(ModParticleTypes.SOUL_WISP_WHITE.get(),
                    feet.x + ox, feet.y + oy, feet.z + oz,
                    0.0, 0.02 + level.random.nextDouble() * 0.03, 0.0);
        }
        // 胸口一圈向外轻推（"快门闪光把魂尘推开"的方向感）
        int ring = 14;
        for (int i = 0; i < ring; i++) {
            double ang = Math.PI * 2.0 * i / ring;
            level.addParticle(ModParticleTypes.SOUL_WISP_WHITE.get(),
                    feet.x, feet.y + h * 0.62, feet.z,
                    Math.cos(ang) * 0.10, 0.045, Math.sin(ang) * 0.10);
        }
    }

    // =====================================================================
    //  驱动
    // =====================================================================

    public static void render(Minecraft mc, Vec3 camera, Matrix4f pose, float time, float dt) {
        if (BURSTS.isEmpty()) return;
        Iterator<Burst> it = BURSTS.iterator();
        while (it.hasNext()) {
            Burst burst = it.next();
            if (!burst.update(dt)) it.remove();
            else burst.render(pose, camera, time);
        }
    }

    // =====================================================================
    //  远程投掷：十字环 + 深紫轨迹
    // =====================================================================

    private static final class SpikeBurst implements Burst {

        /** 起手的"聚焦/蓄力"阶段（环收缩），之后才是光束飞出；光环加粗后给到 0.14s 才看得清 */
        private static final float FOCUS = 0.14F;
        /** 与服务端 {@code RAY_SPEED = 0.7 格/tick} 一致 → 14 格/秒 */
        private static final float SPEED = 14.0F;
        /** 命中/落地后轨迹的余韵 */
        private static final float LINGER = 0.34F;
        /** 十字环总寿命 */
        private static final float RING_LIFE = 0.50F;
        private static final int SEGMENTS = 18;

        private final Vec3 origin;
        private final Vec3 dir;
        private final float range;
        private final float travel;
        private float t;

        SpikeBurst(Vec3 origin, Vec3 dir, float range) {
            this.origin = origin;
            this.dir = dir;
            this.range = range;
            this.travel = range / SPEED;
        }

        @Override
        public boolean update(float dt) {
            t += dt;
            return t < FOCUS + travel + LINGER;
        }

        @Override
        public void render(Matrix4f pose, Vec3 camera, float time) {
            Vector3f o = rel(origin, camera);
            Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
            if (d.lengthSquared() < 1.0E-6F) return;
            d.normalize();

            // ── 1. 银白十字环（先聚焦收缩，再向外扩散消散） ──
            crossRings(pose, o, time);

            // ── 2. 深紫魔法轨迹 ──
            float beamT = t - FOCUS;
            if (beamT <= 0.0F) return;

            float headDist = Math.min(beamT * SPEED, range);
            float grow = clamp(headDist / range, 0.02F, 1.0F);
            float arriveT = beamT - travel;
            float fade = 1.0F;
            float taper = 0.0F;
            if (arriveT > 0.0F) {
                float k = Math.min(1.0F, arriveT / LINGER);
                fade = (1.0F - k) * (1.0F - k);
                taper = k;
            }
            if (fade <= 0.02F) return;

            Vector3f right = new Vector3f();
            Vector3f up = new Vector3f();
            frame(d, right, up);
            Vector3f end = new Vector3f(o).add(new Vector3f(d).mul(range));

            // 主干 + 两条缠绕的魔法丝（同一批：grow 一致，靠 FxGrow 长出）
            WorldFx.Params body = WorldFx.params();
            body.time = time;
            body.pulseSpeed = 5.2F;
            body.pulseSharp = 0.10F;
            body.pulseCount = 3.0F;
            body.edgeSoft = 2.05F;
            body.coreBoost = 0.85F;
            body.flicker = 0.45F;
            body.grow = grow;
            body.taper = taper;
            body.alpha = fade;

            BufferBuilder buffer = WorldFx.begin(body);

            Vector3f[] main = new Vector3f[SEGMENTS];
            Vector3f[] braidA = new Vector3f[SEGMENTS];
            Vector3f[] braidB = new Vector3f[SEGMENTS];
            for (int i = 0; i < SEGMENTS; i++) {
                float q = (float) i / (SEGMENTS - 1);
                Vector3f axis = new Vector3f(o).lerp(end, q);
                // 蛇形游动（黑暗魔法的"活物"感，随 time 沿轴向流动）；起点锚死，免得和十字环脱开
                float wobble = 0.055F * (float) Math.sin(q * 10.0F - time * 5.0F) * Math.min(1.0F, q * 4.0F);
                main[i] = new Vector3f(axis).add(new Vector3f(right).mul(wobble));

                // 螺旋缠绕：头部收拢（sin 包络），中段最鼓
                float coil = 0.10F * (float) Math.sin(Math.PI * Math.min(1.0F, q * 1.08F));
                float spir = q * 9.0F + 1.2F;
                braidA[i] = new Vector3f(axis)
                        .add(new Vector3f(right).mul((float) Math.cos(spir) * coil))
                        .add(new Vector3f(up).mul((float) Math.sin(spir) * coil));
                braidB[i] = new Vector3f(axis)
                        .add(new Vector3f(right).mul((float) Math.cos(spir + Math.PI) * coil))
                        .add(new Vector3f(up).mul((float) Math.sin(spir + Math.PI) * coil));
            }

            // 整体淡出交给 FxAlpha（不要再乘到顶点 alpha 上，否则是平方衰减、尾巴会突然没）
            int alpha = 255;
            // 深紫主轨：粗（半宽 0.085 → 整条 0.17 格宽）
            WorldFx.polyline(buffer, pose, main, null, 0.085F, 104, 30, 198, alpha);
            // 缠绕丝略微偏亮，把"魔法的编织"讲清楚
            WorldFx.polyline(buffer, pose, braidA, null, 0.026F, 152, 74, 255, 216);
            WorldFx.polyline(buffer, pose, braidB, null, 0.022F, 138, 60, 240, 184);
            WorldFx.flush(buffer);

            // 飞行中的头部光晕环（grow 会裁切环的 UV，所以必须单独一批）
            if (arriveT <= 0.0F) {
                Vector3f head = new Vector3f(o).add(new Vector3f(d).mul(headDist));

                WorldFx.Params halo = WorldFx.params();
                halo.time = time;
                halo.pulseSpeed = 8.0F;
                halo.pulseSharp = 0.09F;
                halo.edgeSoft = 1.5F;
                halo.coreBoost = 1.5F;
                halo.flicker = 0.25F;
                halo.alpha = fade;

                BufferBuilder ringBuf = WorldFx.begin(halo);
                WorldFx.ring(ringBuf, pose, head, right, up, 0.155F, 0.014F, 226, 208, 255, 235, 40);
                WorldFx.ring(ringBuf, pose, head, right, up, 0.245F, 0.008F, 168, 96, 255, 130, 40);
                WorldFx.flush(ringBuf);
            }
        }

        /** 银白十字环：水平环 + 两个互相垂直的竖直环 → 正面看正好是「圆 + 十字」。 */
        private void crossRings(Matrix4f pose, Vector3f eye, float time) {
            if (t >= RING_LIFE) return;

            float radius;
            float alpha;
            if (t < FOCUS) {
                // 聚焦：从大收缩到小，亮度上升
                float k = clamp(t / FOCUS, 0.0F, 1.0F);
                radius = 0.95F - 0.62F * (k * k * (3.0F - 2.0F * k));
                alpha = 0.30F + 0.70F * k;
            } else {
                // 扩张：向外推开并消散
                float k = clamp((t - FOCUS) / (RING_LIFE - FOCUS), 0.0F, 1.0F);
                radius = 0.33F + 2.15F * (float) Math.pow(k, 0.70);
                alpha = (1.0F - k) * (1.0F - k);
            }
            if (alpha <= 0.02F) return;

            // 圆心下移到胸口（射线起点在眼睛，只往下挪一点点，别和轨迹起点脱开）
            Vector3f c = new Vector3f(eye).sub(0.0F, 0.45F, 0.0F);

            WorldFx.Params p = WorldFx.params();
            p.time = time;
            p.pulseSpeed = 2.6F;
            p.pulseSharp = 0.07F;
            p.edgeSoft = 1.55F;
            p.coreBoost = 1.35F;
            p.flicker = 0.22F;
            p.alpha = 1.0F;

            BufferBuilder buffer = WorldFx.begin(p);
            int a = (int) (255.0F * clamp(alpha, 0.0F, 1.0F));
            Vector3f axX = new Vector3f(1.0F, 0.0F, 0.0F);
            Vector3f axY = new Vector3f(0.0F, 1.0F, 0.0F);
            Vector3f axZ = new Vector3f(0.0F, 0.0F, 1.0F);

            // 环带加粗到半宽 0.05（整条 0.1 格）：蓄力阶段的"光环"要够厚重、一眼看见
            float band = 0.050F;

            // 水平环（正视时是一条水平亮线）
            WorldFx.ring(buffer, pose, c, axX, axZ, radius, band, 210, 224, 246, a, 64);
            // 两个竖直环（互成 90°：一个正视成圆，一个正视成竖线）
            WorldFx.ring(buffer, pose, c, axX, axY, radius, band, 210, 224, 246, (int) (a * 0.88F), 64);
            WorldFx.ring(buffer, pose, c, axZ, axY, radius, band, 210, 224, 246, (int) (a * 0.88F), 64);
            WorldFx.flush(buffer);
        }
    }

    // =====================================================================
    //  工具
    // =====================================================================

    private static Vector3f rel(Vec3 world, Vec3 camera) {
        return new Vector3f((float) (world.x - camera.x),
                (float) (world.y - camera.y),
                (float) (world.z - camera.z));
    }

    /** 以 {@code d} 为轴取一组正交基（right / up），用于环与螺旋。 */
    private static void frame(Vector3f d, Vector3f right, Vector3f up) {
        Vector3f ref = Math.abs(d.y) > 0.94F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
        right.set(d).cross(ref);
        if (right.lengthSquared() < 1.0E-6F) right.set(1.0F, 0.0F, 0.0F);
        right.normalize();
        up.set(d).cross(right).normalize();
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static float smoothstep(float e0, float e1, float x) {
        float v = clamp((x - e0) / (e1 - e0), 0.0F, 1.0F);
        return v * v * (3.0F - 2.0F * v);
    }
}
