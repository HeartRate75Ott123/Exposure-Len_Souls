package com.plumejade.lensouls.effect;

import com.plumejade.lensouls.boss.BossHealTimer;
import com.plumejade.lensouls.boss.ModAttachments;
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
 * <p>
 * <b>回血节奏</b>（模仿原版生命恢复：刷新 buff 不重置计时，只在获得时起算）：
 * 不能用 {@code duration % 20} 判定——buff 每次被重新施加都会重设 duration，
 * 那样「高频获得 buff = 高频回血」，与「每秒固定回复量」的设计目标不符。
 * 改为读取实体附件 {@link ModAttachments#BOSS_HEAL_TIMER} 的绝对 tick 时间戳：
 * 获得 buff 后首次生效只写入 {@code now + 20}（满 1 秒才回第一次血），
 * 此后每次回血把时间戳推后 20 tick；刷新 buff 不触碰时间戳，故回血速度恒定为每秒一次。
 */
public class BossHealEffect extends MobEffect {

    /** 回血间隔：每秒一次（20 tick） */
    private static final int HEAL_INTERVAL_TICKS = 20;

    public BossHealEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    /** 空白粒子：复用隐藏粒子类型（返回 null 会导致效果包编码崩溃） */
    @Override
    public ParticleOptions createParticleOptions(MobEffectInstance instance) {
        return ModParticleTypes.FILTER_HIDDEN.get();
    }

    /**
     * 每 tick 都进入 {@link #applyEffectTick}，由附件时间戳自行节流。
     * <p>
     * 不能返回 {@code duration % 20 == 0}：duration 会被 buff 刷新重设，
     * 新时长的每个 20 的整数倍都会误触发一次回血。
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        long now = entity.level().getGameTime();
        BossHealTimer timer = entity.getData(ModAttachments.BOSS_HEAL_TIMER);

        // 时间戳仍在未来 → 未到回血时刻（首次生效时只起算，不回血）
        if (timer.nextHealTick > now) {
            return true;
        }
        timer.nextHealTick = now + HEAL_INTERVAL_TICKS;

        int level = Mth.clamp(amplifier, 0, 254) + 1;   // 存储 0..254 → 原版 1..255
        float pct = level * 0.2f;                        // 每级 0.2% 最大生命
        float heal = entity.getMaxHealth() * pct / 100f;
        if (heal > 0f) {
            entity.heal(heal);
        }
        return true;
    }
}
