package com.plumejade.lensouls.component;

import com.mojang.serialization.Codec;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

/**
 * 模组自定义 DataComponent 注册表。
 */
public class ModDataComponents {

    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LenSouls.MODID);

    /** 镜魂冷却数据：冷却结束刻 + 总刻数 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SoulCooldownData>> SOUL_COOLDOWN =
            register("soul_cooldown", builder -> builder
                    .persistent(SoulCooldownData.CODEC)
                    .networkSynchronized(SoulCooldownData.STREAM_CODEC));

    /** 次元枪弹药数据 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GunAmmoData>> GUN_AMMO =
            register("gun_ammo", builder -> builder
                    .persistent(GunAmmoData.CODEC)
                    .networkSynchronized(GunAmmoData.STREAM_CODEC));

    /** 次元枪击杀数据 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GunKillData>> GUN_KILLS =
            register("gun_kills", builder -> builder
                    .persistent(GunKillData.CODEC)
                    .networkSynchronized(GunKillData.STREAM_CODEC));

    /** 药水滤镜数据：携带的原版药水效果与等级 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PotionFilterData>> POTION_FILTER_DATA =
            register("potion_filter_data", builder -> builder
                    .persistent(PotionFilterData.CODEC)
                    .networkSynchronized(PotionFilterData.STREAM_CODEC));

    /**
     * 复制之魂封印标记：佩戴禁复制羽毛（元素觉醒/铁人/深渊）的玩家携带时自动置位，
     * 复制配方见到即拒绝匹配——判定在配方层，任何合成台（含模组/自动化）统一拦截。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> COPY_SOUL_SEALED =
            register("copy_soul_sealed", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        return COMPONENTS.register(name, () -> builder.apply(DataComponentType.builder()).build());
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
