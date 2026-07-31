package com.plumejade.lensouls.recipe;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 复制之魂配方序列化器注册。
 */
public class CopySoulRecipes {

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, LenSouls.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> COPY_SOUL =
            SERIALIZERS.register("copy_soul",
                    () -> new SimpleCraftingRecipeSerializer<>(CopySoulRecipe::new));

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
