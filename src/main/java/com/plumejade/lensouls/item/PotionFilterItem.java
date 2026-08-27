package com.plumejade.lensouls.item;

import com.plumejade.lensouls.component.ModDataComponents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.registries.Registries;

import java.util.List;

/**
 * 药水滤镜：相机配件。由酿造台用玻璃板 + 药水/试剂合成，携带具体原版药水效果（含等级）。
 * 安装在相机滤镜槽后可重复使用，拍照时对自己或入镜生物施加该效果 30s。
 */
public class PotionFilterItem extends Item {

    public PotionFilterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.lensouls.filter.intro"));
        tooltip.add(Component.translatable("tooltip.lensouls.filter.activate"));
        var data = stack.get(ModDataComponents.POTION_FILTER_DATA);
        if (data != null) {
            ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, data.effect());
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(key);
            if (holder.isPresent()) {
                Component name = Component.translatable(holder.get().value().getDescriptionId());
                int level = data.amplifier() + 1;
                tooltip.add(Component.literal("§a" + name.getString() + " " + level + "级，持续30s"));
            } else {
                tooltip.add(Component.literal("§a未知药水效果"));
            }
        } else {
            tooltip.add(Component.literal("§a未注入药水效果"));
        }
    }
}
