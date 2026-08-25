package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.handler.IgnisBrandHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 焰魔照片弹幕（火球）命中实体时，对其叠加一层炽焰烙印（减甲减韧）。
 * <p>
 * 仅对「照片弹幕」标记的火球生效（{@code lensouls:photo_proj}），
 * 普通灾变火球不施加；仅作用于非玩家生物，避免误伤玩家自身。
 */
@Mixin(value = com.github.L_Ender.cataclysm.entity.projectile.Ignis_Fireball_Entity.class, remap = false)
public abstract class IgnisFireballBrandMixin {

    @Inject(method = "onHitEntity", at = @At("HEAD"), remap = false, require = 0)
    private void lensouls$applyBrandOnHit(EntityHitResult pResult, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.getPersistentData().getBoolean("lensouls:photo_proj")) return;
        Entity hit = pResult.getEntity();
        if (!(hit instanceof LivingEntity target)) return;
        if (target instanceof Player) return;
        IgnisBrandHandler.applyIgnisArmorBreak(target);
    }
}
