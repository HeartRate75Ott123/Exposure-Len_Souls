package com.plumejade.lensouls.enchantment;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ModTags;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Optional;

/**
 * 模组附魔注册表。
 */
public class ModEnchantments {

    public static final ResourceKey<Enchantment> SOUL_PHOTOGRAPHY_KEY =
            ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "soul_photography"));

    private static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(Registries.ENCHANTMENT, LenSouls.MODID);

    public static final DeferredHolder<Enchantment, Enchantment> SOUL_PHOTOGRAPHY =
            ENCHANTMENTS.register("soul_photography", () -> {
                // 使用合并标签（剑 + 相机），由 data/lensouls/tags/item/enchantable/soul_photography.json 定义
                var supportedItems = BuiltInRegistries.ITEM.getOrCreateTag(ModTags.SOUL_PHOTOGRAPHY_ENCHANTABLE);

                var def = new Enchantment.EnchantmentDefinition(
                        supportedItems,
                        Optional.empty(),
                        5, 1,
                        new Enchantment.Cost(5, 8),
                        new Enchantment.Cost(25, 8),
                        4,
                        List.of(EquipmentSlotGroup.MAINHAND)
                );
                return new Enchantment(
                        Component.translatable("enchantment.lensouls.soul_photography"),
                        def,
                        HolderSet.empty(),
                        DataComponentMap.EMPTY
                );
            });

    public static int getSoulPhotographyLevel(net.minecraft.core.RegistryAccess registry, net.minecraft.world.item.ItemStack stack) {
        var registryOrThrow = registry.registryOrThrow(Registries.ENCHANTMENT);
        var holder = registryOrThrow.getHolderOrThrow(SOUL_PHOTOGRAPHY_KEY);
        return stack.getEnchantmentLevel(holder);
    }

    public static net.minecraft.core.Holder<Enchantment> getHolder(net.minecraft.core.RegistryAccess registry) {
        return registry.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(SOUL_PHOTOGRAPHY_KEY);
    }

    public static void register(IEventBus modEventBus) {
        ENCHANTMENTS.register(modEventBus);
    }
}
