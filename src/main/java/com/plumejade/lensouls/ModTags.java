package com.plumejade.lensouls;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 模组自定义标签定义。
 */
public class ModTags {

    /**
     * 可附魔摄魂术的相机物品标签。
     * 包含 {@code exposure:camera}、{@code exposure_polaroid:instant_camera} 等。
     */
    public static final TagKey<Item> ENCHANTABLE_CAMERAS =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "enchantable/cameras"));

    /**
     * 摄魂术可附魔的所有物品标签（剑 + 相机）。
     * 由 {@code data/lensouls/tags/item/enchantable/soul_photography.json} 定义。
     */
    public static final TagKey<Item> SOUL_PHOTOGRAPHY_ENCHANTABLE =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "enchantable/soul_photography"));

    /**
     * 所有玻璃板（无色 + 16 种染色），由 {@code data/lensouls/tags/item/glass_panes.json} 定义。
     * 用作药水玻璃板配方的输入与 JEI 代表性原料。
     */
    public static final TagKey<Item> GLASS_PANES =
            ItemTags.create(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "glass_panes"));
}
