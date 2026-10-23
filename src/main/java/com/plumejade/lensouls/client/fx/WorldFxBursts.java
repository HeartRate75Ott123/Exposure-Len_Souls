package com.plumejade.lensouls.client.fx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.plumejade.lensouls.particle.ModParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 世界空间瞬时特效：夺魂索命的「魂飞魄散」、时空回溯的落点。
 * <p>
 * 全部在 {@code AFTER_LEVEL} 由 {@link WorldFxPass} 驱动（Iris 兼容），
 * 动画靠着色器 uniform：
 * <ul>
 *   <li>{@code FxGrow} —— 魂丝从体内<b>沿路径生长</b>出来（不是一次性出现）；</li>
 *   <li>环<b>错峰连续推出</b>（ring 一个个冒出来并向外扩张）；</li>
 *   <li>{@code FxTaper} —— 消散时末端<b>散开、变细</b>，同时半径外扩、亮度衰减。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class WorldFxBursts {

    private interface Burst {
        boolean update(float dt);

        void render(Matrix4f pose, Vec3 camera, float time);
    }

    private static final List<Burst> BURSTS = new ArrayList<>();

    private WorldFxBursts() {
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

    /** 夺魂索命：目标身上「魂飞魄散」。 */
    public static void soulSever(ClientLevel level, Vec3 feet, float height, boolean success) {
        BURSTS.add(new SoulSeverBurst(feet, height, success));

        // 成功 = 绿色发射器；失败 = 紫色发射器（同形态、不同配色）
        var wispType = success ? ModParticleTypes.SOUL_WISP.get() : ModParticleTypes.SOUL_WISP_FAIL.get();
        int wisps = success ? 30 : 16;
        float h = Math.max(0.9F, height);
        for (int i = 0; i < wisps; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 0.9;
            double oz = (level.random.nextDouble() - 0.5) * 0.9;
            double oy = 0.1 + level.random.nextDouble() * h;
            level.addParticle(wispType,
                    feet.x + ox, feet.y + oy, feet.z + oz,
                    0.0, 0.02 + level.random.nextDouble() * 0.03, 0.0);
        }
        for (int i = 0; i < (success ? 12 : 6); i++) {
            double ang = Math.PI * 2 * i / (success ? 12.0 : 6.0);
            level.addParticle(wispType,
                    feet.x, feet.y + h * 0.58, feet.z,
                    Math.cos(ang) * 0.09, 0.05, Math.sin(ang) * 0.09);
        }
    }

    /** 时空回溯落点：脚下青色扩散环 + 上升时间尘埃。 */
    public static void recallArrive(ClientLevel level, Vec3 feet) {
        BURSTS.add(new RecallArriveBurst(feet));
        for (int i = 0; i < 18; i++) {
            double ang = Math.PI * 2 * i / 18.0;
            double r = 0.3 + level.random.nextDouble() * 0.6;
            level.addParticle(ParticleTypes.END_ROD,
                    feet.x + Math.cos(ang) * r, feet.y + 0.1 + level.random.nextDouble() * 0.6,
                    feet.z + Math.sin(ang) * r,
                    0.0, 0.04 + level.random.nextDouble() * 0.03, 0.0);
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

    /** 夺魂索命：魂丝生长 → 环连续推出 → 散开变细消散。 */
    private static final class SoulSeverBurst implements Burst {
        private static final float DURATION = 1.35F;

        private final Vec3 feet;
        private final float height;
        private final boolean success;
        private float t;

        SoulSeverBurst(Vec3 feet, float height, boolean success) {
            this.feet = feet;
            this.height = Math.max(0.9F, height);
            this.success = success;
        }

        @Override
        public boolean update(float dt) {
            t += dt;
            return t < DURATION;
        }

        @Override
        public void render(Matrix4f pose, Vec3 camera, float time) {
            float u = Math.min(1.0F, t / DURATION);
            Vector3f center = new Vector3f(
                    (float) (feet.x - camera.x),
                    (float) (feet.y - camera.y),
                    (float) (feet.z - camera.z));
            // 成功 = 绿色；失败 = 紫色
            int r = success ? 70 : 150;
            int g = success ? 255 : 90;
            int b = success ? 170 : 255;

            // ── 1. 魂丝：沿路径生长（前 22%），末端散开变细（后 45%） ──
            float grow = Math.min(1.0F, t / 0.30F);
            float taper = smoothstep(0.55F, 1.0F, u);
            float strandAlpha = (1.0F - u * u) * (success ? 1.0F : 0.5F);
            if (strandAlpha > 0.02F) {
                WorldFx.Params p = WorldFx.params();
                p.time = time;
                p.pulseSpeed = 0.9F;
                p.pulseSharp = 0.08F;
                p.edgeSoft = 1.35F;
                p.coreBoost = 0.95F;
                p.flicker = 0.4F;
                p.grow = grow;
                p.taper = taper;
                p.alpha = strandAlpha;

                BufferBuilder buffer = WorldFx.begin(p);
                int strands = success ? 9 : 4;
                for (int s = 0; s < strands; s++) {
                    double baseAng = Math.PI * 2.0 * s / strands + u * 1.3;
                    int segments = 10;
                    Vector3f[] pts = new Vector3f[segments];
                    for (int i = 0; i < segments; i++) {
                        float q = (float) i / (segments - 1);
                        // 消散时半径外扩（散开）
                        float spread = 1.0F + taper * 1.5F;
                        float radius = (0.12F + q * 0.30F) * spread;
                        float ang = (float) (baseAng + q * (2.9 + taper * 2.2));
                        float y = center.y + height * 0.55F + q * (height * (1.5F + taper * 0.5F));
                        pts[i] = new Vector3f(
                                center.x + (float) Math.cos(ang) * radius,
                                y,
                                center.z + (float) Math.sin(ang) * radius);
                    }
                    WorldFx.polyline(buffer, pose, pts, null, 0.036F, r, g, (int) (b * 0.9F),
                            (int) (255 * strandAlpha));
                }
                WorldFx.flush(buffer);
            }

            // ── 2. 环：错峰连续推出（每个环自己的一段生命周期） ──
            WorldFx.Params ringParams = WorldFx.params();
            ringParams.time = time;
            ringParams.pulseSpeed = 1.3F;
            ringParams.pulseSharp = 0.06F;
            ringParams.edgeSoft = 1.3F;
            ringParams.coreBoost = 1.0F;
            // 环是大面积细带，FxFlicker 是"沿圆周的高频噪声"，0.3 在浅视角下会被读成频闪；
            // 环要稳定发光，跳动只留给沿圆周跑的脉冲。
            ringParams.flicker = 0.10F;
            ringParams.alpha = 1.0F;

            BufferBuilder rings = WorldFx.begin(ringParams);
            Vector3f axisX = new Vector3f(1f, 0f, 0f);
            Vector3f axisZ = new Vector3f(0f, 0f, 1f);
            int ringCount = success ? 7 : 3;
            for (int i = 0; i < ringCount; i++) {
                float start = i * 0.075F;
                float local = (u - start) / 0.55F;
                if (local <= 0.0F || local >= 1.0F) continue;
                float radius = 0.55F + local * (1.4F + i * 0.22F);
                float y = center.y + 0.10F + local * (height * 0.9F) + i * 0.05F;
                int a = (int) ((1.0F - local) * (1.0F - local) * 230 * (success ? 1.0F : 0.5F));
                if (a <= 3) continue;
                WorldFx.ring(rings, pose, new Vector3f(center.x, y, center.z), axisX, axisZ,
                        radius, 0.032f, r, g, b, a, 56);
            }
            WorldFx.flush(rings);

            // ── 3. 魂柱：从胸口向上长出来，随后变细消散 ──
            if (success && grow > 0.05F) {
                WorldFx.Params p = WorldFx.params();
                p.time = time;
                p.pulseSpeed = 0.55F;
                p.pulseSharp = 0.10F;
                p.edgeSoft = 2.0F;
                p.coreBoost = 0.5F;
                p.flicker = 0.25F;
                p.grow = grow;
                p.taper = taper;
                p.alpha = (1.0F - u * u) * 0.55F;

                BufferBuilder pillar = WorldFx.begin(p);
                float top = center.y + height * (0.9F + taper * 0.9F);
                float wide = 0.20F * (1.0F - taper * 0.7F);
                WorldFx.polyline(pillar, pose, new Vector3f[]{
                        new Vector3f(center.x, center.y + height * 0.42F, center.z),
                        new Vector3f(center.x, top, center.z),
                }, null, wide, r, g, (int) (b * 0.85F), (int) (255 * p.alpha));
                WorldFx.flush(pillar);
            }
        }
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0F - 2.0F * t);
    }

    /** 时空回溯落点：青色扩散环 + 短光柱。 */
    private static final class RecallArriveBurst implements Burst {
        private static final float DURATION = 0.75F;

        private final Vec3 feet;
        private float t;

        RecallArriveBurst(Vec3 feet) {
            this.feet = feet;
        }

        @Override
        public boolean update(float dt) {
            t += dt;
            return t < DURATION;
        }

        @Override
        public void render(Matrix4f pose, Vec3 camera, float time) {
            float u = Math.min(1.0F, t / DURATION);
            float fade = 1.0F - u * u;
            if (fade <= 0.02F) return;

            Vector3f center = new Vector3f(
                    (float) (feet.x - camera.x),
                    (float) (feet.y - camera.y),
                    (float) (feet.z - camera.z));

            WorldFx.Params p = WorldFx.params();
            p.time = time;
            p.pulseSpeed = 2.2F;
            p.pulseSharp = 0.05F;
            p.edgeSoft = 1.4F;
            p.coreBoost = 1.0F;
            p.flicker = 0.2F;
            p.grow = 1.0F;
            p.taper = smoothstep(0.5F, 1.0F, u);
            p.alpha = fade;

            BufferBuilder buffer = WorldFx.begin(p);
            Vector3f axisX = new Vector3f(1f, 0f, 0f);
            Vector3f axisZ = new Vector3f(0f, 0f, 1f);

            for (int i = 0; i < 3; i++) {
                float local = (u - i * 0.08F) / 0.6F;
                if (local <= 0.0F || local >= 1.0F) continue;
                float radius = 0.28F + local * 1.6F;
                int a = (int) ((1.0F - local) * (1.0F - local) * 220);
                if (a <= 3) continue;
                WorldFx.ring(buffer, pose, new Vector3f(center.x, center.y + 0.06F + i * 0.05F, center.z),
                        axisX, axisZ, radius, 0.026f, 120, 220, 255, a, 56);
            }

            for (int s = 0; s < 4; s++) {
                double ang = Math.PI * 2.0 * s / 4.0 + 0.4;
                Vector3f[] pts = new Vector3f[6];
                for (int i = 0; i < 6; i++) {
                    float q = (float) i / 5.0F;
                    pts[i] = new Vector3f(
                            center.x + (float) Math.cos(ang) * 0.12F * (1.0F - q),
                            center.y + 0.05F + q * (1.5F * fade + 0.3F),
                            center.z + (float) Math.sin(ang) * 0.12F * (1.0F - q));
                }
                WorldFx.polyline(buffer, pose, pts, null, 0.024F, 110, 210, 255, (int) (255 * fade));
            }

            WorldFx.flush(buffer);
        }
    }
}
