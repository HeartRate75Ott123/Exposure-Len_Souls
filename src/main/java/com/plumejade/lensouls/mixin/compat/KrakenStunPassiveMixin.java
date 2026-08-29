package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Kraken: disable the "invulnerability effect" passive during stun.
 * The boss periodically applies INVULNERABILITY_EFFECT to itself; remove it before every hit
 * so damage flows through the vanilla hurt path.
 */
@Mixin(targets = "net.unusual.block_factorys_bosses.entity.boss.kraken.KrakenEntity", remap = false)
public abstract class KrakenStunPassiveMixin {

    @Shadow
    public static MobEffectInstance INVULNERABILITY_EFFECT;

    @Inject(method = "hurt", at = @At("HEAD"), remap = false, require = 0)
    private void lensoulsDisableInvuln(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (StunPauseHelper.isStunPaused((Entity) (Object) this)) {
            MobEffectInstance inst = INVULNERABILITY_EFFECT;
            if (inst != null) {
                ((LivingEntity) (Object) this).removeEffect(inst.getEffect());
            }
        }
    }
}
