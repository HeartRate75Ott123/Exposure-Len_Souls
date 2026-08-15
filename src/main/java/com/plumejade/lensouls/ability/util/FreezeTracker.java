package com.plumejade.lensouls.ability.util;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.network.FreezeSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * 时间定格追踪器（服务端单例）。
 * <p>
 * 时间定格 = 原版全局 freeze（{@code ServerTickRateManager.setFrozen}）：
 * 开始/结束各调一次（每次调用都会广播 {@code ClientboundTickingStatePacket}
 * 同步客户端，绝不每 tick 执行）；静默运行，不产生聊天消息。
 * 玩家不受全局冻结影响（原版机制保证）。
 */
public class FreezeTracker {

    private static final FreezeTracker INSTANCE = new FreezeTracker();

    private MinecraftServer server;
    private UUID sourcePlayerId;
    private int remainingTicks;

    public static FreezeTracker getInstance() { return INSTANCE; }

    /** 触发全局时间定格 */
    public void freeze(Player source, int durationTicks) {
        MinecraftServer srv = source.getServer();
        this.server = srv;
        this.sourcePlayerId = source.getUUID();
        this.remainingTicks = durationTicks;
        srv.tickRateManager().setFrozen(true);
        PacketDistributor.sendToAllPlayers(new FreezeSyncPacket(true));
    }

    /** 每 tick 递减，到期解除全局冻结 */
    public void tick() {
        if (server == null) return;
        remainingTicks--;
        if (remainingTicks <= 0) {
            LenSouls.LOGGER.debug("[FreezeTracker] 时间定格到期，解除全局冻结");
            unfreeze();
        }
    }

    /** 当前是否处于全局冻结 */
    public boolean isFrozen() {
        return server != null && server.tickRateManager().isFrozen();
    }

    /** 该玩家是否是当前时间定格的来源（重触发拒绝/登出清理用） */
    public boolean isPlayerSource(Player player) {
        return isFrozen() && sourcePlayerId != null && sourcePlayerId.equals(player.getUUID());
    }

    /** 玩家退出时清理（仅当其为冻结来源时解除全局冻结） */
    public void unfreezePlayerSource(Player source) {
        if (isPlayerSource(source)) {
            unfreeze();
        }
    }

    private void unfreeze() {
        if (server == null) return;
        server.tickRateManager().setFrozen(false);
        PacketDistributor.sendToAllPlayers(new FreezeSyncPacket(false));
        server = null;
        sourcePlayerId = null;
        remainingTicks = 0;
    }
}