package com.plumejade.lensouls.ability.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 客户端全局时间定格状态缓存。
 * <p>
 * 由 {@link com.plumejade.lensouls.ability.network.FreezeSyncPacket} 更新，
 * 控制冻结实体的描边/glint 渲染与灰度画面。
 * <p>
 * 实体判定：全局冻结中且实体存在且非玩家 → 视为冻结（渲染路径天然视锥过滤，
 * 只有玩家看到的实体能走到判定点，无需扫描/距离计算）。
 * <p>
 * 支持测试模式：开启后本地玩家始终显示描边效果，用于调试。
 */
public class ClientFreezeCache {

    private static volatile boolean timeFrozen = false;

    private static volatile boolean testMode = false;

    /** 实体是否处于冻结（全局冻结 && 实体存在 && 非玩家）。 */
    public static boolean isFrozen(int entityId) {
        if (testMode) {
            var mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getId() == entityId) return true;
        }
        if (!timeFrozen) return false;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Entity entity = mc.level.getEntity(entityId);
        if (entity == null) return false;
        if (entity instanceof Player) return false;
        return true;
    }

    /** 是否处于全局冻结（合成闸门）。 */
    public static boolean hasAnyFrozen() {
        return timeFrozen;
    }

    public static boolean isTimeFrozen() {
        return timeFrozen;
    }

    public static void setTimeFrozen(boolean value) {
        timeFrozen = value;
    }

    public static boolean isTestMode() {
        return testMode;
    }

    public static void setTestMode(boolean value) {
        testMode = value;
    }

    public static void toggleTestMode() {
        testMode = !testMode;
    }

    public static void clear() {
        timeFrozen = false;
    }
}