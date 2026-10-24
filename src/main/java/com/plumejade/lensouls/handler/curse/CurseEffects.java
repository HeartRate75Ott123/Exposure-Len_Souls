package com.plumejade.lensouls.handler.curse;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 诅咒「常驻型」药水效果的统一下发口径。
 * <p>
 * <b>为什么需要它</b>：早期实现是每 tick 无条件 `player.addEffect(new MobEffectInstance(效果, 40, amp))`。
 * 上一 tick 的剩余时长已经掉到 39，这一 tick 又塞 40 ⇒ 原版 {@code MobEffectInstance.update} 判定
 * 「时长变长了」→ {@code LivingEntity.onEffectUpdated} → 对 {@code ServerPlayer} 就是**每 tick 一个
 * {@code ClientboundUpdateMobEffectPacket}**（每个效果、每个玩家、每秒 20 个）。诅咒常驻好几个效果时，
 * 这条无意义的重发会把客户端 HUD 每 tick 刷一遍，实机表现为**全局轻微卡顿 / 采挖时方块闪烁、掉落延迟**。
 * <p>
 * 现在的口径：**剩余时长还够（&gt; 阈值）且等级一致就什么都不做**，快过期时才补一次。
 * 效果语义不变（依然是常驻），只是不再每 tick 重发。
 */
public final class CurseEffects {

    /** 剩余时长低于该值才续期（20 tick = 1 秒；配合 60~80 tick 的时长，约每 2~3 秒补一次） */
    public static final int REFRESH_THRESHOLD = 20;

    private CurseEffects() {
    }

    /**
     * 续期一个常驻效果：等级一致且剩余时长 &gt; {@link #REFRESH_THRESHOLD} 时不动。
     *
     * @param duration 补一次给多长（建议 ≥ 3 × {@link #REFRESH_THRESHOLD}）
     */
    public static void keep(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier) {
        if (player == null || effect == null) return;
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() == amplifier
                && current.getDuration() > REFRESH_THRESHOLD) {
            return;
        }
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, false));
    }
}
