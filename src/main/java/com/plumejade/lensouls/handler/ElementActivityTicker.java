package com.plumejade.lensouls.handler;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 元素活性结算的心跳：每 tick 让 {@link ElementActivityMath#push} 对齐一次。
 * <p>
 * 之所以要"每 tick"而不是"升一级推一次"：{@code /effect clear}、来源到期、羽毛/诅咒后加
 * 都不会通知我们，只有轮询才能在一个 tick 内收敛（push 幂等，状态对时零写入）。
 */
public final class ElementActivityTicker {

    private ElementActivityTicker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ElementActivityMath.push(player);
        }
    }
}
