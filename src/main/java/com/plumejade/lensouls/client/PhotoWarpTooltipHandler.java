package com.plumejade.lensouls.client;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityBehavior;
import com.plumejade.lensouls.ability.util.DimensionNames;
import io.github.mortuusars.exposure.world.item.PhotographItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 空间扭曲照片的 tooltip：显示这张照片记录的拍摄坐标与维度。
 * <p>
 * 维度名走翻译键（见 {@link DimensionNames}），所以原版三维度、暮色森林、永恒星光都显示成中文，
 * 缺翻译的维度回退原始 id。
 */
@EventBusSubscriber(modid = LenSouls.MODID, value = Dist.CLIENT)
public final class PhotoWarpTooltipHandler {

    private PhotoWarpTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof PhotographItem)) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (!tag.contains(AbilityBehavior.TAG_WARP_POS)) return;

        CompoundTag pos = tag.getCompound(AbilityBehavior.TAG_WARP_POS);
        event.getToolTip().add(Component.translatable("tooltip.lensouls.spatial_warp.coords",
                        coord(pos.getDouble("x")), coord(pos.getDouble("y")), coord(pos.getDouble("z")))
                .withStyle(ChatFormatting.GRAY));

        String dim = tag.getString(AbilityBehavior.TAG_WARP_DIM);
        if (!dim.isEmpty()) {
            event.getToolTip().add(Component.translatable("tooltip.lensouls.spatial_warp.dimension",
                    DimensionNames.of(dim)).withStyle(ChatFormatting.GRAY));
        }
    }

    private static String coord(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
