package com.plumejade.lensouls.reinforce;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * 强化材料 tooltip：给数据包中配置了效果的材料物品自动追加说明行。
 * <p>
 * 材料效果完全由数据包定义（服务端解析 → 随 {@code DatapackSyncPacket} 下发到客户端缓存），
 * 因此多人模式下客机同样能看到正确内容。已强化过的物品额外显示已强化项数。
 */
public class ReinforceTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        List<Component> tooltip = event.getToolTip();

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ReinforceMaterial material = ReinforceDataLoader.getMaterial(itemId);
        if (material != null && !material.modifiers().isEmpty()) {
            tooltip.add(Component.translatable("item.lensouls.reinforce.material_header")
                    .withStyle(ChatFormatting.DARK_GRAY));
            for (ReinforceModifier modifier : material.modifiers()) {
                tooltip.add(Component.literal(" ").append(modifier.describe())
                        .append(" ").append(modifier.slotSuffix()));
            }
            for (String line : material.desc()) {
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        }

        int used = ReinforceHelper.usedCount(stack);
        if (used > 0) {
            tooltip.add(Component.translatable("item.lensouls.reinforce.used", used)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
