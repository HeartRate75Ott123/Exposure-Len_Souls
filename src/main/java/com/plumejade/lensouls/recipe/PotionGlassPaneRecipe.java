package com.plumejade.lensouls.recipe;

import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.component.PotionFilterData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 药水玻璃板配方（工作台，无序、特殊配方，无需 JSON）。
 * 1 个玻璃板（无色或 16 种染色）+ 1 个已酿药水或酿造试剂 → 同一个玻璃板，并带上 {@link PotionFilterData} 组件，
 * 记录所携带的原版药水效果（含等级）。玻璃板与药水/试剂均消耗。
 * <p>
 * 玻璃板本身已是 Exposure 的相机滤镜（染色玻璃板带对应色调 shader），装上相机后拍照触发该药水效果。
 */
public class PotionGlassPaneRecipe extends CustomRecipe {

    private static final Set<Item> GLASS_PANES = new HashSet<>(List.of(
            Items.GLASS_PANE,
            Items.WHITE_STAINED_GLASS_PANE, Items.ORANGE_STAINED_GLASS_PANE, Items.MAGENTA_STAINED_GLASS_PANE,
            Items.LIGHT_BLUE_STAINED_GLASS_PANE, Items.YELLOW_STAINED_GLASS_PANE, Items.LIME_STAINED_GLASS_PANE,
            Items.PINK_STAINED_GLASS_PANE, Items.GRAY_STAINED_GLASS_PANE, Items.LIGHT_GRAY_STAINED_GLASS_PANE,
            Items.CYAN_STAINED_GLASS_PANE, Items.PURPLE_STAINED_GLASS_PANE, Items.BLUE_STAINED_GLASS_PANE,
            Items.BROWN_STAINED_GLASS_PANE, Items.GREEN_STAINED_GLASS_PANE, Items.RED_STAINED_GLASS_PANE,
            Items.BLACK_STAINED_GLASS_PANE));

    /** 酿造试剂 → 对应原版效果（固定 I 级） */
    private static final Map<Item, ResourceLocation> REAGENT_EFFECTS = new HashMap<>();
    static {
        REAGENT_EFFECTS.put(Items.SUGAR, rl("speed"));
        REAGENT_EFFECTS.put(Items.RABBIT_FOOT, rl("jump_boost"));
        REAGENT_EFFECTS.put(Items.BLAZE_POWDER, rl("strength"));
        REAGENT_EFFECTS.put(Items.GOLDEN_CARROT, rl("night_vision"));
        REAGENT_EFFECTS.put(Items.SPIDER_EYE, rl("poison"));
        REAGENT_EFFECTS.put(Items.PUFFERFISH, rl("water_breathing"));
        REAGENT_EFFECTS.put(Items.GHAST_TEAR, rl("regeneration"));
        REAGENT_EFFECTS.put(Items.MAGMA_CREAM, rl("fire_resistance"));
        REAGENT_EFFECTS.put(Items.GLISTERING_MELON_SLICE, rl("instant_health"));
        REAGENT_EFFECTS.put(Items.FERMENTED_SPIDER_EYE, rl("weakness"));
        REAGENT_EFFECTS.put(Items.PHANTOM_MEMBRANE, rl("slow_falling"));
        REAGENT_EFFECTS.put(Items.TURTLE_SCUTE, rl("resistance"));
    }

    private static ResourceLocation rl(String effect) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", effect);
    }

    public PotionGlassPaneRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int panes = 0;
        int others = 0;
        ItemStack other = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (GLASS_PANES.contains(stack.getItem())) {
                panes++;
            } else {
                others++;
                other = stack;
            }
        }
        if (panes != 1 || others != 1 || other.isEmpty()) return false;
        if (other.getItem() instanceof PotionItem) return true;
        return REAGENT_EFFECTS.containsKey(other.getItem());
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack pane = ItemStack.EMPTY;
        ItemStack other = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (GLASS_PANES.contains(stack.getItem())) pane = stack;
            else other = stack;
        }
        if (pane.isEmpty() || other.isEmpty()) return ItemStack.EMPTY;

        PotionFilterData data = effectFrom(other);
        if (data == null) return ItemStack.EMPTY;

        ItemStack out = pane.copy();
        out.setCount(1);
        out.set(ModDataComponents.POTION_FILTER_DATA, data);
        return out;
    }

    private static PotionFilterData effectFrom(ItemStack other) {
        // 路径 A：已酿好的药水（水/喷溅/滞留）→ 复制其首个效果与等级
        if (other.getItem() instanceof PotionItem) {
            PotionContents pc = other.get(DataComponents.POTION_CONTENTS);
            if (pc != null) {
                List<MobEffectInstance> effects = new ArrayList<>();
                pc.potion().ifPresent(p -> effects.addAll(p.value().getEffects()));
                effects.addAll(pc.customEffects());
                if (!effects.isEmpty()) {
                    MobEffectInstance inst = effects.get(0);
                    ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value());
                    if (id != null) {
                        return new PotionFilterData(id, inst.getAmplifier());
                    }
                }
            }
            return null;
        }
        // 路径 B：酿造试剂 → 对应效果，固定 I 级
        ResourceLocation effId = REAGENT_EFFECTS.get(other.getItem());
        if (effId != null) {
            return new PotionFilterData(effId, 0);
        }
        return null;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return PotionGlassPaneRecipes.POTION_GLASS_PANE.get();
    }
}
