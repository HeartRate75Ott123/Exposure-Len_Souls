package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import com.plumejade.lensouls.handler.FeatherAbyssHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 食之无味（④ e9）与止息（⑤ e8）在「食物驱动的自然回血」这一处的统一口径。
 * <p>
 * 食物驱动的自然回血在 {@code FoodData.tick} 内通过 {@code Player.heal} 结算
 * （饱和度过高时回血、满饥饿时回血两处），本 mixin 是唯一改写点：
 * <ul>
 *   <li><b>④ e9 食之无味·诅咒态</b>（佩戴折翼沉渊且该条未反转）：对佩戴者一律空操作，
 *       饥饿值/饱和度照常消耗；</li>
 *   <li><b>④ e9 反转</b>：放行并把回血量 <b>×2</b>；</li>
 *   <li><b>⑤ e8 止息反转</b>：生命自然恢复 <b>×2</b>；</li>
 * </ul>
 * 两条反转可**同时成立**（乘起来 = ×4）。即时回血类（如蜂蜜瓶）走 LivingEntity 其他路径，不在本 mixin 范围。
 */
@Mixin(FoodData.class)
public class FoodDataMixin {

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V")
    )
    private void lensouls$foodHealing(Player player, float amount) {
        try {
            float mult = 1.0f;

            if (FeatherAbyssHandler.hasAbyss(player)) {
                // ④ e9：诅咒态直接拦截（不回血）；只有该条**已反转**时才放行并 ×2
                if (!CurseManager.rev(player, CurseDefs.ABYSS, 8)) {
                    return;
                }
                mult *= 2.0f;
            }

            // ⑤ e8 止息反转：生命自然恢复 ×2（与 ④ e9 反转相乘，两者可同时成立）
            if (CurseManager.rev(player, CurseDefs.TIMECORE, 7)) {
                mult *= 2.0f;
            }

            player.heal(amount * mult);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ e9 / ⑤ e8 食物回血结算异常", t);
            player.heal(amount);
        }
    }
}
