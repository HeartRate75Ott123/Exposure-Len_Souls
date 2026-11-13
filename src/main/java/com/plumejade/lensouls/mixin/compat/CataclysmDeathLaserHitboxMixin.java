package com.plumejade.lensouls.mixin.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 先驱者照片死亡激光：<b>判定盒收窄到贴合视觉</b>。
 *
 * <h2>本体判定为什么「比光柱粗一大圈」</h2>
 * {@code Death_Laser_Beam_Entity.raytraceEntities(...)}（实机 3.33 {@code javap} 核实）分两步：
 * <pre>
 *   AABB box = new AABB(起点, 命中点).inflate(1.0, 1.0, 1.0);        // ← 两侧各多 1 格
 *   for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
 *       float f = e.getPickRadius() + 0.5F;                          // ← 目标盒再胀 0.5+ 格
 *       if (e.getBoundingBox().inflate(f).clip(from, to).isPresent()) hit(e);
 *   }
 * </pre>
 * 叠起来光柱两侧各有约 1.5 格都算命中，而视觉上的光束只有零点几格宽 ——
 * 表现就是「激光还没扫到身上就已经掉血」。
 *
 * <h2>只改我们这条</h2>
 * 两处 {@code @Redirect} 都先看实体身上有没有 {@code lenssouls:photo_proj} 标记
 * （{@code markPercentAndSpawn} 打的），没有就原样返回，<b>本体先驱者的死亡激光手感完全不变</b>：
 * <ul>
 *   <li>射线的候选筛选用 {@code inflate(0.2)} 代替 {@code inflate(1,1,1)}；</li>
 *   <li>目标边距 {@code getPickRadius() + 0.5F} 里的 {@code getPickRadius()} 对我们这条返回
 *       {@code -0.35F}，加起来约 0.15 格 —— 光束贴到生物身上才算命中。
 *       （不改常量 0.5F 是为了不波及本体；这个负值是刻意用来抵掉那个常量的，别当成 bug。）</li>
 * </ul>
 * 标记读取都是 {@code CompoundTag.getBoolean}，只在光束每 tick 的一次判定里发生，开销可忽略。
 * 目标类不在编译期依赖里：由 {@code lensouls.compat.mixins.json}（{@code required:false} +
 * {@code defaultRequire:0}）兜，模组缺席或改版都不影响启动。
 */
@Mixin(targets = "com.github.L_Ender.cataclysm.entity.projectile.Death_Laser_Beam_Entity", remap = false)
public abstract class CataclysmDeathLaserHitboxMixin {

    /** 本模组照片弹幕标记（与 {@code PhotoProjMarker} 同一个键） */
    private static final String PHOTO_PROJ = "lensouls:photo_proj";

    /** 我们这条光束的候选筛选半径（格） */
    private static final double OUR_BOX_INFLATE = 0.2D;
    /** 本体半径，原样透传 */
    private static final double VANILLA_BOX_INFLATE = 1.0D;

    @Redirect(method = "raytraceEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;inflate(DDD)Lnet/minecraft/world/phys/AABB;",
                    ordinal = 0),
            remap = false, require = 0)
    private AABB lensouls$thinCandidateBox(AABB box, double dx, double dy, double dz) {
        double r = isOurBeam() ? OUR_BOX_INFLATE : VANILLA_BOX_INFLATE;
        return box.inflate(r, r, r);
    }

    @Redirect(method = "raytraceEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getPickRadius()F"),
            remap = false, require = 0)
    private float lensouls$tightTargetMargin(LivingEntity target) {
        // 本体公式是 f = getPickRadius() + 0.5F；这里返回 -0.35F 把那个常量抵掉大半
        return isOurBeam() ? -0.35F : target.getPickRadius();
    }

    private boolean isOurBeam() {
        return ((Entity) (Object) this).getPersistentData().getBoolean(PHOTO_PROJ);
    }
}
