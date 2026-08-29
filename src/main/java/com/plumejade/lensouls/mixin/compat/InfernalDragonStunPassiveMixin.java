package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Infernal Dragon: disable the high-armor passive during stun.
 * Armor is set as a base value from ServerConfiguration.DRAGON_ARMOR at spawn; zero it during stun
 * and restore the original base value when the stun ends, so damage flows through the vanilla path.
 */
@Mixin(targets = "net.unusual.block_factorys_bosses.entity.boss.dragon.boss.InfernalDragonEntity", remap = false)
public abstract class InfernalDragonStunPassiveMixin {

    @Unique
    private double lensoulsSavedArmor = -1;

    @Inject(method = "tick", at = @At("RETURN"), remap = false, require = 0)
    private void lensoulsNeutralizeArmor(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        AttributeInstance armor = ((LivingEntity) (Object) this).getAttribute(Attributes.ARMOR);
        if (armor == null) return;
        if (StunPauseHelper.isStunPaused(self)) {
            if (lensoulsSavedArmor < 0) lensoulsSavedArmor = armor.getBaseValue();
            armor.setBaseValue(0);
        } else if (lensoulsSavedArmor >= 0) {
            armor.setBaseValue(lensoulsSavedArmor);
            lensoulsSavedArmor = -1;
        }
    }
}
