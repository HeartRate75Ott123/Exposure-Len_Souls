package com.plumejade.lensouls.client;

/**
 * 客户端扭曲值缓存（由 {@link com.plumejade.lensouls.network.TwistSyncPacket} 更新，
 * 供 {@link SanBarOverlay} 渲染左侧填充条）。
 */
public final class TwistClientCache {

    private static volatile int twistValue;

    private TwistClientCache() {
    }

    public static void set(int value) {
        twistValue = Math.max(0, Math.min(100, value));
    }

    public static int get() {
        return twistValue;
    }
}
