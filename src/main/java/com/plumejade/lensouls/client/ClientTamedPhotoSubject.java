package com.plumejade.lensouls.client;

/**
 * 客户端侧的「驯服生物是否纳入拍摄主体」镜像（<b>仅供显示</b>）。
 * <p>
 * 真正的开关在服务端 {@link com.plumejade.lensouls.util.TamedPhotoSubject} 里、由
 * {@code PhotoTargets.subject} / {@code WeaknessLensPhoto.subjectEntityId} 裁决；
 * 这里只缓存一份供状态提示文字使用。登录时由 {@code TamedPhotoSubjectStatePacket} 补发对齐，
 * 切换时先乐观翻转再由服务端回发纠正。
 * <p>
 * 初值 {@code true}：与服务端「键不存在 = 开启」的默认口径保持一致，
 * 免得刚进世界的头几帧提示与实际状态相反。
 */
public final class ClientTamedPhotoSubject {

    private static boolean enabled = true;

    private ClientTamedPhotoSubject() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** 本地翻转（仅用于按下的即时反馈），返回翻转后的值 */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }
}
