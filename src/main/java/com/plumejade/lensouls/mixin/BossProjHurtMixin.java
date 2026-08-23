package com.plumejade.lensouls.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 照片 Boss 弹幕命中后清空目标无敌帧：被标记 {@code lensouls:photo_proj} 的弹射物命中时，
 * 将目标 invulnerableTime 清零，使多段判伤技能（激光/符文/尖刺等）能全额连续造成伤害。
 */
@Mixin(LivingEntity.class)
public abstract class BossProjHurtMixin {

    @Inject(method = "hurt", at = @At("TAIL"))
    private void lensouls$clearInvulnForPhotoProj(DamageSource source, float amount,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            Entity direct = source.getDirectEntity();
            if (direct != null && direct.getPersistentData().getBoolean("lensouls:photo_proj")) {
                ((LivingEntity) (Object) this).invulnerableTime = 0;
            }
        }
    }
}
