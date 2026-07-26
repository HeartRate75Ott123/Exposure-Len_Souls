package com.plumejade.lensouls.client.outline;

import com.plumejade.lensouls.LenSouls;
import net.neoforged.fml.ModList;

/**
 * Iris/Oculus 兼容检测工具。
 * <p>
 * 运行时检查光影模组加载状态和着色器包激活情况，
 * 供描边渲染系统在不同渲染管线间切换策略。
 * <p>
 * 参考 ItemGlint (Fabric) 和 AdorableArmory (Forge) 的兼容架构。
 */
public final class SoulOutlineCompat {

    private static final String OCULUS_MOD_ID = "oculus";
    private static final String IRIS_MOD_ID = "iris";

    /** Iris/Oculus API 类，用于检测是否已加载 */
    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    /** Iris/Oculus 手部渲染器类（有 shaderpack 激活时接管手部渲染） */
    private static final String IRIS_HAND_RENDERER_CLASS = "net.irisshaders.iris.pathways.HandRenderer";

    private static Boolean irisApiAvailable;
    private static Boolean handRendererAvailable;

    private SoulOutlineCompat() {}

    // ========================================================
    // 模组加载检测
    // ========================================================

    /**
     * @return Oculus (Forge) 或 Iris (Fabric) 模组是否已加载
     */
    public static boolean isIrisOrOculusLoaded() {
        ModList modList = ModList.get();
        if (modList == null) return false;
        return modList.isLoaded(OCULUS_MOD_ID) || modList.isLoaded(IRIS_MOD_ID);
    }

    /**
     * @return {@code net.irisshaders.iris.api.v0.IrisApi} 类是否存在
     */
    public static boolean isIrisApiAvailable() {
        if (irisApiAvailable != null) return irisApiAvailable;
        irisApiAvailable = checkClass(IRIS_API_CLASS);
        return irisApiAvailable;
    }

    /**
     * @return IrisHandRenderer 类是否存在（有 shaderpack 激活时使用）
     */
    public static boolean isHandRendererAvailable() {
        if (handRendererAvailable != null) return handRendererAvailable;
        handRendererAvailable = checkClass(IRIS_HAND_RENDERER_CLASS);
        return handRendererAvailable;
    }

    // ========================================================
    // 着色器包状态检测（反射）
    // ========================================================

    /**
     * @return 当前是否有 Iris/Oculus 着色器包激活
     */
    public static boolean isShaderPackActive() {
        if (!isIrisApiAvailable()) return false;
        try {
            // IrisApi.getInstance().isShaderPackInUse()
            Class<?> irisApiClass = Class.forName(IRIS_API_CLASS);
            Object instance = irisApiClass.getMethod("getInstance").invoke(null);
            return (boolean) irisApiClass.getMethod("isShaderPackInUse").invoke(instance);
        } catch (Exception e) {
            LenSouls.LOGGER.warn("[SoulOutlineCompat] 检测 Iris shaderpack 状态失败", e);
            return false;
        }
    }

    /**
     * @return 当前是否处于 Iris 阴影 pass 中
     */
    public static boolean isShadowPass() {
        if (!isIrisApiAvailable()) return false;
        try {
            Class<?> irisApiClass = Class.forName(IRIS_API_CLASS);
            Object instance = irisApiClass.getMethod("getInstance").invoke(null);
            return (boolean) irisApiClass.getMethod("isShadowPass").invoke(instance);
        } catch (Exception e) {
            return false;
        }
    }

    // ========================================================
    // 内部
    // ========================================================

    private static boolean checkClass(String className) {
        try {
            Class.forName(className, false, SoulOutlineCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
