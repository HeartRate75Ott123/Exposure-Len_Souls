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
 * <b>版本差异（javap 逐版核对）：</b>2.1.20 的上限调用是 {@code Math.min(DD)D}（double），
 * 2.2.2 改成了 {@code Math.min(FF)F}（float），<b>2.2.4 又改回了 {@code Math.min(DD)D}</b>
 * （{@code Math.min(FF)F} 在 2.2.4 只剩于 {@code addDamage}/{@code readAdditionalSaveData}，
 * {@code hurt} 里已经没有）。所以两个靶点都要挂、各自 {@code require = 0}：哪个版本用哪个就命中哪个，
 * 另一个安静跳过（2.2.4 上跳过的是 float 那个）。少挂一个的后果是 {@code @ModifyArg} 静默失配
 * （表现就是「绕上限完全没生效、伤害照旧被压」）。
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
     * float 版靶点（{@code Math.min(FF)F}）：2.2.2 有、2.1.20 和 2.2.4 都没有。
     * {@code require = 0} 保证在没这条调用的版本上安静跳过（2.2.4 就是这种情况）。
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
