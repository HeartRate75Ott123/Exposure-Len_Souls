package com.plumejade.lensouls.effect;

import com.plumejade.lensouls.particle.ModParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 浓雾的治愈（boss_heal）：每秒回复 最大生命值 x%。
 * <p>
 * 等级取 {@code amplifier + 1}（存储 amplifier 允许 0~254，即原版等级 1~255，
 * 钳位防 255 溢出），每级回复 0.2% 最大生命 / 秒。
 * 图标见 {@code textures/mob_effect/boss_heal.png}，无可见转圈粒子（隐藏粒子）。
 * 已加入灾变 {@code cataclysm:tags/mob_effect/effective_for_bosses}，可对灾变 Boss 生效。
 */
public class BossHealEffect extends MobEffect {

    public BossHealEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    /** 空白粒子：复用隐藏粒子类型（返回 null 会导致效果包编码崩溃） */
    @Override
    public ParticleOptions createParticleOptions(MobEffectInstance instance) {
        return ModParticleTypes.FILTER_HIDDEN.get();
    }

    /** 每秒（每 20 tick）结算一次 */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration > 0 && duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        int level = Mth.clamp(amplifier, 0, 254) + 1;   // 存储 0..254 → 原版 1..255
        float pct = level * 0.2f;                        // 每级 0.2% 最大生命
        float heal = entity.getMaxHealth() * pct / 100f;
        if (heal > 0f) {
            entity.heal(heal);
        }
        return true;
    }
}
