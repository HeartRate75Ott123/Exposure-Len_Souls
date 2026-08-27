package com.plumejade.lensouls.item;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.component.PotionFilterData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 为携带 {@link PotionFilterData} 组件的玻璃板追加 tooltip，显示注入的全部药水效果、等级与时长。
 */
@EventBusSubscriber(modid = LenSouls.MODID, value = Dist.CLIENT)
public class PotionGlassPaneTooltipHandler {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        PotionFilterData data = stack.get(ModDataComponents.POTION_FILTER_DATA);
        if (data == null || data.effects().isEmpty()) return;

        event.getToolTip().add(Component.translatable("tooltip.lensouls.potion_glass.effects"));
        for (PotionFilterData.Entry e : data.effects()) {
            ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, e.effect());
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(key);
            String nameKey = holder.isPresent() ? holder.get().value().getDescriptionId() : e.effect().toString();
            int level = e.amplifier() + 1;
            event.getToolTip().add(Component.literal("§a" + Component.translatable(nameKey).getString()
                    + " " + level + "级 (" + formatDuration(e.duration()) + ")"));
        }
        event.getToolTip().add(Component.literal("§7相机滤镜：拍照触发上述效果"));
    }

    private static String formatDuration(int ticks) {
        if (ticks <= 0) return "瞬间";
        int seconds = ticks / 20;
        int m = seconds / 60;
        int s = seconds % 60;
        if (m > 0) return m + ":" + (s < 10 ? "0" + s : s);
        return seconds + "s";
    }
}
