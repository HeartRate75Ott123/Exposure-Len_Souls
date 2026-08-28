package com.plumejade.lensouls.client;

import net.minecraft.client.Minecraft;

/**
 * 客户端缓存「祸之可能性」倒计时结束的游戏刻（gui tick）。
 * 由 {@link com.plumejade.lensouls.network.AbyssCountdownPacket} 触发。
 */
public class AbyssCountdownClient {
    private static int endTick = 0;

    public static void start() {
        endTick = Minecraft.getInstance().gui.getGuiTicks() + 60;
    }

    public static boolean isActive() {
        return Minecraft.getInstance().gui.getGuiTicks() < endTick;
    }

    public static int remainingSeconds() {
        return (int) Math.ceil((endTick - Minecraft.getInstance().gui.getGuiTicks()) / 20.0);
    }
}
