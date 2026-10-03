package com.plumejade.lensouls.client.feather;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * GUI 内浮动提示（如「该槽位已永久锁定」）。
 * <p>
 * <b>为什么不用动作栏消息</b>：GUI 渲染在消息层之上，玩家在界面里根本看不到聊天/动作栏提示。
 * 所以提示必须**画在当前 GUI 里**，并带不透明度淡入淡出，几秒后自动消失。
 * <p>
 * 时间轴：淡入 0.25s → 停留 2s → 淡出 0.5s。
 * <p>
 * <b>透明度必须统一走 {@link GuiGraphics#setColor}</b>（= RenderSystem 的 ColorModulator）：
 * 底板是 `rrect` 着色器按色值里的 alpha 画的，而文字走的是另一条渲染通路——
 * 之前只把 alpha 烘进颜色，实机表现就是「底板已消失、文字还满不透明几帧后突兀消失」。
 * 用 ColorModulator 一次性乘住「底板 + 文字」，两者才会同步渐隐。
 */
public final class FeatherToast {

    private static final float FADE_IN_MS = 250.0F;
    private static final float HOLD_MS = 2000.0F;
    private static final float FADE_OUT_MS = 500.0F;
    private static final float TOTAL_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS;

    /** 峰值不透明度（不完全盖死背景） */
    private static final float PEAK_ALPHA = 0.95F;

    private static Component text;
    private static long startedAt;

    private FeatherToast() {
    }

    /** 弹出一条提示（重复调用会替换文本并重新计时） */
    public static void show(Component message) {
        text = message;
        startedAt = System.currentTimeMillis();
    }

    public static void clear() {
        text = null;
    }

    public static boolean isVisible() {
        return text != null;
    }

    /** 在 GUI 里渲染；由屏幕的 render 末尾调用（这样它压在所有内容之上） */
    public static void render(GuiGraphics graphics, int width, int height) {
        if (text == null) return;

        float elapsed = System.currentTimeMillis() - startedAt;
        if (elapsed >= TOTAL_MS) {
            text = null;
            return;
        }

        float alpha;
        if (elapsed < FADE_IN_MS) {
            alpha = elapsed / FADE_IN_MS;
        } else if (elapsed < FADE_IN_MS + HOLD_MS) {
            alpha = 1.0F;
        } else {
            alpha = 1.0F - (elapsed - FADE_IN_MS - HOLD_MS) / FADE_OUT_MS;
        }
        alpha = Mth.clamp(alpha, 0.0F, 1.0F) * PEAK_ALPHA;
        if (alpha <= 0.01F) return;

        var mc = Minecraft.getInstance();
        var font = mc.font;
        int textWidth = font.width(text);
        int boxW = textWidth + 16;
        int boxH = 16;
        int x = (width - boxW) / 2;
        int y = (int) (height * 0.62F);

        // ★ 两条通路必须**各自**承载 alpha，缺一不可：
        //   ① 底板走 `rrect` 着色器 —— 它的 uniform 是自己声明的（FillColor/Tint），
        //      **不读 ColorModulator**，所以 setColor 对底板无效，必须把 alpha 烘进色值；
        //   ② 文字走原版字体渲染 —— 色值里的 alpha 对它没产生预期效果（实测），要靠 setColor。
        int baseAlpha = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        try {
            RoundedRect.roundedRect(graphics, x, y, boxW, boxH, 6.0F,
                    (baseAlpha * 3 / 4) << 24 | 0x14181F,
                    (baseAlpha * 3 / 4) << 24 | 0x0E1218,
                    (Math.round(alpha * 0.35F * 255.0F) << 24) | 0xFFFFFF, 1.0F);
            graphics.drawString(font, text, x + 8, y + 4, 0xF2F6FF, true);
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
