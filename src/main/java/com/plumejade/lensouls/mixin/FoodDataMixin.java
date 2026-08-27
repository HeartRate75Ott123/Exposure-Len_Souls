package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.handler.FeatherAbyssHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 食之无味：羽·折翼沉渊佩戴期间无法通过食物回血。
 * <p>
 * 食物驱动的自然回血在 {@code FoodData.tick} 内通过 {@code Player.heal} 结算
 * （饱和度过高时回血、满饥饿时回血两处），对佩戴者一律空操作，
 * 饥饿值/饱和度照常消耗。即时回血类（如蜂蜜瓶）走 LivingEntity 其他路径，不在本 mixin 范围。
 */
@Mixin(FoodData.class)
public class FoodDataMixin {

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V")
    )
    private void lensouls$blockFoodHealing(Player player, float amount) {
        if (!FeatherAbyssHandler.hasAbyss(player)) {
            player.heal(amount);
        }
    }
}
