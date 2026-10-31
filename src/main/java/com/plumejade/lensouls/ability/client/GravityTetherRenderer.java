package com.plumejade.lensouls.ability.client;

import com.plumejade.lensouls.client.fx.WorldFx;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;

/**
 * 引力枪牵引「磁力线」渲染（客户端）。
 * <p>
 * <b>2026-10-31 按原版拴绳重写</b>：以前这里靠"被命中的那颗子弹"驱动 —— 玩家跑快一点，
 * 子弹被移出跟踪范围/移除重建，就出现「特效消失 → 子弹没了 → 拉不住」。
 * 现在只认 {@link ClientTetherCache} 里的**牵引关系**（{@code targetId -> ownerId}，由服务端
 * 在开始/结束时发一次），端点每帧用两端实体的 {@code Mth.lerp(partialTick, xo, x)} 现算 ——
 * 也就是 {@code EntityRenderer.renderLeash} 的做法：<b>没有任何跨帧平滑状态</b>，
 * 因此不会漂移、不会"从屏幕外追上来"、也不会因为某个载体实体被卸载而消失。
 * <p>
 * 三股缠绕的线束 + 沿线脉冲（{@code fx_ribbon}）、目标身上的三个反向旋转「磁笼」保持不变。
 */
public final class GravityTetherRenderer {

    private static final int ARC_SEGMENTS = 40;

    private GravityTetherRenderer() {
    }

    public static boolean wantsRender() {
        return !ClientTetherCache.isEmpty();
    }

    public static void render(Minecraft mc, Vec3 camPos, Matrix4f identity, float time,
                              float dt, float partialTick) {
        Level level = mc.level;
        if (level == null) return;
        Map<Integer, Integer> links = ClientTetherCache.links();
        if (links.isEmpty()) return;

        WorldFx.Params params = WorldFx.params();
        params.time = time;
        params.pulseSpeed = 1.05f;
        params.pulseCount = 2.0f;
        params.pulseSharp = 0.110f;
        params.edgeSoft = 1.15f;   // 更软的横向衰减 = 自带抗锯齿
        params.coreBoost = 0.65f;
        params.flicker = 0.18f;

        BufferBuilder buffer = WorldFx.begin(params);
        float pt = Mth.clamp(partialTick, 0.0F, 1.0F);

        for (Map.Entry<Integer, Integer> e : links.entrySet()) {
            if (!(level.getEntity(e.getValue()) instanceof LivingEntity shooter) || !shooter.isAlive()) continue;
            if (!(level.getEntity(e.getKey()) instanceof LivingEntity target) || !target.isAlive()) continue;

            Vec3 shooterPos = lerp(shooter, pt);
            Vec3 targetPos = lerp(target, pt).add(0, target.getBbHeight() * 0.55, 0);

            // 起点放在眼睛前方，别让光束从相机内部扫出来（第一人称会被糊满整屏）
            Vec3 view = shooter.getViewVector(pt);
            Vec3 from = shooterPos.add(0, shooter.getEyeHeight() * 0.9, 0).add(view.scale(0.9));

            boolean hurt = shooter.hurtTime > 0 || target.hurtTime > 0;
            float dist = (float) from.distanceTo(targetPos);

            // 端点重合时 dir.normalize() 会变 NaN：太小只省略光束，磁笼照画
            if (dist >= 0.12f) {
                drawTether(buffer, identity, from, targetPos, camPos, dist, time, hurt);
            }
            drawCage(buffer, identity, targetPos, target.getBbWidth(), target.getBbHeight(), camPos, time, hurt);
        }

        WorldFx.flush(buffer);
    }

    /** 与 {@code EntityRenderer.renderLeash} 同款：两端都用 partialTick 在上一 tick / 当前 tick 之间插值。 */
    private static Vec3 lerp(LivingEntity e, float pt) {
        return new Vec3(
                Mth.lerp(pt, e.xo, e.getX()),
                Mth.lerp(pt, e.yo, e.getY()),
                Mth.lerp(pt, e.zo, e.getZ()));
    }

    private static void drawTether(BufferBuilder b, Matrix4f pose, Vec3 from, Vec3 to, Vec3 camPos,
                                   float dist, float time, boolean hurt) {
        Vec3 dir = to.subtract(from);
        Vec3 axis = dir.normalize();
        Vec3 up = Math.abs(axis.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 n1 = axis.cross(up).normalize();
        Vec3 n2 = axis.cross(n1).normalize();

        // 三股要"读成一根线、只在边缘看出编织感"：幅度、线宽、副线亮度都收紧，
        // 螺旋 1.00 圈、转速约 0.4（早先幅度 0.29 格 + 转得快，近看会分离成几道平行光）。
        float amplitude = dist * 0.016f;
        float[][] strands = {
                {1.00f, 0.00f, 0.022f, 1.00f},
                {0.42f, 2.094f, 0.013f, 0.26f},
                {0.26f, 4.188f, 0.010f, 0.14f},
        };

        for (float[] s : strands) {
            Vector3f[] pts = new Vector3f[ARC_SEGMENTS + 1];
            float[] alphas = new float[ARC_SEGMENTS + 1];
            for (int i = 0; i <= ARC_SEGMENTS; i++) {
                float t = (float) i / ARC_SEGMENTS;
                float taper = (float) Math.pow(Math.sin(Math.PI * t), 0.65);
                float amp = amplitude * s[0] * taper * (hurt ? 0.62f : 1.0f);
                double ang = t * Math.PI * 2.0 * 1.00 + time * (0.42 + s[0] * 0.22) + s[1];
                double wobble = Math.sin(t * 4.0 + time * 1.6) * 0.20;
                Vec3 offset = n1.scale(Math.cos(ang) * amp + wobble * amp * 0.35)
                        .add(n2.scale(Math.sin(ang) * amp));
                Vec3 p = from.add(dir.scale(t)).add(offset);
                pts[i] = new Vector3f(
                        (float) (p.x - camPos.x),
                        (float) (p.y - camPos.y),
                        (float) (p.z - camPos.z));
                // 近镜头淡出只在真正贴进镜头时才收（0.15~0.6 格），否则拉到近处会整条淡没
                alphas[i] = smoothstep(0.15f, 0.6f, (float) p.distanceTo(camPos));
            }
            WorldFx.polyline(b, pose, pts, null, s[2], 170, 236, 255, (int) (255 * s[3]), alphas);
        }
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - e0) / (e1 - e0)));
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * 被牵引目标身上的"磁笼"。
     * <p>
     * 线宽随体型放大（{@code sqrt(bbW²+bbH²)/1.90} 夹 0.55~3.4，与项目里其它贴实体特效同口径），
     * 再叠一个"视距 × 0.0022"的下限，保证远处也有几像素宽。
     */
    private static void drawCage(BufferBuilder b, Matrix4f pose, Vec3 center0, float targetWidth,
                                 float targetHeight, Vec3 camPos, float time, boolean hurt) {
        float radius = Math.max(0.55f, targetWidth * 0.72f);
        int brightness = hurt ? 240 : 205;

        float diag = (float) Math.sqrt(targetWidth * targetWidth + targetHeight * targetHeight);
        float sizeScale = Math.max(0.55f, Math.min(3.4f, diag / 1.90f));
        float distToCam = (float) center0.distanceTo(camPos);
        float halfWidth = Math.max(0.042f * sizeScale, distToCam * 0.0022f);

        Vector3f center = new Vector3f(
                (float) (center0.x - camPos.x),
                (float) (center0.y - camPos.y),
                (float) (center0.z - camPos.z));

        for (int i = 0; i < 3; i++) {
            float spin = time * (0.8f + i * 0.35f) * (i % 2 == 0 ? 1f : -1f);
            double tilt = Math.PI * 0.5 * i;
            Vector3f axisA = new Vector3f((float) Math.cos(spin), 0f, (float) Math.sin(spin));
            Vector3f axisB = new Vector3f((float) -Math.sin(spin) * (float) Math.cos(tilt),
                    (float) Math.sin(tilt), (float) Math.cos(spin) * (float) Math.cos(tilt));
            axisA.normalize();
            axisB.normalize();
            WorldFx.ring(b, pose, center, axisA, axisB, radius, halfWidth,
                    150, 235, 255, brightness, 56);
        }
    }
}
