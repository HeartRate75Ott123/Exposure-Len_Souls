package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sandworm: disable the "non-exposed invulnerable" passive during stun.
 * isInvulnerable() returns true while not exposed; force false during stun
 * so damage flows through the vanilla hurt path.
 */
@Mixin(targets = "net.unusual.block_factorys_bosses.entity.boss.sandworm.SandwormEntity", remap = false)
public abstract class SandwormStunPassiveMixin {

    @Inject(method = "isInvulnerable", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lensoulsStunExpose(CallbackInfoReturnable<Boolean> cir) {
        if (StunPauseHelper.isStunPaused((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
