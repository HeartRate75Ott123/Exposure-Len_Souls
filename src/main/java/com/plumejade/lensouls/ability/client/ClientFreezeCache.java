package com.plumejade.lensouls.ability.client;

import net.minecraft.client.Minecraft;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 客户端冻结实体 ID 缓存。
 * <p>
 * 由 {@link com.plumejade.lensouls.ability.network.FreezeSyncPacket} 更新，
 * 控制冻结实体的描边渲染。
 * <p>
 * 支持测试模式：开启后本地玩家始终显示描边效果，用于调试。
 */
public class ClientFreezeCache {

    private static final Set<Integer> FROZEN_IDS = new HashSet<>();

    private static boolean testMode = false;

    public static boolean isFrozen(int entityId) {
        if (FROZEN_IDS.contains(entityId)) return true;
        if (testMode) {
            var mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getId() == entityId) return true;
        }
        return false;
    }

    public static boolean hasAnyFrozen() {
        return !FROZEN_IDS.isEmpty();
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

    public static void freezeAll(Collection<Integer> ids) {
        FROZEN_IDS.addAll(ids);
    }

    public static void unfreezeAll(Collection<Integer> ids) {
        FROZEN_IDS.removeAll(ids);
    }

    public static void clear() {
        FROZEN_IDS.clear();
    }

    /** 添加单个实体 ID（供 BOSS 镜魂描边临时借用） */
    public static void add(int entityId) {
        FROZEN_IDS.add(entityId);
    }

    /** 移除单个实体 ID */
    public static void remove(int entityId) {
        FROZEN_IDS.remove(entityId);
    }
}
