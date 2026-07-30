package com.plumejade.lensouls.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ICondition;

import java.util.Map;

public record ConfigRecipeCondition(String config) implements ICondition {

    public static final MapCodec<ConfigRecipeCondition> CODEC = RecordCodecBuilder.mapCodec(inst ->
            inst.group(Codec.STRING.fieldOf("config").forGetter(ConfigRecipeCondition::config))
                    .apply(inst, ConfigRecipeCondition::new));

    private static final Map<String, java.util.function.Supplier<Boolean>> CONFIG_MAP = Map.of(
            "enableDimensionalGunRecipe", () -> Config.ENABLE_DIMENSIONAL_GUN_RECIPE.get(),
            "enableGravityGunRecipe", () -> Config.ENABLE_GRAVITY_GUN_RECIPE.get(),
            "enableConverterRecipe", () -> Config.ENABLE_CONVERTER_RECIPE.get()
    );

    @Override
    public boolean test(IContext context) {
        var supplier = CONFIG_MAP.get(config);
        return supplier != null && supplier.get();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "config");
}
