package com.plumejade.lensouls.effect;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.damage.ElementDamage;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 模组隐藏效果注册表。
 * <p>
 * 注册四种元素附魔效果：火、水、土、末影。
 * 所有效果均无纹理图标、不产生粒子、不在 HUD 显示。
 * <p>
 * 如需添加新元素：
 * <ol>
 *   <li>在 {@code ElementDamage} 中添加枚举常量</li>
 *   <li>在本类中用 {@link #register} 注册效果</li>
 *   <li>在 {@code ModItems} 中注册镜魂物品</li>
 *   <li>在 {@code entity_weakness/*.json} 中添加弱点数据</li>
 *   <li>{@link com.plumejade.lensouls.damage.DamageHandler} 自动多态适配，无需修改</li>
 * </ol>
 */
public class ModEffects {

    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, LenSouls.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> FIRE_INFUSION =
            register("fire_infusion", () -> new ElementInfusionEffect(ElementDamage.FIRE, 0xFF4500));

    public static final DeferredHolder<MobEffect, MobEffect> WATER_INFUSION =
            register("water_infusion", () -> new ElementInfusionEffect(ElementDamage.WATER, 0x1E90FF));

    public static final DeferredHolder<MobEffect, MobEffect> EARTH_INFUSION =
            register("earth_infusion", () -> new ElementInfusionEffect(ElementDamage.EARTH, 0x8B4513));

    public static final DeferredHolder<MobEffect, MobEffect> ENDER_INFUSION =
            register("ender_infusion", () -> new ElementInfusionEffect(ElementDamage.ENDER, 0x9933CC));

    private static DeferredHolder<MobEffect, MobEffect> register(String name, Supplier<MobEffect> effect) {
        return EFFECTS.register(name, effect);
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
