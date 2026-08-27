package com.plumejade.lensouls.integration;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 为 exposure:expanded 的 16 个滤镜物品追加镜魂拍照效果说明。
 * 每个物品固定两行（配件安装方式 + 触发方式与 30s 冷却），第三行为具体效果。
 * 物品 id 形如 {@code exposure_expanded:<name>_filter}，spider 为拍敌人专用。
 */
@EventBusSubscriber(modid = LenSouls.MODID, value = Dist.CLIENT)
public class FilterTooltipHandler {

    private static final Map<ResourceLocation, String> EFFECT_KEYS = new HashMap<>();
    private static final Set<ResourceLocation> ENEMY = new HashSet<>();

    private static void put(String item, String effectKey) {
        EFFECT_KEYS.put(ResourceLocation.fromNamespaceAndPath("exposure_expanded", item), effectKey);
    }

    static {
        put("antialias_filter", "tooltip.lensouls.filter.antialias");
        put("art_filter", "tooltip.lensouls.filter.art");
        put("bits_filter", "tooltip.lensouls.filter.bits");
        put("blobs_filter", "tooltip.lensouls.filter.blobs");
        put("blur_filter", "tooltip.lensouls.filter.blur");
        put("bumpy_filter", "tooltip.lensouls.filter.bumpy");
        put("color_convolve_filter", "tooltip.lensouls.filter.color_convolve");
        put("deconverge_filter", "tooltip.lensouls.filter.deconverge");
        put("desaturate_filter", "tooltip.lensouls.filter.desaturate");
        put("flip_filter", "tooltip.lensouls.filter.flip");
        put("ntsc_filter", "tooltip.lensouls.filter.ntsc");
        put("pencil_filter", "tooltip.lensouls.filter.pencil");
        put("scan_pincushion_filter", "tooltip.lensouls.filter.scan_pincushion");
        put("sobel_filter", "tooltip.lensouls.filter.sobel");
        put("wobble_filter", "tooltip.lensouls.filter.wobble");
        put("spider_filter", "tooltip.lensouls.filter.spider");
        ENEMY.add(ResourceLocation.fromNamespaceAndPath("exposure_expanded", "spider_filter"));
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String effectKey = EFFECT_KEYS.get(id);
        if (effectKey == null) return;
        boolean enemy = ENEMY.contains(id);
        event.getToolTip().add(Component.translatable(enemy ? "tooltip.lensouls.filter.intro_enemy" : "tooltip.lensouls.filter.intro"));
        event.getToolTip().add(Component.translatable(enemy ? "tooltip.lensouls.filter.activate_enemy" : "tooltip.lensouls.filter.activate"));
        event.getToolTip().add(Component.translatable(effectKey));
    }
}
