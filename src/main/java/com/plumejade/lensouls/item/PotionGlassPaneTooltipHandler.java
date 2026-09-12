package com.plumejade.lensouls.item;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.component.PotionFilterData;
import com.plumejade.lensouls.recipe.PotionGlassPaneRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * 药水玻璃板相关 tooltip：
 * <ol>
 *   <li>携带 {@link PotionFilterData} 组件的玻璃板 → 显示已注入的全部药水效果、等级与时长；</li>
 *   <li>可注入的原料（已酿药水 / 酿造试剂）→ 提示可与玻璃板合成，并<b>列出会被注入的效果名、等级、时长</b>
 *       （需求：药材类 tooltip 增加一行对应能注入的 effect；效果名格式与玻璃板自身的注入列表一致）；</li>
 * </ol>
 */
@EventBusSubscriber(modid = LenSouls.MODID, value = Dist.CLIENT)
public class PotionGlassPaneTooltipHandler {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        PotionFilterData data = stack.get(ModDataComponents.POTION_FILTER_DATA);
        if (data != null && !data.effects().isEmpty()) {
            event.getToolTip().add(Component.translatable("tooltip.lensouls.potion_glass.effects"));
            for (PotionFilterData.Entry e : data.effects()) {
                event.getToolTip().add(Component.literal(effectLine(e)));
            }
            event.getToolTip().add(Component.translatable("tooltip.lensouls.potion_glass.input.camera"));
            return;
        }

        boolean potion = PotionGlassPaneRecipe.isPotion(stack);
        boolean reagent = PotionGlassPaneRecipe.isReagent(stack);
        if (potion) {
            event.getToolTip().add(Component.translatable("tooltip.lensouls.potion_glass.input.potion"));
        } else if (reagent) {
            event.getToolTip().add(Component.translatable("tooltip.lensouls.potion_glass.input.reagent"));
        } else {
            return;
        }

        // 会注入什么：效果名 / 等级 / 时长（与玻璃板已注入列表同一套格式）
        List<PotionFilterData.Entry> injectable = PotionGlassPaneRecipe.effectFrom(stack);
        if (!injectable.isEmpty()) {
            event.getToolTip().add(Component.translatable("tooltip.lensouls.potion_glass.injectable"));
            for (PotionFilterData.Entry e : injectable) {
                event.getToolTip().add(Component.literal(effectLine(e)));
            }
        }
        event.getToolTip().add(Component.translatable("tooltip.lensouls.potion_glass.input.camera"));
    }

    /** 单条效果行：§a效果名 N级 (时长) */
    private static String effectLine(PotionFilterData.Entry entry) {
        return "§a" + effectName(entry.effect()) + " " + (entry.amplifier() + 1) + "级 ("
                + formatDuration(entry.duration()) + ")";
    }

    private static String effectName(ResourceLocation effectId) {
        ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, effectId);
        var holder = BuiltInRegistries.MOB_EFFECT.getHolder(key);
        String nameKey = holder.isPresent() ? holder.get().value().getDescriptionId() : effectId.toString();
        return Component.translatable(nameKey).getString();
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
