package com.plumejade.lensouls.client.fx;

/**
 * 一条屏幕后处理特效。
 * <p>
 * 生命周期由 {@link ScreenFxManager} 驱动：每帧 {@link #update(float)} 推进，
 * 返回 {@code false} 即被移除；活着时把自身参数写进 {@link ScreenFxManager.State}。
 */
public interface ScreenFxEffect {

    /** @param dt 秒；返回 false = 结束 */
    boolean update(float dt);

    /** 把本帧参数叠加到共享状态 */
    void apply(ScreenFxManager.State state);
}
