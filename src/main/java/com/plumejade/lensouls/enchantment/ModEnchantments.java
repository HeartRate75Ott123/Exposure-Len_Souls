package com.plumejade.lensouls.enchantment;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 模组附魔：摄魂术。
 * <p>
 * 1.21.1 起附魔由数据包 JSON 定义（{@code data/lensouls/enchantment/soul_photography.json}），
 * 其 {@code supported_items} 标签由数据包加载器正确绑定，可在铁砧/附魔台正常附到任意装备。
 * 此类仅持有资源键与持有方，供其他系统按附魔等级查询。
 */
public class ModEnchantments {

    public static final ResourceKey<Enchantment> SOUL_PHOTOGRAPHY_KEY =
            ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "soul_photography"));

    public static final DeferredHolder<Enchantment, Enchantment> SOUL_PHOTOGRAPHY =
            DeferredHolder.create(SOUL_PHOTOGRAPHY_KEY);

    public static int getSoulPhotographyLevel(net.minecraft.core.RegistryAccess registry, net.minecraft.world.item.ItemStack stack) {
        var registryOrThrow = registry.registryOrThrow(Registries.ENCHANTMENT);
        var holder = registryOrThrow.getHolderOrThrow(SOUL_PHOTOGRAPHY_KEY);
        return stack.getEnchantmentLevel(holder);
    }

    public static net.minecraft.core.Holder<Enchantment> getHolder(net.minecraft.core.RegistryAccess registry) {
        return registry.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(SOUL_PHOTOGRAPHY_KEY);
    }

    public static void register(IEventBus modEventBus) {
    }
}
