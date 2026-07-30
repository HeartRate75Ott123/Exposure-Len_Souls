package com.plumejade.lensouls.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * 实体照片饰品物品。
 * 通过 CustomData 中的 stolen_entity 记录实体类型。
 * 可在 Curios photograph 槽中装备获得对应效果。
 */
public class EntityPhotographItem extends Item {

    public EntityPhotographItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(String entityId) {
        ItemStack stack = new ItemStack(ModItems.ENTITY_PHOTOGRAPH.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("lensouls:stolen_entity", entityId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        // 用实体名作为物品名，JEI 可搜索
        int colon = entityId.indexOf(':');
        String transKey = "entity." + entityId.substring(0, colon) + "." + entityId.substring(colon + 1);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("照片(").append(Component.translatable(transKey)).append(Component.literal(")"))
                        .withStyle(ChatFormatting.GREEN).withStyle(s -> s.withItalic(false)));
        return stack;
    }
}
