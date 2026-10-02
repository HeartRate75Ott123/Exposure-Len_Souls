package com.plumejade.lensouls.util;

import io.github.mortuusars.exposure.util.Fov;
import io.github.mortuusars.exposure.util.PointOfView;
import io.github.mortuusars.exposure.world.camera.frame.EntitiesInFrame;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 「照片入镜实体列表」的组装 —— Exposure {@code EntitiesInFrame.get} 的收口策略。
 * <p>
 * 由 {@code mixin/EntitiesInFrameMixin} 的单一 TAIL 注入调用，是这条链路的<b>唯一实现</b>。
 *
 * <p><b>职责边界（别搞混）：</b>
 * <ul>
 *   <li>{@link CameraVisibility} = <b>纯几何判定原语</b>（视锥 + 多身体采样 + 逐点方块遮挡 + 射程上限）。
 *       它同时服务要害打击 / 断魂的瞄准（那里<b>必须</b>只认包围盒中心 {@code hasClearSight}），
 *       所以不要把「组列表 / 放宽」这类<b>策略</b>塞进去 —— 一旦混用就会退化成隔墙锁头。</li>
 *   <li>本类 = <b>Exposure 领域的列表策略</b>：收窄 → 补回 → 子部件 → 排序。
 *       焦距口径（{@link Fov#fovToFocalLength} / {@link EntitiesInFrame#calculateVisibleDistance}）
 *       只属于这里。</li>
 * </ul>
 *
 * <p><b>为什么需要「补回」：</b>Exposure 自己的预筛只看<b>眼睛点</b>
 * （{@code frustum.contains(entity.getEyePosition())} + 对眼睛的 {@code hasLineOfSight}）。
 * 拍高大生物的下半身 / 脚时，它的眼睛在画面上沿之外 ⇒ 实体在进入我们的逻辑<b>之前</b>就被丢掉，
 * 表现为「照片里看得见脚，却削不了韧 / 吃不到弱点 / 偷不到能力」。
 * 所以这里按 Exposure 同口径（焦距距离）复查候选，再用多身体采样点的
 * {@link CameraVisibility#isVisible} 决定是否补回。
 *
 * <p><b>不会隔墙生效：</b>补回的每个身体采样点都要过 {@code ClipContext.Block.COLLIDER}
 * 的视线检查，整只被墙挡住时所有采样点都不通。
 *
 * <p><b>为什么是「事后对齐」而不是 {@code @Redirect} Exposure 的预筛：</b>
 * {@code FrustumCheck.contains(Vec3)} 的 redirect <b>拿不到「正在被检测的是哪个实体」</b>
 * —— 上一版为此引入 ThreadLocal + 隐式参数捕获，且曾<b>静默失效</b>（不报错、只是过滤恒不生效）。
 * 这一段普通 Java 没有那类签名歧义。
 */
public final class FrameEntities {

    /** 与 Exposure 一致：fov 先取 5% 边缘余量 */
    public static final double FOV_MARGIN = 0.95;

    /** 补回扫描的最大半径（在射程上限内再收一点，避免无谓的实体遍历） */
    private static final double MAX_SCAN_RADIUS = 128.0;

    private FrameEntities() {
    }

    /**
     * 整理最终入镜列表：收窄 Exposure 给的结果 → 补回被它眼睛预筛刷掉的 → 补子部件父实体 → 按距离排序。
     *
     * @param cameraHolder   相机持有实体（搜索范围与所在世界都以它为准）
     * @param exposureResult Exposure {@code EntitiesInFrame.get} 的原始返回。
     *                       <b>null / 空也必须继续补回</b>：只露脚的高大生物正是被它的眼睛预筛刷掉的那种，
     *                       若在此早退，补回等于没做。
     * @return 新的可变列表（调用方直接交回 Exposure），保持 {@code get(0)} = 最近主体
     */
    public static List<LivingEntity> assemble(Entity cameraHolder, PointOfView pov, double fov,
                                              List<LivingEntity> exposureResult) {
        Level level = cameraHolder.level();
        Vec3 camPos = pov.pos();
        Vec3 lookDir = pov.dir();
        // Exposure 的 FrustumCheck 把 fov 当全角用（halfSize = depth * tan(fov / 2)），故半角 = fov / 2
        double halfAngleDeg = (fov * FOV_MARGIN) / 2.0;
        double maxDistance = CameraVisibility.maxCaptureDistance();

        // ⚠ 相机持有者（玩家自己）绝不在「画面内实体」里：
        // Exposure 的眼睛视锥天然把它排除（眼睛就在相机位置，depth ≈ 0），但**本方法的补回一点都可能把它捞回来**
        // —— 视线朝下时自己的脚/身体就在视锥里，而且「对自己的射线」不会被自己的碰撞箱挡住 ⇒ isVisible 通过。
        // 它到相机的距离 ≈ 0，排序后必定落在 get(0) ⇒ 下游所有「取第 0 个当主体」的消费者
        // （能力窃取主体、弱点透镜记录弱点、时间定格…）都会变成玩家自己：实测症状就是
        // 「弱点透镜记录的弱点全是同一个元素（玩家那条 entity_weakness 配置）」，与拍摄对象无关。
        boolean holderIsLiving = cameraHolder instanceof LivingEntity;

        // 1) 收窄：Exposure 已含视锥 + 焦距距离 + 单点（眼睛）视线，这里再按我们的多采样口径复判
        List<LivingEntity> visible = new ArrayList<>();
        if (exposureResult != null) {
            for (LivingEntity candidate : exposureResult) {
                if (candidate == null || !candidate.isAlive()) continue;
                if (candidate == cameraHolder) continue;      // 防御：持有者永不算主体
                if (CameraVisibility.isVisible(level, camPos, lookDir, halfAngleDeg, maxDistance, candidate)) {
                    visible.add(candidate);
                }
            }
        }

        // 2) 补回（本体）+ 子部件→父实体：一趟扫描做完，省一次实体遍历
        double focalLength = Fov.fovToFocalLength(fov * FOV_MARGIN);
        AABB area = new AABB(cameraHolder.blockPosition())
                .inflate(Math.min(maxDistance, MAX_SCAN_RADIUS) + 16.0);
        for (Entity entity : level.getEntities(null, area)) {
            // 2a) 子部件（九头蛇头 / 娜迦体节）：不是 LivingEntity，Exposure 会丢掉 → 补其父实体
            if (entity instanceof PartEntity<?> part) {
                if (!part.isAlive()) continue;
                Entity parent = part.getParent();
                if (!(parent instanceof LivingEntity living) || !living.isAlive()) continue;
                if (living == cameraHolder) continue;         // 部件链回到持有者 ⇒ 同样不算
                if (visible.contains(living)) continue;
                if (!CameraVisibility.isVisible(level, camPos, lookDir, halfAngleDeg, maxDistance, part)) continue;
                visible.add(living);
                continue;
            }
            // 2b) 本体：Exposure 只看眼睛点，拍脚 / 下半身时会被它刷掉
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (holderIsLiving && living == cameraHolder) continue;   // ← 别把玩家自己补回来（见上方警告）
            if (visible.contains(living)) continue;
            // 与 Exposure 同口径的焦距阈值（镜头越广越近、越长焦越远），不额外放宽
            if (EntitiesInFrame.calculateVisibleDistance(camPos, living) > focalLength) continue;
            if (!CameraVisibility.isVisible(level, camPos, lookDir, halfAngleDeg, maxDistance, living)) continue;
            visible.add(living);
        }

        // 3) Exposure 原本就是按「到相机的距离」升序（它用 entity.position() 排序），保持一致 ⇒ get(0) = 最近主体
        visible.sort(Comparator.comparingDouble(candidate -> camPos.distanceTo(candidate.position())));
        return visible;
    }
}
