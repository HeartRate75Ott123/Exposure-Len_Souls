package com.plumejade.lensouls.particle;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModParticleTypes {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, LenSouls.MODID);

    /** 次元枪命中火花（主世界→亮绿） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HIT_SPARK =
            PARTICLE_TYPES.register("hit_spark", () -> new SimpleParticleType(false));

    /** 次元枪命中火花（地狱→橙） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HIT_SPARK_ORANGE =
            PARTICLE_TYPES.register("hit_spark_orange", () -> new SimpleParticleType(false));

    /** 次元枪命中火花（末地→紫） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HIT_SPARK_PURPLE =
            PARTICLE_TYPES.register("hit_spark_purple", () -> new SimpleParticleType(false));

    /** 铁砧风格飞溅火花 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLYING_SPARK =
            PARTICLE_TYPES.register("flying_spark", () -> new SimpleParticleType(false));

    /** 草飘动粒子（弹道尾迹） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRASS_BLADE =
            PARTICLE_TYPES.register("grass_blade", () -> new SimpleParticleType(false));

    // ========== 韧性粒子 ==========

    /** 韧性削减：平行四边形散射 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TOUGHNESS_HIT =
            PARTICLE_TYPES.register("toughness_hit", () -> new SimpleParticleType(false));

    /** 破韧：十字架散射 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TOUGHNESS_BREAK =
            PARTICLE_TYPES.register("toughness_break", () -> new SimpleParticleType(false));

    /** 破韧附加：冲击波圆环（缩放+淡出） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TOUGHNESS_SHOCKWAVE =
            PARTICLE_TYPES.register("toughness_shockwave", () -> new SimpleParticleType(false));

    /** 时间定格拒绝：天青色平行四边形（同削韧粒子发射器，天青色着色） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FREEZE_REJECT =
            PARTICLE_TYPES.register("freeze_reject", () -> new SimpleParticleType(false));

    /** 元素灌注环境粒子 — 火焰（红橙） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELEMENT_PARTICLE_FIRE =
            PARTICLE_TYPES.register("element_particle_fire", () -> new SimpleParticleType(false));
    /** 元素灌注环境粒子 — 水流（蓝） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELEMENT_PARTICLE_WATER =
            PARTICLE_TYPES.register("element_particle_water", () -> new SimpleParticleType(false));
    /** 元素灌注环境粒子 — 大地（土褐） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELEMENT_PARTICLE_EARTH =
            PARTICLE_TYPES.register("element_particle_earth", () -> new SimpleParticleType(false));
    /** 元素灌注环境粒子 — 末影（紫红） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELEMENT_PARTICLE_ENDER =
            PARTICLE_TYPES.register("element_particle_ender", () -> new SimpleParticleType(false));

    /** 元素弱点螺旋粒子 — 火焰（红橙） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELEMENT_SPIRAL_FIRE =
            PARTICLE_TYPES.register("element_spiral_fire", () -> new SimpleParticleType(false));
    /** 元素弱点螺旋粒子 — 水流（青蓝） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELEMENT_SPIRAL_WATER =
            PARTICLE_TYPES.register("element_spiral_water", () -> new SimpleParticleType(false));
    /** 元素弱点螺旋粒子 — 大地（土褐） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELEMENT_SPIRAL_EARTH =
            PARTICLE_TYPES.register("element_spiral_earth", () -> new SimpleParticleType(false));
    /** 元素弱点螺旋粒子 — 末影（紫红） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELEMENT_SPIRAL_ENDER =
            PARTICLE_TYPES.register("element_spiral_ender", () -> new SimpleParticleType(false));

    /** 元素弱点螺旋粒子 — 弹射物（亮绿） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELEMENT_SPIRAL_PROJECTILE =
            PARTICLE_TYPES.register("element_spiral_projectile", () -> new SimpleParticleType(false));

    /** 元素弱点螺旋粒子 — 弱点透镜增伤（深青灰 #2d495c） */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELEMENT_SPIRAL_WEAKNESS =
            PARTICLE_TYPES.register("element_spiral_weakness", () -> new SimpleParticleType(false));

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
