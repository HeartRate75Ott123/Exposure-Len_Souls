package com.plumejade.lensouls.client.fx;

/**
 * N公司2级员工「camera_shoot」命中玩家时的屏幕特效：<b>快门闪白 + 取景框 + 横向失焦</b>。
 * <p>
 * 只作用于被拍到的那位玩家的第一人称视角（服务端按 32 格 debuff 圈逐个发包）。
 * 银白色系，四个通道各自独立淡入淡出，<b>u = 1 时全部归零</b>，因此首尾与常规画面自然衔接，
 * 不会出现"啪一下盖上来 / 啪一下切回"的硬边：
 * <ol>
 *   <li><b>闪白 0~0.17</b>：快门那一瞬的白（银白而非纯白），起手 0.02 内冲上峰值再揭开；</li>
 *   <li><b>取景框 0.02~0.20 淡入 → 0.76~1.00 淡出</b>：四角臂 + 细画幅边框 + 中央圆环
 *       + 圆内十字 + 环外刻度，全部由 {@code screen_fx} 的 SDF 线绘制；</li>
 *   <li><b>横向失焦 0~0.18 抬起 → 0.62~0.97 收回</b>：只沿水平方向做多 tap 模糊
 *       （镜头散光/脱焦的观感），峰值约 2% 屏宽的横向扩散；</li>
 *   <li><b>开合 0.02~0.34</b>：圆环半径从 1.37 倍收紧到定格尺寸，"对上焦"的收束感。</li>
 * </ol>
 */
public final class PhotoShotScreenFx implements ScreenFxEffect {

    private static final float DURATION = 1.15F;

    private float t;

    @Override
    public boolean update(float dt) {
        t += dt;
        return t < DURATION;
    }

    @Override
    public void apply(ScreenFxManager.State s) {
        float u = Math.min(1.0F, t / DURATION);

        // ── 1. 快门闪白：0.02 内冲到峰值，0.17 内揭开（银白，不是纯白） ──
        float rise = smoothstep(0.0F, 0.02F, u);
        float fall = 1.0F - smoothstep(0.02F, 0.17F, u);
        float flash = 0.92F * rise * fall;
        if (flash > s.flash) {
            s.flashR = 0.93F;
            s.flashG = 0.96F;
            s.flashB = 1.00F;
            s.flash = flash;
        }

        // ── 2. 取景框：淡入 / 淡出都留足时间，收尾自然 ──
        float in = smoothstep(0.02F, 0.20F, u);
        float out = 1.0F - smoothstep(0.76F, 1.00F, u);
        s.viewfinder = Math.max(s.viewfinder, in * out);

        // ── 3. 圆环开合：出现时略大，随后收紧到定格（对焦感） ──
        s.viewfinderOpen = Math.max(s.viewfinderOpen, smoothstep(0.02F, 0.34F, u));

        // ── 4. 横向失焦：先聚后散（散焦在收尾前就放完，最后只剩取景框淡出） ──
        float blurIn = smoothstep(0.0F, 0.18F, u);
        float blurOut = 1.0F - smoothstep(0.62F, 0.97F, u);
        s.blurH = Math.max(s.blurH, 0.88F * blurIn * blurOut);
    }

    private static float smoothstep(float e0, float e1, float x) {
        float v = Math.max(0.0F, Math.min(1.0F, (x - e0) / (e1 - e0)));
        return v * v * (3.0F - 2.0F * v);
    }
}
