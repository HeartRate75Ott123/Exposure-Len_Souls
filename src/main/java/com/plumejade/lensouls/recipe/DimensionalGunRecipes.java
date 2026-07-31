package com.plumejade.lensouls.recipe;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 次元枪升级/降级配方序列化器注册。
 */
public class DimensionalGunRecipes {

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, LenSouls.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> GUN_UPGRADE =
            SERIALIZERS.register("gun_upgrade",
                    () -> new SimpleCraftingRecipeSerializer<>(DimensionalGunUpgradeRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> GUN_DOWNGRADE =
            SERIALIZERS.register("gun_downgrade",
                    () -> new SimpleCraftingRecipeSerializer<>(DimensionalGunDowngradeRecipe::new));

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
