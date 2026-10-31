package com.plumejade.lensouls.ability.client;

import com.plumejade.lensouls.client.fx.WorldFx;
import com.plumejade.lensouls.entity.GravityBulletEntity;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 引力枪牵引「磁力线」。
 * <p>
 * 三股缠绕的线束（主线 + 两条副线，螺旋相位不同、两端收敛），青白色，
 * 沿线奔跑的能量脉冲由 {@code fx_ribbon} 着色器负责（UV 柔边 → 不再是硬边几何）。
 * 被牵引目标身上套 3 个反向旋转的「磁笼」细环。
 * <p>
 * 绘制时机见 {@link com.plumejade.lensouls.client.fx.WorldFxPass}（AFTER_LEVEL，Iris 兼容）。
 * 不再发射末地烛粒子——电弧本身已足够表达牵引。
 * <p>
 * <b>延迟跟随</b>（2026-10-31，用户反馈「移动时位置刷新顿挫」）：与韧性条
 * {@code ToughnessBarRenderer.POS_SMOOTH}（每帧 0.06 ≈ 时间常数 0.27s）对齐，
 * 端点位置用 dt 校正的指数平滑（{@link #POS_TAU}），帧率高低于延迟量无影响。
 * 早先的 0.075s 几乎是硬跟随，移动时看起来就是"每 tick 跳一下"。
 */
public class GravityTetherRenderer {

    private static final int ARC_SEGMENTS = 40;

    /**
     * 起点（枪口，绑在自己身上）的平滑时间常数：延迟太大会"脱枪"，所以保守取小值。
     */
    private static final float FROM_TAU = 0.10f;
    /**
     * 终点（被牵引目标）的平滑时间常数，对齐韧性条 {@code POS_SMOOTH = 0.06/帧}
     * （60fps 下等效 ≈0.27s，这里取 0.22s 稍跟手）。受伤时两者都减半，打击反馈更跟手。
     */
    private static final float TO_TAU = 0.22f;

    private static final Map<Integer, Vec3> smoothFromMap = new HashMap<>();
    private static final Map<Integer, Vec3> smoothToMap = new HashMap<>();

    // ── 每 tick 一次的实体查询缓存 ────────────────────────────────────────
    // wantsRender() 与 render() 每帧各查一次 100³ 的 AABB，而实体只会在 tick 边界进出这个范围：
    // 移动时 AABB 反复扫过不同区块，是全模组最贵的一处每帧查询（用户反馈的"卡帧"）。
    private static Level cachedLevel;
    private static long cachedGameTime = Long.MIN_VALUE;
    private static List<GravityBulletEntity> cachedBullets = List.of();
    /** 玩家离上次查询点超过这个距离（格）才提前重查，避免快速位移时紧不到 */
    private static final double CACHE_REQUERY_DIST_Sq = 16.0 * 16.0;
    private static Vec3 cachedAt = Vec3.ZERO;

    private static List<GravityBulletEntity> bullets(Minecraft mc) {
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return List.of();
        long now = level.getGameTime();
        boolean sameTick = level == cachedLevel && now == cachedGameTime;
        boolean near = player.position().distanceToSqr(cachedAt) < CACHE_REQUERY_DIST_Sq;
        if (sameTick && near) return cachedBullets;

        cachedLevel = level;
        cachedGameTime = now;
        cachedAt = player.position();
        cachedBullets = level.getEntitiesOfClass(GravityBulletEntity.class,
                AABB.ofSize(player.position(), 100, 100, 100),
                b -> b.getClientHitTarget() != null);
        return cachedBullets;
    }

    public static boolean wantsRender() {
        return !bullets(Minecraft.getInstance()).isEmpty();
    }

    public static void render(Minecraft mc, Vec3 camPos, Matrix4f identity, float time,
                             float dt, float partialTick) {
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        List<GravityBulletEntity> bullets = bullets(mc);
        if (bullets.isEmpty()) return;

        smoothFromMap.keySet().removeIf(id -> level.getEntity(id) == null);
        smoothToMap.keySet().removeIf(id -> level.getEntity(id) == null);

        WorldFx.Params params = WorldFx.params();
        params.time = time;
        params.pulseSpeed = 1.05f;
        params.pulseCount = 2.0f;
        params.pulseSharp = 0.110f;
        params.edgeSoft = 1.15f;   // 更软的横向衰减 = 自带抗锯齿
        params.coreBoost = 0.65f;
        params.flicker = 0.18f;

        BufferBuilder buffer = WorldFx.begin(params);

        for (GravityBulletEntity bullet : bullets) {
            LivingEntity target = bullet.getClientHitTarget();
            if (target == null || !target.isAlive()) continue;
            Entity owner = bullet.getOwner();
            if (!(owner instanceof LivingEntity shooter) || !shooter.isAlive()) continue;

            // 端点先做"部分 tick 插值"（实体位置每 tick 才更新，不插值就是 20Hz 的位置跳变），
            // 再做<b>按秒</b>的指数平滑 —— 帧率高低不影响延迟量，移动时不会出现重复刷新的顿挫感。
            float pt = Math.max(0.0F, Math.min(1.0F, partialTick));
            Vec3 shooterPos = new Vec3(
                    Mth.lerp(pt, shooter.xo, shooter.getX()),
                    Mth.lerp(pt, shooter.yo, shooter.getY()),
                    Mth.lerp(pt, shooter.zo, shooter.getZ()));
            Vec3 targetPos = new Vec3(
                    Mth.lerp(pt, target.xo, target.getX()),
                    Mth.lerp(pt, target.yo, target.getY()) + target.getBbHeight() * 0.55,
                    Mth.lerp(pt, target.zo, target.getZ()));

            // 起点放在眼睛前方，别让光束从相机内部扫出来（第一人称会被糊满整屏）
            Vec3 view = shooter.getViewVector(pt);
            Vec3 rawFrom = shooterPos.add(0, shooter.getEyeHeight() * 0.9, 0).add(view.scale(0.9));
            Vec3 rawTo = targetPos;
            int bid = bullet.getId();

            boolean hurt = shooter.hurtTime > 0 || target.hurtTime > 0;
            // 两个端点各自平滑：枪口跟手、目标明显延迟跟随（韧性条同款量级）。
            // 用 dt 校正的指数平滑，帧率高低于延迟量无影响。
            float fromTau = hurt ? FROM_TAU * 0.5F : FROM_TAU;
            float toTau = hurt ? TO_TAU * 0.5F : TO_TAU;
            float fromK = dt <= 0.0F ? 1.0F : (1.0F - (float) Math.exp(-dt / fromTau));
            float toK = dt <= 0.0F ? 1.0F : (1.0F - (float) Math.exp(-dt / toTau));
            smoothFromMap.put(bid, approach(smoothFromMap.get(bid), rawFrom, fromK));
            smoothToMap.put(bid, approach(smoothToMap.get(bid), rawTo, toK));

            Vec3 from = smoothFromMap.get(bid);
            Vec3 to = smoothToMap.get(bid);
            float dist = (float) from.distanceTo(to);
            if (dist < 0.5f) continue;

            drawTether(buffer, identity, from, to, camPos, dist, time, hurt, bid);
            drawCage(buffer, identity, to, target.getBbWidth(), camPos, time, hurt);
        }

        WorldFx.flush(buffer);
    }

    private static void drawTether(BufferBuilder b, Matrix4f pose, Vec3 from, Vec3 to, Vec3 camPos,
                                   float dist, float time, boolean hurt, int seed) {
        Vec3 dir = to.subtract(from);
        Vec3 axis = dir.normalize();
        Vec3 up = Math.abs(axis.y) > 0.92 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 n1 = axis.cross(up).normalize();
        Vec3 n2 = axis.cross(n1).normalize();

        float amplitude = dist * 0.026f;
        float[][] strands = {
                {1.00f, 0.00f, 0.026f, 1.00f},
                {0.55f, 2.094f, 0.018f, 0.38f},
                {0.32f, 4.188f, 0.014f, 0.22f},
        };

        for (float[] s : strands) {
            Vector3f[] pts = new Vector3f[ARC_SEGMENTS + 1];
            float[] alphas = new float[ARC_SEGMENTS + 1];
            for (int i = 0; i <= ARC_SEGMENTS; i++) {
                float t = (float) i / ARC_SEGMENTS;
                float taper = (float) Math.pow(Math.sin(Math.PI * t), 0.65);
                float amp = amplitude * s[0] * taper * (hurt ? 0.62f : 1.0f);
                double ang = t * Math.PI * 2.0 * 1.35 + time * (1.1 + s[0] * 0.6) + s[1];
                double wobble = Math.sin(t * 9.0 + time * 3.4 + seed * 0.7) * 0.35;
                Vec3 offset = n1.scale(Math.cos(ang) * amp + wobble * amp * 0.4)
                        .add(n2.scale(Math.sin(ang) * amp));
                Vec3 p = from.add(dir.scale(t)).add(offset);
                pts[i] = new Vector3f(
                        (float) (p.x - camPos.x),
                        (float) (p.y - camPos.y),
                        (float) (p.z - camPos.z));
                // 近镜头淡出：0.6 格内完全透明，2.2 格外全亮
                alphas[i] = smoothstep(0.5f, 1.6f, (float) p.distanceTo(camPos));
            }
            WorldFx.polyline(b, pose, pts, null, s[2], 170, 236, 255, (int) (255 * s[3]), alphas);
        }
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - e0) / (e1 - e0)));
        return t * t * (3.0F - 2.0F * t);
    }

    /** 指数趋近一步：prev 为空时直接落到目标点（首次出现不甩尾）。 */
    private static Vec3 approach(Vec3 prev, Vec3 target, float k) {
        if (prev == null) return target;
        return prev.add(target.subtract(prev).scale(k));
    }

    /**
     * 被牵引目标身上的"磁笼"。
     *
     * @param smoothCenter 已经过部分 tick 插值 + 指数平滑的腰部点（跟光束用同一份位置，
     *                     否则笼子会跟着原始位置抖）
     */
    private static void drawCage(BufferBuilder b, Matrix4f pose, Vec3 smoothCenter, float targetWidth,
                                 Vec3 camPos, float time, boolean hurt) {
        float radius = Math.max(0.55f, targetWidth * 0.72f);
        int brightness = hurt ? 230 : 185;

        Vector3f center = new Vector3f(
                (float) (smoothCenter.x - camPos.x),
                (float) (smoothCenter.y - camPos.y),
                (float) (smoothCenter.z - camPos.z));

        for (int i = 0; i < 3; i++) {
            float spin = time * (0.8f + i * 0.35f) * (i % 2 == 0 ? 1f : -1f);
            double tilt = Math.PI * 0.5 * i;
            Vector3f axisA = new Vector3f((float) Math.cos(spin), 0f, (float) Math.sin(spin));
            Vector3f axisB = new Vector3f((float) -Math.sin(spin) * (float) Math.cos(tilt),
                    (float) Math.sin(tilt), (float) Math.cos(spin) * (float) Math.cos(tilt));
            axisA.normalize();
            axisB.normalize();
            WorldFx.ring(b, pose, center, axisA, axisB, radius, 0.020f,
                    150, 235, 255, brightness, 48);
        }
    }
}
