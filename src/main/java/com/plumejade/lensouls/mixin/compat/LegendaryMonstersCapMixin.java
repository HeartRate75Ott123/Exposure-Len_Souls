package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.damage.ElementBypassHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 传奇怪物 BOSS 单次伤害上限绕过 Mixin。
 * <p>
 * 传奇怪物基类 {@code IAnimatedBoss.hurt()} 中有：
 * {@code amount = (float) Math.min(damageCap(), amount)}
 * <p>
 * 当武器活性等级 5 + 目标有对应弱点，或目标处于我方定身时，绕过此上限。
 * <p>
 * <b>版本差异（实测 2.2.2）：</b>2.1.20 的上限调用是 {@code Math.min(DD)D}（double），
 * 而 2.2.2 改成了 {@code Math.min(FF)F}（float）。靶点必须两个都挂、各自 {@code require = 0}，
 * 否则在对应版本上 {@code @ModifyArg} 会静默失配（表现就是「绕上限完全没生效、伤害照旧被压」）。
 * 传奇怪物没有伤害桶，不需要 bucket reset。
 */
@Mixin(value = net.miauczel.legendary_monsters.entity.AnimatedMonster.OriginClasses.IAnimatedBoss.class, remap = false)
public abstract class LegendaryMonstersCapMixin {

    @Inject(method = "hurt", at = @At("HEAD"), remap = false, require = 0)
    private void lensouls$checkBypass(DamageSource source, float amount,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        // 只需要设置 cap 绕过标志（无 bucket）
        ElementBypassHelper.evaluateAndShouldBypassBucket(source, self);
    }

    @ModifyArg(
            method = "hurt",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(DD)D"),
            index = 0,
            remap = false,
            require = 0
    )
    private double lensouls$bypassDamageCap(double capValue) {
        if (ElementBypassHelper.shouldBypassCap()) {
            return Double.MAX_VALUE;
        }
        return capValue;
    }

    /**
     * 传奇怪物 2.2.2 起的 float 版靶点（{@code Math.min(FF)F}）。
     * 2.1.20 没有这条调用，{@code require = 0} 保证旧版安静跳过。
     */
    @ModifyArg(
            method = "hurt",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F"),
            index = 0,
            remap = false,
            require = 0
    )
    private float lensouls$bypassDamageCapFloat(float capValue) {
        if (ElementBypassHelper.shouldBypassCap()) {
            return Float.MAX_VALUE;
        }
        return capValue;
    }
}
