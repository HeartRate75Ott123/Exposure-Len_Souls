package com.plumejade.lensouls.mixin.compat;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import com.plumejade.lensouls.integration.PhotoSpecialEffects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 让真实的 Exposure 照片（含能力窃取产出）也提供 Curios 属性修饰符。
 * <p>
 * 属性原来只在 {@code EntityPhotographItem}（创造照片）里派生；普通拍照/能力窃取
 * 产出的是 Exposure 的 {@code PhotographItem}，未实现 {@link ICurioItem}，故 Curios
 * 取不到修饰符。此处按同类模式，依据物品 CUSTOM_DATA 的 {@code lensouls:stolen_entity}
 * 派生属性，与创造照片行为一致。修饰符绑定在类上，子照片进堆也不丢失。
 */
@Mixin(targets = "io.github.mortuusars.exposure.world.item.PhotographItem", remap = false)
public class PhotographCurioMixin implements ICurioItem {

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext,
                                                                                ResourceLocation slot, ItemStack stack) {
        String entityId = PhotographEffectRegistry.getStolenEntity(stack);
        if (entityId == null) return HashMultimap.create();
        return PhotoSpecialEffects.buildAttributeModifiers(entityId);
    }
}
