package com.plumejade.lensouls.util;

import com.plumejade.lensouls.Config;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 相机可见性判定（全模组唯一真源）。
 * <p>
 * 拍摄/瞄准选敌的三道闸门，按从廉价到昂贵的顺序执行：
 * <ol>
 *   <li><b>视锥</b>：目标采样点须落在相机锥角内（{@link #inCone}）。</li>
 *   <li><b>射程</b>：硬距离上限（{@link #maxCaptureDistance()}，可配置）。Exposure 原生的
 *       {@code calculateVisibleDistance} 只按焦距折算，长焦镜头下会放到数十格，导致
 *       「超远视锥内的全部生物」被认到。</li>
 *   <li><b>遮挡</b>：从相机到目标包围盒多个采样点做 {@code COLLIDER} 射线，遇方块即止
 *       （{@link #hasClearSight}）。Exposure 原生只采样「眼睛」单点，会绕角透墙。</li>
 * </ol>
 * <p>
 * 消费方：{@code EntitiesInFrameMixin}（弱点透镜/能力窃取/时间定格/韧性选取的公共来源）、
 * {@link AimTargetUtil}（要害打击/断魂）、Field Guide 图鉴扫描。
 * <p>
 * 多部件实体（九头蛇头/娜迦体节）由调用方把「子部件」传进来判定，父实体的解析留给调用方。
 */
public final class CameraVisibility {

    /** 配置未就绪时的兜底射程（格）。 */
    public static final double FALLBACK_MAX_DISTANCE = 32.0;

    private CameraVisibility() {
    }

    // ==================== 射程 ====================

    /**
     * 拍摄/瞄准的最大距离（格）。配置值 {@code <= 0} 视为不限（返回 {@link Double#MAX_VALUE}）。
     */
    public static double maxCaptureDistance() {
        try {
            double v = Config.CAMERA_MAX_CAPTURE_DISTANCE.get();
            return v <= 0.0 ? Double.MAX_VALUE : v;
        } catch (Throwable ignored) {
            return FALLBACK_MAX_DISTANCE;
        }
    }

    /** 目标是否在射程内（按包围盒最近点计算，避免大体积 BOSS 被判超距）。 */
    public static boolean inRange(Vec3 cameraPos, Entity target, double maxDistance) {
        if (target == null || cameraPos == null) return false;
        if (maxDistance >= Double.MAX_VALUE) return true;
        return cameraPos.distanceTo(target.getBoundingBox().getCenter()) <= maxDistance;
    }

    // ==================== 视锥 ====================

    /**
     * 采样点是否落在以 {@code lookDir} 为中轴、半角 {@code halfAngleDeg} 的视锥内。
     */
    public static boolean inCone(Vec3 cameraPos, Vec3 lookDir, double halfAngleDeg, Vec3 sample) {
        Vec3 toSample = sample.subtract(cameraPos);
        if (toSample.lengthSqr() < 1.0E-8) return true;
        double cos = Math.cos(Math.toRadians(Math.max(0.0, Math.min(180.0, halfAngleDeg))));
        return lookDir.normalize().dot(toSample.normalize()) >= cos;
    }

    // ==================== 遮挡 ====================

    /**
     * 「遇方块就停止，不要透墙」——相机到目标是否存在无遮挡视线。
     * <p>
     * <b>以目标包围盒中心为准，单射线判定。</b>这一点是必须的，而不是保守选择：
     * Exposure 原生的 {@code hasLineOfSight} 只打「相机 → 实体<b>眼睛</b>」一根射线，
     * 且该检测<b>与我们是否混入无关、始终执行</b>。因此一个"整只被墙挡住"的生物
     * 根本进不到画面列表里；能被拍到的必然是<b>眼睛那根射线是通的</b>（露头、从门缝/窗口），
     * 而它的身体其实在墙后。所以只要沿用「眼睛可见即合格」，问题永远修不掉——
     * 必须改成要求<b>身体中心</b>可见，才等价于玩家直觉里的"这只怪在墙后，不该被拍到"。
     * <p>
     * 相机自身卡在方块内时（贴墙/埋方块）跳过遮挡判定，否则会全盲。
     */
    public static boolean hasClearSight(Level level, Vec3 cameraPos, Entity target) {
        if (level == null || cameraPos == null || target == null) return false;

        // 相机卡在碰撞体里：不做遮挡判定（否则全部目标都会被判为被挡住）
        if (!level.noCollision(new AABB(cameraPos, cameraPos).inflate(1.0E-4))) return true;

        Vec3 center = target.getBoundingBox().getCenter();
        if (cameraPos.distanceToSqr(center) < 1.0E-8) return true;
        return isRayClear(level, cameraPos, center, target);
    }

    /** 单段「相机 → 采样点」碰撞体射线；MISS = 畅通。 */
    private static boolean isRayClear(Level level, Vec3 from, Vec3 to, Entity context) {
        return level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context)).getType() == HitResult.Type.MISS;
    }

    // ==================== 组合判定 ====================

    /**
     * 完整判定：视锥 + 射程 + 遮挡，且自动回溯多部件（子部件可见即父实体可见）。
     *
     * @param halfAngleDeg 视锥半角（度）
     * @param maxDistance  最大距离（格），{@code <= 0} 或超大值表示不限
     */
    public static boolean isVisible(Level level, Vec3 cameraPos, Vec3 lookDir,
                                    double halfAngleDeg, double maxDistance, Entity target) {
        if (level == null || cameraPos == null || lookDir == null || target == null) return false;

        if (isSampleVisible(level, cameraPos, lookDir, halfAngleDeg, maxDistance, target, target.getEyePosition())) {
            return true;
        }

        PartEntity<?>[] parts = target.getParts();
        if (parts != null) {
            for (PartEntity<?> part : parts) {
                if (part == null || !part.isAlive()) continue;
                if (isSampleVisible(level, cameraPos, lookDir, halfAngleDeg, maxDistance, part, part.getEyePosition())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSampleVisible(Level level, Vec3 cameraPos, Vec3 lookDir, double halfAngleDeg,
                                           double maxDistance, Entity occlusionTarget, Vec3 coneSample) {
        if (!inCone(cameraPos, lookDir, halfAngleDeg, coneSample)) return false;
        if (!inRange(cameraPos, occlusionTarget, maxDistance)) return false;
        return hasClearSight(level, cameraPos, occlusionTarget);
    }

    /**
     * 过滤出相机真正看得见的存活生物（保持入参顺序）。
     */
    public static List<LivingEntity> filterVisible(Level level, Vec3 cameraPos, Vec3 lookDir,
                                                   double halfAngleDeg, double maxDistance, Iterable<LivingEntity> candidates) {
        List<LivingEntity> out = new ArrayList<>();
        if (candidates == null) return out;
        for (LivingEntity e : candidates) {
            if (e == null || !e.isAlive()) continue;
            if (isVisible(level, cameraPos, lookDir, halfAngleDeg, maxDistance, e)) out.add(e);
        }
        return out;
    }
}
