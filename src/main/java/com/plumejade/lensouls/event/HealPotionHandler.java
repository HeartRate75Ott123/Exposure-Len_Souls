package com.plumejade.lensouls.event;

import com.plumejade.lensouls.item.HealPotionItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 回复药水维度同步（低频，降低性能开销）。
 * <p>
 * 每 20 tick：记录玩家当前维度；若主/副手持回复药水，则同步已到访维度数到物品。
 */
public class HealPotionHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (player.tickCount % 20 != 0) return;

        // 记录当前维度（与是否手持无关，避免漏记历史维度）
        int count = HealPotionItem.getPlayerVisitedCount(player);

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof HealPotionItem) {
                HealPotionItem.setVisitedCount(stack, count);
            }
        }
    }
}
