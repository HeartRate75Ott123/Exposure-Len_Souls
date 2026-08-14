package com.plumejade.lensouls.boss;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 BOSS 韧性缓存。
 * <p>
 * 由 S2C 包 {@link ToughnessSyncPacket} 更新，
 * 供 {@link ToughnessBarRenderer} 和定身闪烁渲染读取。
 */
public class BossToughnessClientCache {

    private static final List<ToughnessEntry> entries = new ArrayList<>();

    public static void update(List<ToughnessEntry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
    }

    public static List<ToughnessEntry> getEntries() {
        return entries;
    }

    /** 某实体是否处于破防定身状态 */
    public static boolean isStunned(int entityId) {
        return find(entityId) != null && find(entityId).broken();
    }

    /** 某实体是否处于削韧无敌窗口 */
    public static boolean isInvincible(int entityId) {
        return find(entityId) != null && find(entityId).invincible();
    }

    /** 查找实体的韧性条目（Jade 显示韧性值用） */
    public static ToughnessEntry find(int entityId) {
        for (ToughnessEntry e : entries) {
            if (e.entityId() == entityId) return e;
        }
        return null;
    }

    public static void clear() {
        entries.clear();
    }
}
