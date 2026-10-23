package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.handler.curse.CurseLootBonus;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ⑤ e15 厄运 反转态「抢夺 +2」：在**原版询问「这个攻击者有几级抢夺」**时回答「+2」。
 * <p>
 * 1.21.1 的生物战利品表用 {@code minecraft:enchanted_count_increase}，它的取级口径是
 * {@code EnchantmentHelper.getEnchantmentLevel(Enchantments.LOOTING, ATTACKING_ENTITY)}
 * （反编译 {@code EnchantedCountIncreaseFunction} 确认：它不是读 TOOL，而是读**攻击者身上
 * 按附魔槽位汇总的等级**）。所以这里加 2 等价于「手上那把武器的抢夺等级高 2」——
 * 战利品表照旧按原版公式算，没有写抢夺的生物（以及生物的非抢夺掉落）一个都不会多。
 * <p>
 * 只对 {@code minecraft:looting} 生效；非玩家攻击者当场返回，热路径上只有一次
 * {@code instanceof} + 一次 {@code Holder#is}。
 */
@Mixin(EnchantmentHelper.class)
public class EnchantmentHelperLootingMixin {

    @Inject(
            method = "getEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/LivingEntity;)I",
            at = @At("RETURN"),
            cancellable = true)
    private static void lensouls$curseLootingBonus(Holder<Enchantment> enchantment, LivingEntity entity,
                                                   CallbackInfoReturnable<Integer> cir) {
        if (entity == null || !enchantment.is(Enchantments.LOOTING)) return;
        int bonus = CurseLootBonus.extraLooting(entity);
        if (bonus <= 0) return;
        cir.setReturnValue(cir.getReturnValueI() + bonus);
    }
}
