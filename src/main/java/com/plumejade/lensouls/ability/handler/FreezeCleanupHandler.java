package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.util.FreezeTracker;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 时间定格断线清理。
 * <p>
 * 玩家退出时立即 unfreeze 该 source 的所有目标。
 */
public class FreezeCleanupHandler {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            FreezeTracker.getInstance().unfreezePlayerSource(sp);
        }
    }
}
