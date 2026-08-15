package com.plumejade.lensouls.ability.client;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;

/**
 * 客户端全局时间定格状态缓存。
 * <p>
 * 由 {@link com.plumejade.lensouls.ability.network.FreezeSyncPacket} 更新，
 * 携带服务端实际定身实体 id 集（拍摄瞬间画面内生物，韧性目标 30% 判定后）。
 * 控制冻结实体的描边/glint 渲染与渲染插值冻结。
 * <p>
 * 实体判定：定身集包含该实体（服务端已排除玩家）。
 * <p>
 * 支持测试模式：开启后本地玩家始终显示描边效果，用于调试。
 */
public class ClientFreezeCache {

    private static volatile boolean timeFrozen = false;

    private static final IntOpenHashSet frozenEntities = new IntOpenHashSet();

    private static volatile boolean testMode = false;

    /** 由 FreezeSyncPacket 更新：冻结总开关 + 定身实体集。 */
    public static void updateFreeze(boolean frozen, int[] ids) {
        timeFrozen = frozen;
        frozenEntities.clear();
        if (frozen && ids != null) {
            for (int id : ids) {
                frozenEntities.add(id);
            }
        }
    }

    /** 实体是否处于冻结（定身集包含）。 */
    public static boolean isFrozen(int entityId) {
        if (testMode) {
            var mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getId() == entityId) return true;
        }
        return timeFrozen && frozenEntities.contains(entityId);
    }

    /** 是否处于全局冻结（合成闸门）。 */
    public static boolean hasAnyFrozen() {
        return timeFrozen;
    }

    public static boolean isTimeFrozen() {
        return timeFrozen;
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
        frozenEntities.clear();
    }
}
