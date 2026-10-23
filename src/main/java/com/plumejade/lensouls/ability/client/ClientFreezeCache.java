package com.plumejade.lensouls.ability.client;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;

import java.util.UUID;

/**
 * 客户端全局时间定格状态缓存。
 * <p>
 * 由 {@link com.plumejade.lensouls.ability.network.FreezeSyncPacket} 更新，
 * 携带服务端实际定身实体 id 集（拍摄瞬间画面内生物，韧性目标 30% 判定后）
 * 以及<b>触发者 UUID</b>——冻结天穹等纯客户端表现只对触发者可见。
 * 控制冻结实体的描边/glint 渲染与渲染插值冻结。
 * <p>
 * 实体判定：定身集包含该实体（服务端已排除玩家）。
 * <p>
 * 支持测试模式：开启后本地玩家始终显示描边效果，用于调试。
 */
public class ClientFreezeCache {

    private static volatile boolean timeFrozen = false;

    private static final IntOpenHashSet frozenEntities = new IntOpenHashSet();

    /** 触发者 UUID（服务端下发；为 null 表示未知）。 */
    private static volatile UUID sourceUuid;

    private static volatile boolean testMode = false;

    /** 由 FreezeSyncPacket 更新：冻结总开关 + 定身实体集 + 触发者。 */
    public static void updateFreeze(boolean frozen, int[] ids, UUID source) {
        timeFrozen = frozen;
        sourceUuid = frozen ? source : null;
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

    /**
     * 本地玩家是否就是这次时停的触发者。
     * <p>
     * 冻结天穹这类"纯客户端表现"只对触发者可见（其他玩家只是世界被定住，
     * 天上不该出现别人的时钟）。
     */
    public static boolean isFreezeSource() {
        if (!timeFrozen) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (testMode) return true;
        UUID source = sourceUuid;
        return source != null && source.equals(mc.player.getUUID());
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
        sourceUuid = null;
        frozenEntities.clear();
    }
}
