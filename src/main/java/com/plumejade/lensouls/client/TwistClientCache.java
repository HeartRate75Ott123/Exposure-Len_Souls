package com.plumejade.lensouls.client;

/**
 * 客户端扭曲值缓存（由 {@link com.plumejade.lensouls.network.TwistSyncPacket} 更新，
 * 供 {@link SanBarOverlay} 渲染左侧填充条）。
 */
public final class TwistClientCache {

    private static volatile int twistValue;
    /** 左侧条是否该显示（服务端随包下发，客户端判断不了佩戴状态） */
    private static volatile boolean barVisible;

    private TwistClientCache() {
    }

    public static void set(int value, boolean visible) {
        twistValue = Math.max(0, Math.min(100, value));
        barVisible = visible;
    }

    /** 兼容旧调用：只更新数值，显示标志保持上一次的值 */
    public static void set(int value) {
        set(value, barVisible);
    }

    public static int get() {
        return twistValue;
    }

    public static boolean barVisible() {
        return barVisible;
    }
}
