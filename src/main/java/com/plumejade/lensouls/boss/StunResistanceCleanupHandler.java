package com.plumejade.lensouls.boss;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 定格特性：生物被定身（破刹 / 时间定格）时，清除其身上的「抗性提升」buff。
 * <p>
 * 在伤害结算前（LivingDamageEvent.Pre）移除：凡被定格的目标，抗性提升即刻失效，
 * 伤害足额结算（与 KrakenStunPassiveMixin 在 hurt 时移除无敌效果的思路一致）。
 * 破刹/定身期间目标每 tick 由各 tick-cancel mixin 暂停，无法依赖 tick 自清理，
 * 故在受击时点兜底移除；配合 {@code BossStunTickMixin} 的进入定格即时移除双保险。
 */
public class StunResistanceCleanupHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (victim.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                && StunPauseHelper.isStunPaused(victim)) {
            victim.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }
    }
}
