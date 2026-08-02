package com.plumejade.lensouls.client;

import com.plumejade.lensouls.handler.FeatherTwitcherHandler;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** 屏幕左侧 sanbar HUD 覆盖层：佩戴扭曲羽毛时显示，按扭曲值从下往上填充能量 */
public class SanBarOverlay {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "textures/gui/sanbar_background.png");
    private static final ResourceLocation BAR =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "textures/gui/sanbar.png");

    private static final int FRAME_WIDTH = 16;
    private static final int FRAME_HEIGHT = 34;
    private static final int FRAME_COUNT = 6;
    private static final int FRAME_TIME = 2;
    private static final float SCALE = 1.4f;
    private static final int X = 10;
    /** bar 区高度（帧内 y=2..31，30px） */
    private static final int BAR_HEIGHT = 30;
    /** bar 区底部像素 y=31（从下往上第 3 格），填充从该像素底端（v=32 线）往上 */
    private static final int BAR_BOTTOM = 31;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!FeatherTwitcherHandler.hasTwitcher(mc.player)) return;

        GuiGraphics g = event.getGuiGraphics();
        // 用 round 保证 34px 纹理完整映射（int 截断会裁掉底部像素并压缩比例）
        int drawW = Math.round(FRAME_WIDTH * SCALE);
        int drawH = Math.round(FRAME_HEIGHT * SCALE);
        int y = (g.guiHeight() - drawH) / 2;
        // 统一实际缩放系数：背景与填充条共享，保证像素网格严格对齐
        float scaleY = drawH / (float) FRAME_HEIGHT;

        // background 全帧完整显示（上下 2px 槽由它提供视觉）
        g.blit(BACKGROUND, X, y, drawW, drawH, 0, 0, FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);

        // 能量填充：fillPx 个纹理像素，从 bar 底部（v=32 线）往上裁剪，6 帧动画照常播放
        int twist = TwistClientCache.get();
        int fillPx = (int) Math.ceil(BAR_HEIGHT * twist / 100.0);
        if (fillPx > 0) {
            int frame = (mc.gui.getGuiTicks() / FRAME_TIME) % FRAME_COUNT;
            int vStart = frame * FRAME_HEIGHT + (BAR_BOTTOM + 1) - fillPx;
            int drawHpx = Math.round(fillPx * scaleY);
            // 底边对齐 v=32 线（bar 区底端），不压背景的下 2px 槽
            int barBottomY = y + Math.round((BAR_BOTTOM + 1) * scaleY);
            g.blit(BAR, X, barBottomY - drawHpx, drawW, drawHpx,
                    0, vStart, FRAME_WIDTH, fillPx, FRAME_WIDTH, FRAME_COUNT * FRAME_HEIGHT);
        }
    }
}
