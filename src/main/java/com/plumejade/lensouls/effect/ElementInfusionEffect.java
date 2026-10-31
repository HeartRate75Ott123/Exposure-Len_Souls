package com.plumejade.lensouls.effect;

import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.particle.ModParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 元素活性标记效果（照片饰品 / 照片套装提供的「元素活性」）。
 * <p>
 * 只作为「该实体当前带有此元素活性」的标记，由 {@link com.plumejade.lensouls.damage.DamageHandler}
 * 读取等级后折算追伤；不显示 HUD 图标（客户端注册为隐藏），只有环绕粒子。
 * <p>
 * <b>到期提示已移除（旧系统）</b>：镜魂右键早已改挂 {@link SoulDotEffect}（{@code *_dot} 药水效果），
 * 「XX镜魂 还有 N 秒结束」只应由 {@link SoulDotEffect} 按<b>实际镜魂来源</b>发出。
 * 本效果是照片饰品的常驻活性（套装约每 10 秒刷新一次 300 tick），对它发到期提示既只会误报元素名
 * （「火焰镜魂 还有 3 秒」），又会每 10 秒无意义地打断一次动作栏，因此整段提示逻辑已删除。
 * <p>
 * 效果等级（amplifier）仍按 {@link #getDamageMultiplier(int)} 的口径编码：
 * amplifier 0（I级）→ 基础倍率；amplifier N → (N+1).0x。
 */
public class ElementInfusionEffect extends MobEffect {

    private final ElementDamage element;

    /**
     * @param element 对应元素类型
     * @param color   粒子色值，设为 0 以完全隐藏
     */
    public ElementInfusionEffect(ElementDamage element, int color) {
        super(MobEffectCategory.BENEFICIAL, color);
        this.element = element;
    }

    /** 返回效果绑定的元素类型 */
    public ElementDamage getElement() {
        return element;
    }

    /**
     * 根据效果 amplifier 返回物品倍率系数（编码在 amplifier 中，利用原版高等级不被低等级覆盖的特性）。
     * <p>
     * amplifier 0 → ×1.0（基础镜魂）<br>
     * amplifier 1 → ×1.2（云筑魔像）<br>
     * amplifier 2 → ×1.5（堕落圣骑、末影守卫）<br>
     * amplifier 3 → ×2.0（焰魔、湮灭构造体、下界合金巨兽）
     */
    public float getDamageMultiplier(int amplifier) {
        return switch (amplifier) {
            case 0 -> 1.0f;
            case 1 -> 1.2f;
            case 2 -> 1.5f;
            case 3 -> 2.0f;
            default -> 1.0f + amplifier * 0.5f;
        };
    }

    /** 根据元素类型获取对应的环境粒子类型 */
    private net.minecraft.core.particles.SimpleParticleType getElementParticleType() {
        return switch (element) {
            case FIRE -> ModParticleTypes.ELEMENT_PARTICLE_FIRE.get();
            case WATER -> ModParticleTypes.ELEMENT_PARTICLE_WATER.get();
            case EARTH -> ModParticleTypes.ELEMENT_PARTICLE_EARTH.get();
            case ENDER -> ModParticleTypes.ELEMENT_PARTICLE_ENDER.get();
            case PROJECTILE -> ModParticleTypes.ELEMENT_PARTICLE_FIRE.get();
        };
    }

    @Override
    public net.minecraft.core.particles.ParticleOptions createParticleOptions(MobEffectInstance instance) {
        return getElementParticleType();
    }

    // 注意：这里**不要**再自己 addParticle。
    // 原版 LivingEntity.tickEffects 已经按 DATA_EFFECT_PARTICLES（＝本效果 createParticleOptions 的返回值）
    // 每 tick 以 1/4 概率在身体范围内撒一次，出生速度传的是哨兵值 (1,1,1)——由
    // client/particle/ElementParticle 照原版药水粒子的运动模型处理。旧版在这里额外补一发
    // 带 (0.02, 0.02, 0.02) 初速的粒子，既与上面那条重复，又是"粒子乱飞"的第二来源，已删。
}
