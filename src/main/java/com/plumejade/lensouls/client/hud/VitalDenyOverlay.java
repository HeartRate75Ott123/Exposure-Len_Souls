package com.plumejade.lensouls.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 要害打击「白霸体挡下」时压在最上层的<b>赛博朋克故障叉号</b>。
 *
 * <h3>只画叉号，不带底层</h3>
 * 用户明确要求："新特效只用叠加叉号部分" —— <b>不压暗世界、不放四角括号、不写字、不做扫描线</b>，
 * 也不复用 N 公司 2 级员工那套特效的底层（闪白 + 取景框 + 横向失焦）。这里只有一支巨大的 ×，
 * 因此不会和取景器自己的准星/四角臂抢画面，也不会把玩家看到的画面整体压暗。
 *
 * <h3>形态（方案 B：故障切片）</h3>
 * 屏幕被横向切成 {@link #BANDS} 条，每条各自左右错位、约 1/5 的条子直接"撕掉"（留横向缺口），
 * 再叠青/品红两片轻微分离的复制体做色散。错位每 {@link #GLITCH_STEP_MS} 毫秒重掷一次，
 * 所以那 0.35s 停留里它是持续抖的，不是一张静态图。
 *
 * <h3>时序</h3>
 * {@code 60ms} 硬砸进来（sqrt 曲线，前几帧就到位，不做慢淡入） → {@code 350ms} 停留抖动
 * → {@code 250ms} 淡出。整段 0.66s。
 *
 * <h3>画在哪 / 何时不画</h3>
 * 由 {@code GameRendererFrameEndMixin} 在 vanilla {@code GuiGraphics.flush()} 之后调用，
 * 与 {@link ViewfinderOverlay} 同一处 —— Exposure 打开取景器会 cancel 整个 {@code Gui.render}，
 * 只有这个注入点能稳定压在最上层。
 * 打开任何 Screen、或正在后台抓帧出片时一律跳过（不污染照片）。
 */
@OnlyIn(Dist.CLIENT)
public final class VitalDenyOverlay {

    /** 硬砸进来用的时间（ms） */
    private static final long IN_MS = 60L;
    /** 停留（期间持续抖动） */
    private static final long HOLD_MS = 350L;
    /** 淡出 */
    private static final long FADE_MS = 250L;
    private static final long TOTAL_MS = IN_MS + HOLD_MS + FADE_MS;    // 660

    /** 横向切片数量：越少条越粗、越像"信号撕裂"；18 条在中高分辨率下不会碎成噪点 */
    private static final int BANDS = 18;
    /** 错位重掷间隔 */
    private static final long GLITCH_STEP_MS = 90L;

    /** 主体红 / 色散青 / 色散品红 / 白热芯 */
    private static final int RED = 0xFF2D55;
    private static final int CYAN = 0x22D3EE;
    private static final int MAGENTA = 0xFF00A8;
    private static final int HOT = 0xFFE7EA;

    private static long startedAt = -1L;
    private static long glitchBucket = -1L;
    private static long glitchSeed = 0L;

    private VitalDenyOverlay() {
    }

    /** 触发一次（重复调用会重新计时，玩家连按不会叠出多份） */
    public static void show() {
        startedAt = System.currentTimeMillis();
    }

    public static void render() {
        if (startedAt < 0L) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.noRender) return;

        long elapsed = System.currentTimeMillis() - startedAt;
        if (elapsed < 0L || elapsed >= TOTAL_MS) {
            startedAt = -1L;
            return;
        }
        // 开界面 / 后台抓帧出片时都不画（照片里不该出现这支叉）
        if (mc.screen != null) return;
        if (io.github.mortuusars.exposure.client.capture.task.BackgroundScreenshotCaptureTask.isCapturing()) {
            startedAt = -1L;
            return;
        }

        float alpha = alphaAt(elapsed);
        if (alpha <= 0.01F) return;

        long bucket = elapsed / GLITCH_STEP_MS;
        if (bucket != glitchBucket) {
            glitchBucket = bucket;
            glitchSeed = System.nanoTime() * 0x9E3779B97F4A7C15L + bucket;
        }

        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            draw(graphics, alpha);
        } finally {
            graphics.flush();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    /** 硬砸入：sqrt 曲线让前 1~2 帧就基本到位，读作"啪一下出现"而不是淡入 */
    private static float alphaAt(long elapsed) {
        if (elapsed < IN_MS) {
            float k = elapsed / (float) IN_MS;
            return Math.min(1.0F, Mth.sqrt(k));
        }
        if (elapsed < IN_MS + HOLD_MS) return 1.0F;
        float k = (elapsed - IN_MS - HOLD_MS) / (float) FADE_MS;
        return Math.max(0.0F, 1.0F - k * k);
    }

    private static void draw(GuiGraphics g, float alpha) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        int cx = w / 2;
        int cy = h / 2;

        // 叉号对角线总长度：留 14% 边距，别戳到屏幕角
        float halfLen = Math.min(w, h) * 0.43F;
        float thick = Math.min(w, h) * 0.052F;

        int bands = BANDS;
        int bandH = Math.max(3, h / bands);
        RandomSource rnd = RandomSource.create(glitchSeed);

        for (int i = 0; i < bands; i++) {
            int y0 = i * bandH;
            if (y0 >= h) break;
            int y1 = Math.min(h, y0 + bandH);

            // 约 1/5 的横向条直接撕掉：缺口是"故障"的关键，全画反而像干净的叉
            if (rnd.nextInt(5) == 0) continue;

            int shift = (int) ((rnd.nextFloat() - 0.5F) * w * 0.05F);
            float bandAlpha = alpha * (0.72F + rnd.nextFloat() * 0.28F);

            g.enableScissor(0, y0, w, y1);
            // 色散：青/品红各偏 3px，低透明度（只做"通道错位"的暗示，不抢主体）
            drawCross(g, cx + shift - 3, cy, halfLen, thick, argb(CYAN, bandAlpha * 0.40F));
            drawCross(g, cx + shift + 3, cy, halfLen, thick, argb(MAGENTA, bandAlpha * 0.40F));
            // 主体红 + 白热芯
            drawCross(g, cx + shift, cy, halfLen, thick, argb(RED, bandAlpha));
            drawCross(g, cx + shift, cy, halfLen, thick * 0.30F, argb(HOT, bandAlpha * 0.85F));
            g.disableScissor();
        }
    }

    /** 一支 45° 的 ×（两条互相垂直的斜杠，同一姿势内画完） */
    private static void drawCross(GuiGraphics g, int cx, int cy, float halfLen, float thick, int color) {
        int hl = Math.round(halfLen);
        int ht = Math.max(1, Math.round(thick * 0.5F));

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45.0F));
        g.fill(-hl, -ht, hl, ht, color);
        g.pose().mulPose(Axis.ZP.rotationDegrees(90.0F));
        g.fill(-hl, -ht, hl, ht, color);
        g.pose().popPose();
    }

    private static int argb(int rgb, float alpha) {
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
