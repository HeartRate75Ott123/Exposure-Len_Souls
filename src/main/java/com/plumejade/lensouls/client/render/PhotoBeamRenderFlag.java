package com.plumejade.lensouls.client.render;

/**
 * 「当前正在渲染的这道光束是不是我们的照片弹幕」——给光束渲染 mixin 的内部开关。
 * <p>
 * 为什么需要它：光束渲染器把 {@code render} 和 {@code renderBeam} 拆成两个方法，
 * 而实体只在 {@code render} 的签名里。{@code @ModifyArg}（改滚转角）挂在 {@code renderBeam} 上，
 * 拿不到实体，只能靠这个「渲染前一刻刷新的」静态开关判断。
 * <p>
 * 渲染是渲染线程单线程串行执行的，而且每次 {@code render} 都会在调用 {@code renderBeam} 之前
 * 重新写入（不是自己的光束也会写 false），所以一个普通静态 boolean 就够，不需要 ThreadLocal。
 * 纯客户端类：只在 {@code lensouls.compat.mixins.json} 的 <b>client</b> 数组里的 mixin 引用它。
 */
public final class PhotoBeamRenderFlag {

    private static boolean ownBeam;

    private PhotoBeamRenderFlag() {
    }

    public static void set(boolean own) {
        ownBeam = own;
    }

    public static boolean get() {
        return ownBeam;
    }
}
