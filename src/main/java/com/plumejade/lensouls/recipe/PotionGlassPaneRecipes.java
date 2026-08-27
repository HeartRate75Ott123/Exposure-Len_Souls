package com.plumejade.lensouls.recipe;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 药水玻璃板配方序列化器注册。
 */
public class PotionGlassPaneRecipes {

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, LenSouls.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> POTION_GLASS_PANE =
            SERIALIZERS.register("potion_glass_pane",
                    () -> new SimpleCraftingRecipeSerializer<>(PotionGlassPaneRecipe::new));

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
