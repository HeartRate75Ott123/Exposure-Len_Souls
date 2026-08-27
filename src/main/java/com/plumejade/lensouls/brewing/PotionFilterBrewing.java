package com.plumejade.lensouls.brewing;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.component.PotionFilterData;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.core.component.DataComponents;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 药水滤镜酿造：玻璃板（无色+16染色）作输入，药水物品（复制其效果与等级）或酿造试剂（对应效果固定 I 级）作配料，
 * 产出携带具体药水效果的 potion_filter 物品。
 */
@EventBusSubscriber(modid = LenSouls.MODID, bus = EventBusSubscriber.Bus.GAME)
public class PotionFilterBrewing {

    @SubscribeEvent
    public static void onBrew(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addRecipe(new PotionFilterRecipe());
    }

    public static class PotionFilterRecipe implements IBrewingRecipe {
        /** 17 种玻璃板：无色 + 16 染色 */
        private static final Set<Item> GLASS_PANES = new HashSet<>(List.of(
                Items.GLASS_PANE, Items.WHITE_STAINED_GLASS_PANE, Items.LIGHT_GRAY_STAINED_GLASS_PANE,
                Items.GRAY_STAINED_GLASS_PANE, Items.BLACK_STAINED_GLASS_PANE, Items.BROWN_STAINED_GLASS_PANE,
                Items.RED_STAINED_GLASS_PANE, Items.ORANGE_STAINED_GLASS_PANE, Items.YELLOW_STAINED_GLASS_PANE,
                Items.LIME_STAINED_GLASS_PANE, Items.GREEN_STAINED_GLASS_PANE, Items.CYAN_STAINED_GLASS_PANE,
                Items.LIGHT_BLUE_STAINED_GLASS_PANE, Items.BLUE_STAINED_GLASS_PANE, Items.PURPLE_STAINED_GLASS_PANE,
                Items.MAGENTA_STAINED_GLASS_PANE, Items.PINK_STAINED_GLASS_PANE));

        /** 酿造试剂 → 原版药水效果（固定 I 级）。覆盖全部可酿造正面+部分负面效果。 */
        private static final Map<Item, ResourceLocation> REAGENT_EFFECTS = new HashMap<>();
        static {
            REAGENT_EFFECTS.put(Items.SUGAR, ResourceLocation.parse("minecraft:speed"));
            REAGENT_EFFECTS.put(Items.RABBIT_FOOT, ResourceLocation.parse("minecraft:jump_boost"));
            REAGENT_EFFECTS.put(Items.BLAZE_POWDER, ResourceLocation.parse("minecraft:strength"));
            REAGENT_EFFECTS.put(Items.GOLDEN_CARROT, ResourceLocation.parse("minecraft:night_vision"));
            REAGENT_EFFECTS.put(Items.SPIDER_EYE, ResourceLocation.parse("minecraft:poison"));
            REAGENT_EFFECTS.put(Items.PUFFERFISH, ResourceLocation.parse("minecraft:water_breathing"));
            REAGENT_EFFECTS.put(Items.GHAST_TEAR, ResourceLocation.parse("minecraft:regeneration"));
            REAGENT_EFFECTS.put(Items.MAGMA_CREAM, ResourceLocation.parse("minecraft:fire_resistance"));
            REAGENT_EFFECTS.put(Items.GLISTERING_MELON_SLICE, ResourceLocation.parse("minecraft:instant_health"));
            REAGENT_EFFECTS.put(Items.FERMENTED_SPIDER_EYE, ResourceLocation.parse("minecraft:weakness"));
            REAGENT_EFFECTS.put(Items.PHANTOM_MEMBRANE, ResourceLocation.parse("minecraft:slow_falling"));
            REAGENT_EFFECTS.put(Items.TURTLE_SCUTE, ResourceLocation.parse("minecraft:resistance"));
        }

        @Override
        public boolean isInput(ItemStack input) {
            return GLASS_PANES.contains(input.getItem());
        }

        @Override
        public boolean isIngredient(ItemStack ingredient) {
            if (ingredient.getItem() instanceof PotionItem) return true;
            return REAGENT_EFFECTS.containsKey(ingredient.getItem());
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            ItemStack out = new ItemStack(ModItems.POTION_FILTER.get());
            // 路径 A：已酿好的药水（水/喷溅/滞留）→ 复制其首个效果与等级
            if (ingredient.getItem() instanceof PotionItem) {
                PotionContents pc = ingredient.get(DataComponents.POTION_CONTENTS);
                if (pc != null) {
                    List<MobEffectInstance> effects = new ArrayList<>();
                    pc.potion().ifPresent(p -> effects.addAll(p.value().getEffects()));
                    effects.addAll(pc.customEffects());
                    if (!effects.isEmpty()) {
                        MobEffectInstance inst = effects.get(0);
                        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value());
                        if (id != null) {
                            out.set(ModDataComponents.POTION_FILTER_DATA,
                                    new PotionFilterData(id, inst.getAmplifier()));
                            return out;
                        }
                    }
                }
                return ItemStack.EMPTY;
            }
            // 路径 B：酿造试剂 → 对应效果，固定 I 级
            ResourceLocation effId = REAGENT_EFFECTS.get(ingredient.getItem());
            if (effId != null) {
                out.set(ModDataComponents.POTION_FILTER_DATA, new PotionFilterData(effId, 0));
                return out;
            }
            return ItemStack.EMPTY;
        }
    }
}
