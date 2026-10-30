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
 * <b>回血节奏</b>：模仿原版再生——按 tick 累积、满一秒回一跳；但相位锚在<b>实体</b>
 * （{@link ModAttachments#BOSS_HEAL_TIMER} 的在场 tick 累计器）而不是效果实例的
 * {@code duration}，所以：
 * <ul>
 *   <li><b>刷新 buff 不影响节奏</b>：{@code MobEffectInstance.update()} 只改 duration/amplifier，
 *       碰不到累计器 ⇒ 高频刷新（如整合包脚本每次挨打都重刷）仍是每秒一跳；</li>
 *   <li><b>获得后满 1 秒才第一跳</b>：不存在「获得即回一次」的免费回血
 *       （旧实现的 {@code duration % 20} 与新时长的 20 倍数对齐就会立刻回）。</li>
 * </ul>
 * 节流细节全部在 {@link BossHealTimer}，本类只负责「该回血了 → 按等级回多少」。
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

    /**
     * 每 tick 都进入 {@link #applyEffectTick}，由 {@link BossHealTimer} 的累计器自行节流。
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
        BossHealTimer timer = entity.getData(ModAttachments.BOSS_HEAL_TIMER);

        // 累计器判定「这一 tick 该不该回」：刷新不推进、真断档自动重算
        if (!timer.tickAndShouldHeal(entity.tickCount)) {
            return true;
        }

        int level = Mth.clamp(amplifier, 0, 254) + 1;   // 存储 0..254 → 原版 1..255
        float pct = level * 0.2f;                        // 每级 0.2% 最大生命
        float heal = entity.getMaxHealth() * pct / 100f;
        if (heal > 0f) {
            entity.heal(heal);
        }
        return true;
    }
}
