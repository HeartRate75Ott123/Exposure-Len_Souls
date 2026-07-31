package com.plumejade.lensouls.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 复制之魂：工作台与任意物品合成，输出该物品的完整副本（附魔、组件、NBT 全保留）。
 * <p>
 * 1 复制之魂 + 任意物品 → 完整副本（数量等量）；合成逻辑见 {@link com.plumejade.lensouls.recipe.CopySoulRecipe}。
 */
public class CopySoulItem extends Item {

    public CopySoulItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lensouls.copy_soul.desc"));
    }
}
