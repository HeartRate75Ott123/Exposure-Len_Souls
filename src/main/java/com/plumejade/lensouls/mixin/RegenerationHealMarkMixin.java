package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.handler.curse.HealSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 给「生命恢复（Regeneration）效果带来的治疗」打来源标记。
 * <p>
 * {@code RegenerationMobEffect} 是**包私有**类（{@code javap} 显示 {@code class} 而非
 * {@code public class}），不能直接引用类型 ⇒ 用 {@code targets} 字符串目标。
 * {@code applyEffectTick} 里只有一处 {@code LivingEntity.heal(F)}（{@code javap} 核实），
 * 重定向到 {@link HealSource#healAsRegeneration} 即可让 ⑤ e8 反转的
 * 「再生类效果正常生效」在 {@code LivingHealEvent} 侧可判定。
 */
@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
public class RegenerationHealMarkMixin {

    @Redirect(
            method = "applyEffectTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
    private void lensouls$regenHeal(LivingEntity entity, float amount) {
        HealSource.healAsRegeneration(entity, amount);
    }
}
