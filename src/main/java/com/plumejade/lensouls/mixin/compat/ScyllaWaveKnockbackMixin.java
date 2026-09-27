package com.plumejade.lensouls.mixin.compat;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 斯库拉照片弹幕（水波）击退减弱。
 * <p>
 * 灾变 {@code Wave_Entity.attackEntities(strength, x, z)} 里 {@code strength} 只用于计算击退
 * （{@code adjustedStrength = strength * knockbackScale}，随后 {@code entity.setDeltaMovement(...)}），
 * 原版由 {@code tick} 传入固定 {@code 1.5}，且水波存活 60 tick、每 tick 对重叠目标结算一次
 * —— 3 道水波叠加后是持续推挤，实战手感非常"飘"。照片版把 strength 压到 0.35 倍
 * （约 −65% 击退），伤害/湿润效果完全不动。
 * <p>
 * 仅对带 {@code lensouls:photo_proj} 标记的水波生效；斯库拉本体和其他来源的水波保持原样。
 * 位于 {@code lensouls.compat.mixins.json}（required=false），灾变未安装时不加载。
 */
@Mixin(targets = "com.github.L_Ender.cataclysm.entity.effect.Wave_Entity", remap = false)
public class ScyllaWaveKnockbackMixin {

    /** 照片水波击退倍率（0.35 ≈ 原版的三分之一） */
    private static final double LENSOULS_PHOTO_WAVE_KNOCKBACK = 0.35D;

    @ModifyVariable(method = "attackEntities", at = @At("HEAD"), argsOnly = true, index = 1,
            remap = false, require = 0)
    private double lensouls$reducePhotoWaveKnockback(double strength) {
        Entity self = (Entity) (Object) this;
        if (self.getPersistentData().getBoolean("lensouls:photo_proj")) {
            return strength * LENSOULS_PHOTO_WAVE_KNOCKBACK;
        }
        return strength;
    }
}
