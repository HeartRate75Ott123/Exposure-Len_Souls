package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 破刹 / 时间定格中的末影人不再免疫投射物（箭可正常造成伤害）。
 * <p>
 * 原版 {@link EnderMan#hurt} 对 {@code IS_PROJECTILE} 来源的伤害直接返回 false（完全免疫，
 * 弓箭射不中打不出伤害）。当末影人处于破刹（韧性打破）或时间定格（定身）时，
 * 改写该判定使其走正常受伤路径。
 */
@Mixin(EnderMan.class)
public class EndermanStunProjectileMixin {

    @Redirect(
            method = "hurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z")
    )
    private boolean lensouls$allowProjectileWhenStunned(DamageSource source, TagKey<DamageType> tag,
                                                        EnderMan self, DamageSource damageSource, float amount) {
        if (StunPauseHelper.isStunPaused(self)) {
            return false;
        }
        return source.is(tag);
    }
}