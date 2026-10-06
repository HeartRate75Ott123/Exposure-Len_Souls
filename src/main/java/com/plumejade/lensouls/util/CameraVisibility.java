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
 *   <li><b>遮挡</b>：从相机到目标包围盒<b>多个身体采样点</b>做 {@code COLLIDER} 射线，遇方块即止
 *       （{@link #hasClearSightTo}）：<b>任一采样点</b>在视锥内且视线通畅即算拍到。
 *       采样点从脚到头排列（脚底优先），因此「上半身被方块挡住、只露脚／下半身」也能拍进去；
 *       整只被墙挡住时所有采样点都不通，依然拍不到（不会退化成透墙）。</li>
 * </ol>
 * <p>
 * 注意区分两个口径：{@link #hasClearSight} 是「只认身体中心」的严格版，仍供
 * {@link AimTargetUtil}（要害打击/断魂的瞄准）使用——那里刻意要求中心可见，避免隔墙锁头；
 * 拍照捕捉走的是 {@link #isVisible} 的多点采样版。
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
     * 「遇方块就停止，不要透墙」——相机到<b>目标包围盒中心</b>是否存在无遮挡视线（严格版）。
     * <p>
     * <b>只供瞄准类判定（{@link AimTargetUtil}）使用</b>，拍照捕捉请用
     * {@link #isVisible}（多采样、脚底优先）。这里要求身体中心可见是刻意的：
     * 要害打击/断魂不该隔着墙锁头。
     * <p>
     * 相机自身卡在方块内时（贴墙/埋方块）跳过遮挡判定，否则会全盲。
     */
    public static boolean hasClearSight(Level level, Vec3 cameraPos, Entity target) {
        if (level == null || cameraPos == null || target == null) return false;
        return hasClearSightTo(level, cameraPos, target, target.getBoundingBox().getCenter());
    }

    /**
     * 「遇方块就停止」的<b>单点</b>视线判定：相机 → {@code sample} 之间没有碰撞体方块即算通畅。
     * <p>
     * 相机自身卡在碰撞体里时（贴墙/埋在方块中）不做遮挡判定，否则会全盲。
     */
    public static boolean hasClearSightTo(Level level, Vec3 cameraPos, Entity context, Vec3 sample) {
        if (level == null || cameraPos == null || sample == null) return false;
        if (!level.noCollision(new AABB(cameraPos, cameraPos).inflate(1.0E-4))) return true;
        if (cameraPos.distanceToSqr(sample) < 1.0E-8) return true;
        return isRayClear(level, cameraPos, sample, context);
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
        // 第三方辅助作战单位（gytrinket 无人机/蜂群/僚机）一律不算拍摄目标。
        // 分类定义在 AllyFilter（「谁是自己人」的唯一事实来源）；过滤发生在「逐个候选」这一层，
        // 调用方自然会继续看下一个生物。
        if (AllyFilter.isAssistConstruct(target)) return false;

        if (isEntityVisible(level, cameraPos, lookDir, halfAngleDeg, maxDistance, target)) {
            return true;
        }

        PartEntity<?>[] parts = target.getParts();
        if (parts != null) {
            for (PartEntity<?> part : parts) {
                if (part == null || !part.isAlive()) continue;
                if (isEntityVisible(level, cameraPos, lookDir, halfAngleDeg, maxDistance, part)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 身体采样比例（相对包围盒高度，从脚到头）。
     * <p>
     * <b>脚底优先</b>：生物上半身被方块挡住、只露出脚或下半身时也要能拍到——这正是「拍脚底」的需求。
     * 整只被墙挡住时所有采样点都不通，依然拍不到（不会退化成透墙）。
     * 命中即返回：视线通畅的正常情况第一个采样点就通过，只有真被挡时才逐点退让，平均射线数仍接近 1 根。
     */
    private static final double[] BODY_SAMPLE_FRACTIONS = {0.08D, 0.28D, 0.5D, 0.72D};

    /** 单个实体（或子部件）：射程 → 逐身体采样点（视锥 + 视线）→ 眼睛（补「细高生物只露头」）。 */
    private static boolean isEntityVisible(Level level, Vec3 cameraPos, Vec3 lookDir, double halfAngleDeg,
                                           double maxDistance, Entity entity) {
        if (!inRange(cameraPos, entity, maxDistance)) return false;

        AABB box = entity.getBoundingBox();
        double cx = (box.minX + box.maxX) * 0.5D;
        double cz = (box.minZ + box.maxZ) * 0.5D;
        double height = box.getYsize();
        for (double fraction : BODY_SAMPLE_FRACTIONS) {
            Vec3 sample = new Vec3(cx, box.minY + height * fraction, cz);
            if (!inCone(cameraPos, lookDir, halfAngleDeg, sample)) continue;
            if (hasClearSightTo(level, cameraPos, entity, sample)) return true;
        }

        // 眼睛单独再试一次：细高生物「只露头」时，头部比 0.72 高度更高
        Vec3 eye = entity.getEyePosition();
        if (inCone(cameraPos, lookDir, halfAngleDeg, eye) && hasClearSightTo(level, cameraPos, entity, eye)) {
            return true;
        }
        return false;
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
