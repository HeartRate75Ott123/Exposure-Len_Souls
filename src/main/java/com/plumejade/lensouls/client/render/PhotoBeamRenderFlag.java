package com.plumejade.lensouls.client.render;

/**
 * 「当前正在渲染的这道光束」的瞬时状态——给光束渲染 mixin 用的内部通道。
 * <p>
 * 为什么需要它：光束渲染器把 {@code render} / {@code renderBeam} / {@code renderEnd} 拆成三个方法，
 * 而实体只在 {@code render} 的签名里。挂在 {@code renderBeam}（改滚转角）、{@code renderEnd}（改爆点帧）
 * 上的 {@code @ModifyArg} 拿不到实体，只能靠这个「渲染前一刻刷新的」静态状态判断与取值。
 * <p>
 * 渲染是渲染线程单线程串行执行的，而且每次 {@code render} 都会在调用 {@code renderBeam} 之前重新写入
 * （不是自己的光束也会写 false），所以普通静态字段就够，不需要 ThreadLocal。
 * 纯客户端类：只在 {@code lensouls.compat.mixins.json} 的 <b>client</b> 数组里的 mixin 引用它。
 */
public final class PhotoBeamRenderFlag {

    /**
     * 爆点（收尾特效）的可用帧数：贴图上半区每帧 16×16，{@code u = 0.0625 × frame}；
     * <b>只有 frame 0~5 是火花</b>（不透明像素数 12/32/52/88/132/164，由小到大），
     * <b>frame 6 起是空白</b>——原作 {@code if (frame < 0) frame = 6;} 正是拿空白帧当「隐身」。
     * 湮灭（{@code the_warped_one/annihilation_beam.png}）与先驱者（{@code harbinger/death_laser_beam.png}）
     * 两张图这一点逐像素一致。
     */
    public static final int IMPACT_FRAMES = 6;

    /** 爆点往返一轮的 tick 数（一涨一缩共 0.6 秒）。 */
    public static final int IMPACT_PERIOD_TICKS = 12;

    private static boolean ownBeam;
    private static int endFrame;

    private PhotoBeamRenderFlag() {
    }

    public static void set(boolean own) {
        ownBeam = own;
    }

    public static boolean get() {
        return ownBeam;
    }

    /** 本帧这道光束该用的爆点帧号（仅 {@link #get()} 为真时有意义）。 */
    public static void setEndFrame(int frame) {
        endFrame = frame;
    }

    public static int endFrame() {
        return endFrame;
    }

    /**
     * 爆点动画帧号：在自己的生命里做 <b>0→5→0 三角波往返</b>（周期 {@link #IMPACT_PERIOD_TICKS} tick）。
     * <p>
     * 原作口径的问题：爆点与光束体共用 {@code appear} 这个 {@code ControlledAnim}（时长 3），
     * {@code increaseTimer()} 涨到时长即<b>饱和</b>不再循环 ⇒ 爆点只在前 3 tick 变一次，
     * 之后整条命都是同一帧（唯一还会动的是结束时 {@code decreaseTimer()} 的倒放）。
     * 这里按 {@code tickCount} 自己算帧，让爆点整条命都在动；{@code partialTick} 让跨帧切换更顺。
     */
    public static int impactCapFrame(int tickCount, float partialTick) {
        int period = IMPACT_PERIOD_TICKS;
        float phase = Math.floorMod(tickCount, period) + partialTick;
        float half = period / 2.0F;
        float wave = phase <= half ? phase / half : (period - phase) / half;   // 0 → 1 → 0
        int frame = Math.round(wave * (IMPACT_FRAMES - 1));
        if (frame < 0) {
            return 0;
        }
        return Math.min(frame, IMPACT_FRAMES - 1);
    }
}
