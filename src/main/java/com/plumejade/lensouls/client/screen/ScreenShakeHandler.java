package com.plumejade.lensouls.client.screen;

/**
 * 客户端屏幕震屏工具。
 * <p>
 * 各 BOSS 爆发阶段调用 {@link #shake(float, int)} 触发。
 * 后续可通过 GameRenderer mixin（RenderLevelStageEvent）应用实际相机偏移。
 */
public class ScreenShakeHandler {

    private static float currentIntensity = 0f;
    private static int remainingTicks = 0;

    /**
     * 触发屏幕震动。
     *
     * @param intensity    像素偏移强度（1~10）
     * @param durationTicks 持续 tick 数
     */
    public static void shake(float intensity, int durationTicks) {
        // 取最大值：新震动强度高或剩余时间长则覆盖
        if (intensity > currentIntensity || durationTicks > remainingTicks) {
            currentIntensity = intensity;
            remainingTicks = durationTicks;
        }
    }

    /** 每 tick 衰减（由客户端 TickEvent 驱动） */
    public static void tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
            if (remainingTicks <= 0) {
                currentIntensity = 0f;
            }
        }
    }

    /** 当前是否在震动中 */
    public static boolean isShaking() {
        return remainingTicks > 0 && currentIntensity > 0f;
    }

    /** 获取当前震动强度 */
    public static float getIntensity() { return currentIntensity; }

    /** 获取剩余 tick */
    public static int getRemainingTicks() { return remainingTicks; }

    /** 获取当前帧的随机像素偏移量（X） */
    public static float getOffsetX() {
        if (!isShaking()) return 0f;
        return (float) (Math.random() - 0.5) * 2f * currentIntensity;
    }

    /** 获取当前帧的随机像素偏移量（Y） */
    public static float getOffsetY() {
        if (!isShaking()) return 0f;
        return (float) (Math.random() - 0.5) * 2f * currentIntensity;
    }

    /** 立即停止震动 */
    public static void reset() {
        currentIntensity = 0f;
        remainingTicks = 0;
    }
}
