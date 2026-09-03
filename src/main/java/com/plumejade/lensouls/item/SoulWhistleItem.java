package com.plumejade.lensouls.item;

import com.plumejade.lensouls.handler.BroochEffectHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 灵魂口哨：Curios 任意槽位饰品。
 * <p>
 * 佩戴时自身造成伤害 -80%；幻灵（借体本体 + 召唤物）伤害增强：
 * 每受一次伤害 +50%/层（cap 10，刷新式 10s），并让幻灵命中 40% 概率改为
 * 目标最大生命 30% 的魔法穿透伤害。
 * 与法师胸针互斥，不可同戴。效果逻辑见 {@link com.plumejade.lensouls.handler.WhistlePhantomHandler}。
 */
public class SoulWhistleItem extends Item implements ICurioItem {

    public SoulWhistleItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    /** 互斥：已佩戴法师胸针则无法佩戴口哨 */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return !BroochEffectHandler.hasBrooch(player);
        }
        return true;
    }

    @Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, TooltipContext context, ItemStack stack) {
        return List.of(Component.translatable("item.lensouls.curio_any_slot")
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lensouls.soul_whistle.desc1"));
        tooltip.add(Component.translatable("item.lensouls.soul_whistle.desc2"));
        tooltip.add(Component.translatable("item.lensouls.soul_whistle.desc3"));
        tooltip.add(Component.translatable("item.lensouls.soul_whistle.desc4"));
    }
}
