package com.plumejade.lensouls.integration;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Iris 光影激活检测（反射，无编译依赖）。
 * <p>
 * 仅检测"光影包已激活"（enableShaders 开启且已加载 shaderpack）；
 * Iris 安装但未激活时返回 false（原版/Sodium 渲染路径）。
 * 不缓存，支持游戏中切换光影。
 */
public final class IrisCompat {

    private IrisCompat() {
    }

    /** 当前是否处于 Iris 光影渲染（shaderpack 已激活）。 */
    public static boolean isShadersActive() {
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Method getCurrentPack = irisClass.getMethod("getCurrentPack");
            Object pack = getCurrentPack.invoke(null);
            return pack instanceof Optional<?> opt && opt.isPresent();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
