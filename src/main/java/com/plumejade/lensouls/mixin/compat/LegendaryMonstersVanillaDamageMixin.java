package com.plumejade.lensouls.mixin.compat;

import net.miauczel.legendary_monsters.entity.AnimatedMonster.OriginClasses.IAnimatedMonster;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 传奇怪物 BOSS 强制走原版伤害流程（兼容）。
 * <p>
 * {@code IAnimatedBoss} 用自定义字段 {@code totalDamageTaken} 作为真实血量来源：
 * {@code getHealth() = maxHealth - totalDamageTaken}、{@code setHealth()} 服务端空操作，
 * 真实伤害在 {@code LivingIncomingDamageEvent} 时点由 {@code ForgeEvents.addDamage()} 提前存档，
 * 导致 {@code LivingDamageEvent.Pre} 里的所有伤害修改（韧性减伤、弱点、活性、强袭等）无法落地并产生"回弹"。
 * <p>
 * 三个注入拉回原版管线：{@code getHealth} 读原版字段、{@code setHealth} 正常扣血、{@code addDamage} 空操作。
 * 副作用：受击攻击冷却（hurtCD）与伤害适应减伤被移除（按"强行走原版伤害流程"接受）。
 */
@Mixin(value = net.miauczel.legendary_monsters.entity.AnimatedMonster.OriginClasses.IAnimatedBoss.class, remap = false)
public abstract class LegendaryMonstersVanillaDamageMixin extends IAnimatedMonster {

    @SuppressWarnings("unchecked")
    public LegendaryMonstersVanillaDamageMixin(EntityType<?> type, Level level) {
        super((EntityType<? extends IAnimatedMonster>) type, level);
    }

    @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lensouls$vanillaGetHealth(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(super.getHealth());
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lensouls$vanillaSetHealth(float newHealth, CallbackInfo ci) {
        super.setHealth(newHealth);
        ci.cancel();
    }

    @Inject(method = "addDamage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lensouls$noopAddDamage(float amount, DamageSource source, CallbackInfo ci) {
        ci.cancel();
    }
}
