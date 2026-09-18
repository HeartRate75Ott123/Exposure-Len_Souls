package com.plumejade.lensouls.recipe;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 强化配方序列化器注册。
 */
public class ReinforceRecipes {

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, LenSouls.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> REINFORCE =
            SERIALIZERS.register("reinforce",
                    () -> new SimpleCraftingRecipeSerializer<>(ReinforceRecipe::new));

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
