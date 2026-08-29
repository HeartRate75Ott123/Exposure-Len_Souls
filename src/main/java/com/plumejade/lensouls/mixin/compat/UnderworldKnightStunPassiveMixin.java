package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Underworld Knight: disable the "immune stacks" passive during stun.
 * Each stack blocks one hit and reduces damage; force-clear stacks before every hit
 * so player damage flows through the vanilla hurt path.
 */
@Mixin(targets = "net.unusual.block_factorys_bosses.entity.boss.knight.UnderworldKnightEntity", remap = false)
public abstract class UnderworldKnightStunPassiveMixin {

    @Shadow
    public abstract void setImmuneStacks(int pStacks);

    @Inject(method = "hurt", at = @At("HEAD"), remap = false, require = 0)
    private void lensoulsDisableImmuneStacks(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (StunPauseHelper.isStunPaused((Entity) (Object) this)) {
            this.setImmuneStacks(0);
        }
    }
}
