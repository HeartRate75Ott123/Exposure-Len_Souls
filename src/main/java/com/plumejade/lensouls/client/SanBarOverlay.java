package com.plumejade.lensouls.client;

import com.plumejade.lensouls.handler.FeatherAbyssHandler;
import com.plumejade.lensouls.handler.FeatherTwitcherHandler;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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
        // 扭曲羽毛 / 折翼沉渊 均可显示（值按各自上限等比例换算，扭曲 0-100，沉渊 0-200 发来的是 /2）
        if (!FeatherTwitcherHandler.hasTwitcher(mc.player) && !FeatherAbyssHandler.hasAbyss(mc.player)) return;

        GuiGraphics g = event.getGuiGraphics();
        // 用 round 保证 34px 纹理完整映射（int 截断会裁掉底部像素并压缩比例）
        int drawW = Math.round(FRAME_WIDTH * SCALE);
        int drawH = Math.round(FRAME_HEIGHT * SCALE);
        int y = (g.guiHeight() - drawH) / 2;
        // 统一实际缩放系数：背景与填充条共享，保证像素网格严格对齐
        float scaleY = drawH / (float) FRAME_HEIGHT;

        // background 全帧完整显示（上下 2px 槽由它提供视觉）
        g.blit(BACKGROUND, X, y, drawW, drawH, 0, 0, FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);

        // 能量填充：从 bar 区底边（v=32 线，即 y=31 底端，丢掉下 2 行边框）往上按比例裁剪
        // 比例直接用浮点：30 行蓝色区 × 扭曲值/100，不逐级取整，视觉与数值严格线性
        int twist = TwistClientCache.get();
        float fillPx = BAR_HEIGHT * twist / 100.0f;
        int drawHpx = Math.round(fillPx * scaleY);
        if (drawHpx > 0) {
            int frame = (mc.gui.getGuiTicks() / FRAME_TIME) % FRAME_COUNT;
            float vStart = frame * FRAME_HEIGHT + (BAR_BOTTOM + 1) - fillPx;
            // 底边对齐 v=32 线（bar 区底端），不压背景的下 2px 槽
            int barBottomY = y + Math.round((BAR_BOTTOM + 1) * scaleY);
            g.blit(BAR, X, barBottomY - drawHpx, drawW, drawHpx,
                    0, vStart, FRAME_WIDTH, (int) Math.ceil(fillPx),
                    FRAME_WIDTH, FRAME_COUNT * FRAME_HEIGHT);
        }

        // 祸之可能性倒计时：物品栏上方红字数字
        if (AbyssCountdownClient.isActive()) {
            int sec = AbyssCountdownClient.remainingSeconds();
            int cx = g.guiWidth() / 2;
            int cy = g.guiHeight() - 60;
            g.drawCenteredString(mc.font, Component.literal("§c" + sec), cx, cy, 0xFF3333);
        }
    }
}
