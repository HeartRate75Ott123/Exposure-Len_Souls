package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.boss.BossHealTimer;
import com.plumejade.lensouls.boss.ModAttachments;
import com.plumejade.lensouls.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * 浓雾的治愈（boss_heal）回血时钟的移除清理。
 * <p>
 * {@link com.plumejade.lensouls.effect.BossHealEffect} 用实体附件
 * {@link ModAttachments#BOSS_HEAL_TIMER} 里的绝对 tick 时间戳节流，
 * 与效果实例的 duration 解耦——刷新 buff 不动时间戳（这是本次改动的目的）。
 * 但时间戳是实体级的，效果被移除后仍会残留；若残留值已过期，
 * 「移除后立刻重新获得」会在下一 tick 直接白送一次回血（正是要杜绝的暴血路径）。
 * <p>
 * 因此在此清空时钟，使「获得 → 满 1 秒回第一次」在任何获得路径下都成立：
 * <ul>
 *   <li>{@link MobEffectEvent.Expired}：自然到时（服务端触发）；</li>
 *   <li>{@link MobEffectEvent.Remove}：牛奶 / {@code /effect clear} / 指令移除（双端触发）。</li>
 * </ul>
 * 只用 {@code getExistingDataOrNull}，不为无关实体创建附件。
 */
public class BossHealEffectHandler {

    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        clearTimer(event.getEntity(), event.getEffectInstance());
    }

    @SubscribeEvent
    public static void onRemoved(MobEffectEvent.Remove event) {
        clearTimer(event.getEntity(), event.getEffectInstance());
    }

    private static void clearTimer(LivingEntity entity, MobEffectInstance instance) {
        if (instance == null || !instance.is(ModEffects.BOSS_HEAL)) {
            return;
        }
        BossHealTimer timer = entity.getExistingDataOrNull(ModAttachments.BOSS_HEAL_TIMER);
        if (timer != null) {
            timer.reset();
        }
    }
}
