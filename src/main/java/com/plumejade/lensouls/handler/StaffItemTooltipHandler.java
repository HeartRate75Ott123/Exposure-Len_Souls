package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.config.StaffItemLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 法杖类武器 tooltip 处理器。
 * <p>
 * 若手持物品属于 {@code staff_item} 数据包定义的法杖（客户端缓存经 DatapackSyncPacket 同步），
 * 在 tooltip 末尾追加一行金黄字「法杖」。
 */
public class StaffItemTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (!StaffItemLoader.isStaff(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return;
        event.getToolTip().add(Component.translatable("item.lensouls.staff_tag")
                .withStyle(ChatFormatting.GOLD));
    }
}
