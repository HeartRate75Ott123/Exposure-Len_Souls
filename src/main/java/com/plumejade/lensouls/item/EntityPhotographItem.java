package com.plumejade.lensouls.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import com.plumejade.lensouls.integration.PhotoSpecialEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 实体照片饰品物品。
 * 通过 CustomData 中的 stolen_entity 记录实体类型。
 * 可在 Curios photograph 槽中装备获得对应效果。
 * <p>
 * 属性增益/负面由 Curios 佩戴时驱动（{@link #getAttributeModifiers}），
 * 同种实体照片只生效一份、不同种可叠加。
 */
public class EntityPhotographItem extends Item implements ICurioItem {

    public EntityPhotographItem(Properties properties) {
        super(properties);
    }

    /** 佩戴时按窃取实体派生属性修饰符（Curios 自动随装备/卸下增删） */
    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext,
                                                                               ResourceLocation slot, ItemStack stack) {
        String entityId = PhotographEffectRegistry.getStolenEntity(stack);
        if (entityId == null) return HashMultimap.create();
        return PhotoSpecialEffects.buildAttributeModifiers(entityId);
    }

    public static ItemStack create(String entityId) {
        ItemStack stack = new ItemStack(ModItems.ENTITY_PHOTOGRAPH.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("lensouls:stolen_entity", entityId);
        if (com.plumejade.lensouls.integration.PhotographEffectRegistry.hasEffect(entityId)) {
            tag.putBoolean("lensouls:photograph_curio", true);
        }
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
