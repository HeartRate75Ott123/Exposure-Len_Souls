package com.plumejade.lensouls.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 石之心：Curios 任意槽位饰品。
 * <p>
 * 效果：<b>单次受到的伤害被限制为佩戴者最大生命值的 25%</b>（无论这一击原本多高），
 * 代价是<b>受到的所有伤害 +17%</b>。逻辑见
 * {@link com.plumejade.lensouls.handler.HeartOfStoneHandler}。
 * <p>
 * 不与其他饰品互斥（互斥关系只存在于四根羽毛之间、以及胸针/口哨之间）。
 */
public class HeartOfStoneItem extends Item implements ICurioItem {

    public HeartOfStoneItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    /** 栏位提示：替换 Curios 生成的槽位列表，统一显示「任意饰品栏」 */
    @Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, TooltipContext context, ItemStack stack) {
        return List.of(Component.translatable("item.lensouls.curio_any_slot")
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lensouls.heart_of_stone.desc1"));
        tooltip.add(Component.translatable("item.lensouls.heart_of_stone.desc2"));
        tooltip.add(Component.translatable("item.lensouls.heart_of_stone.desc3"));
    }
}
