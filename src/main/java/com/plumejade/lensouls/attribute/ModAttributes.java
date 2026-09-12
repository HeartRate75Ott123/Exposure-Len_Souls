package com.plumejade.lensouls.attribute;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 照片饰品自定义属性。
 * <p>
 * 每个元素一个"弱点"属性，值 = 佩戴照片提供的该元素弱点系数（加法），
 * 供 {@code DamageHandler} 在元素追伤结算时读取（受伤时只读属性，避免遍历照片查询）。
 * 通过 {@link EntityAttributeModificationEvent} 添加到玩家实体。
 */
public class ModAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, LenSouls.MODID);

    public static final DeferredHolder<Attribute, Attribute> FIRE_WEAKNESS =
            register("fire_weakness");
    public static final DeferredHolder<Attribute, Attribute> WATER_WEAKNESS =
            register("water_weakness");
    public static final DeferredHolder<Attribute, Attribute> EARTH_WEAKNESS =
            register("earth_weakness");
    public static final DeferredHolder<Attribute, Attribute> ENDER_WEAKNESS =
            register("ender_weakness");

    /** 闪避率：照片固有 + 套装效果均通过 ADD_VALUE 修饰符叠加，伤害处理时读取属性值掷骰 */
    public static final DeferredHolder<Attribute, Attribute> DODGE_CHANCE =
            register("dodge_chance", 0.0, 0.0, 1.0);

    /**
     * 命中率：玩家造成伤害的概率。默认 100%，上限 100%。
     * <p>
     * 基值为 1.0 且照片用 {@link net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation#ADD_MULTIPLIED_BASE}
     * 加减（如 -0.24 表示 -24%）：这样 Curios/原版属性行会原生渲染成百分比
     * （{@code 命中率 -24%}），无需自定义 tooltip 格式。
     * <p>
     * 语义上以「惩罚」为主、其余照片给正向加成用于抵扣：弹幕类照片 -24%，
     * 小白/唤魔者 -6%，其余按功能性弱强给 +7%~+14%（越弱给越多）。
     * 因上限为 100%，单独佩戴正向照片不会超过 100%，其价值体现在抵扣惩罚。
     */
    public static final DeferredHolder<Attribute, Attribute> HIT_CHANCE =
            register("hit_chance", 1.0, 0.0, 1.0);

    private static DeferredHolder<Attribute, Attribute> register(String name) {
        // 默认 1.0：弱点以 ADD_MULTIPLIED_BASE 百分比修饰叠加（0.12 → 显示 +12%），
        // 实际系数 = 属性值 - 1.0
        return ATTRIBUTES.register(name, () ->
                new RangedAttribute("attribute.name.lensouls." + name, 1.0, 0.0, 10.0));
    }

    private static DeferredHolder<Attribute, Attribute> register(String name, double base, double min, double max) {
        return ATTRIBUTES.register(name, () ->
                new RangedAttribute("attribute.name.lensouls." + name, base, min, max));
    }

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
        modEventBus.addListener(ModAttributes::onEntityAttributeModification);
    }

    /** 给玩家实体添加元素弱点属性 */
    private static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        for (DeferredHolder<Attribute, ? extends Attribute> holder : ATTRIBUTES.getEntries()) {
            event.add(EntityType.PLAYER, (Holder<Attribute>) (Holder<?>) holder);
        }
    }
}
