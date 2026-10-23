package com.plumejade.lensouls.client.fx;

/**
 * 削韧命中反馈（只作用于**造成韧性伤害的那位玩家**）。两档，刻意分开：
 * <ul>
 *   <li><b>普通削韧</b>：<b>只有屏幕抖动</b>（{@link CameraShake} 抖相机角度，0.26s），
 *       没有任何全屏后处理 —— 一下一下削韧时不至于糊屏；</li>
 *   <li><b>打出定身的那一次</b>：抖动更狠更久（0.55s），再加一记<b>"冲击"</b>全屏表现。</li>
 * </ul>
 *
 * <h3>定身那一下的冲击（为什么不用镜面碎裂）</h3>
 * 镜面碎做过一版，实机观感不好（碎片乱飞、像画面故障而不是力道），已整体移除。
 * 现在换成只靠现有后处理通道组合出来的"硬冲击"，各通道分别入场/收场、u = 1 全部归零：
 * <ol>
 *   <li><b>爆闪</b> 0~0.10s：银白 0.62，一瞬间把画面打白（"砸下来"的那一帧）；</li>
 *   <li><b>拉爆</b> 0~0.98s 缓收：径向拉伸 0.95 —— 画面被朝中心猛拽 + 径向拖尾，
 *       这是"力道"最主要的来源；</li>
 *   <li><b>径向速度线</b> 0~0.42s：{@code FxStreak} 1.35，从画面外圈往中心扫的放射状亮线；</li>
 *   <li><b>冲击环</b> 0~0.55s：从屏幕中心炸开一圈银色亮环（半径 ease-out 扩到 1.6）；</li>
 *   <li><b>边缘压暗</b> 0~0.98s 缓收：冷蓝暗角，让"被打了一下"的压迫感留在视野边缘。</li>
 *   <li><b>RGB 脉冲</b> 0~0.6s：跟着 {@link #BEAT_HZ} 的节拍跳色 —— 色散振幅与三通道各自的
 *       呼吸（{@code FxRgb}）都按节拍起伏，中心几乎不动、边缘最明显；冲击环同时带一半棱镜色。</li>
 * </ol>
 * 色散叠在拉爆上，0.42s 收完，把"被顶穿"的撕裂感补上。
 */
public final class ToughnessImpactScreenFx implements ScreenFxEffect {

    /** 定身那一下的总时长（后处理通道全部在这个时间内归零） */
    private static final float STUN_DURATION = 0.85F;
    /** 去重窗口：这之内的第二次命中并入当前实例（定身级会覆盖普通级） */
    private static final float MERGE_WINDOW = 0.20F;
    /** RGB 脉冲 / 色散脉冲的节拍频率（每秒几下）——0.55s 窗口内约 4 拍 */
    private static final float BEAT_HZ = 7.5F;

    private static ToughnessImpactScreenFx current;

    private boolean stun;
    private float t;

    private ToughnessImpactScreenFx(boolean stun) {
        this.stun = stun;
    }

    /** 削韧命中：{@code stun = true} 表示这一下把韧性打空、触发了定身 */
    public static void trigger(boolean stun) {
        if (current != null && current.t < MERGE_WINDOW) {
            if (stun && !current.stun) current.promote();
            return;
        }
        // 留一行日志：这条链路横跨「服务端发包 → 客户端屏幕后处理」，出问题时能一眼分清
        // 是"包没到"还是"着色器没画"（每次削韧一行，量很小）
        com.plumejade.lensouls.LenSouls.LOGGER.info("[Toughness] 屏幕反馈触发 stun={}", stun);
        current = new ToughnessImpactScreenFx(stun);
        CameraShake.trigger(stun);
        ScreenFxManager.push(current);
    }

    public static void reset() {
        current = null;
        CameraShake.reset();
    }

    private void promote() {
        if (stun) return;
        stun = true;
        t = 0.0F;
        CameraShake.trigger(true);
    }

    @Override
    public boolean update(float dt) {
        t += dt;
        return t < STUN_DURATION;
    }

    @Override
    public void apply(ScreenFxManager.State s) {
        // 普通削韧：不动后处理（抖动由 CameraShake 负责）
        if (!stun) return;

        float u = Math.min(1.0F, t / STUN_DURATION);

        // 起手：0.055 内冲到峰值 —— 是"砸下来"而不是缓动进来
        float hit = smoothstep(0.0F, 0.055F, u);
        // 两条收尾曲线：快的（闪/色散/速度线）与慢的（拉伸/暗角）
        float fast = 1.0F - smoothstep(0.05F, 0.42F, u);
        float slow = 1.0F - smoothstep(0.18F, 0.98F, u);

        // ── 1. 爆闪 ──
        float flash = 0.62F * (1.0F - smoothstep(0.0F, 0.10F, u));
        if (flash > s.flash) {
            s.flashR = 0.95F;
            s.flashG = 0.97F;
            s.flashB = 1.00F;
            s.flash = flash;
        }

        // ── 2. 拉爆：向中心猛拽 + 径向拖尾（力道的主要来源） ──
        s.warp = Math.max(s.warp, 0.95F * hit * slow);

        // ── 3. 色散：把"被顶穿"讲清楚，0.42 收完；振幅按节拍起伏（RGB 分离脉冲） ──
        float chromaPulse = 0.40F + 0.90F * beat(t, BEAT_HZ);
        s.chroma = Math.max(s.chroma, 1.00F * hit * fast * chromaPulse);

        // ── 4. 径向速度线 ──
        s.streak = Math.max(s.streak, 1.35F * hit * fast);

        // ── 5. 冲击环：从屏幕中心炸开（ease-out 扩散） ──
        s.rippleX = 0.5F;
        s.rippleY = 0.5F;
        float out = 1.0F - (1.0F - u) * (1.0F - u);
        s.rippleRadius = 0.06F + 1.55F * out;
        s.rippleStrength = Math.max(s.rippleStrength,
                0.85F * hit * (1.0F - smoothstep(0.0F, 0.55F, u)));
        s.rippleR = 0.86F;
        s.rippleG = 0.92F;
        s.rippleB = 1.00F;

        // ── 6. 边缘压暗（冷蓝），收尾最慢（压得比原来浅一点，给 RGB 脉冲留位置） ──
        s.addTint(0.06F, 0.09F, 0.14F, 0.30F * hit * slow);

        // ── 7. RGB 脉冲：跟着节拍跳色的三通道呼吸，边缘最明显 ──
        //    env 在 0.34~0.72 之间收回，正好和膨胀的冲击环一起收尾
        float rgbEnv = hit * (1.0F - smoothstep(0.34F, 0.72F, u));
        s.rgb = Math.max(s.rgb, 0.95F * rgbEnv * (0.15F + 0.85F * beat(t, BEAT_HZ)));

        // 冲击环带一点棱镜色：和 RGB 脉冲同调，避免"只有外圈跳色、环还是死银色"
        s.prism = Math.max(s.prism, 0.45F * hit * fast);
    }

    /** 节拍脉冲：每 {@code hz} 次一秒，phase=0 处立刻到达峰值再衰减（急起慢落）。 */
    private static float beat(float seconds, float hz) {
        float phase = (seconds * hz) % 1.0F;
        if (phase < 0.0F) phase += 1.0F;
        float v = 1.0F - phase;
        return v * v;
    }

    private static float smoothstep(float e0, float e1, float x) {
        float v = Math.max(0.0F, Math.min(1.0F, (x - e0) / (e1 - e0)));
        return v * v * (3.0F - 2.0F * v);
    }
}
