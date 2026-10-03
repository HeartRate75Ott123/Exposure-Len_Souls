package com.plumejade.lensouls.client.feather;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 界面右上角的关闭按钮（纯程序绘制，不用贴图）。
 * <p>
 * 移植来源：参考项目 {@code plumestweaks} 的 {@code RiftTeleportScreen#renderCloseButton}
 * （同一套视觉口径）：16×16 圆角小方块 + 悬停增亮/描边 + 两根 45° 细条组成的 X。
 * 抽成组件是为了让**两级菜单共用同一份实现**，尺寸/配色/命中判定不会各写一套走样。
 * <p>
 * 调用方负责决定「点它做什么」：一级菜单关闭整个界面（回玩家物品栏），
 * 选择屏返回一级菜单（见各自的 {@code onClose()}/{@code back()}）。
 */
public final class CloseButton {

    /** 按钮边长（GUI 像素） */
    public static final int SIZE = 16;
    /** 距锚点右缘/上缘的间距 */
    private static final int MARGIN = 6;
    /** X 臂长（从中心向外） */
    private static final int ARM = 5;

    private CloseButton() {
    }

    /** 以某个矩形的右上角为锚点：返回按钮左上角 x（rightEdge = 矩形右边界） */
    public static int x(int rightEdge) {
        return rightEdge - SIZE - MARGIN;
    }

    /** 以某个矩形的右上角为锚点：返回按钮左上角 y（topEdge = 矩形上边界） */
    public static int y(int topEdge) {
        return topEdge + MARGIN;
    }

    public static boolean isOver(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }

    /** 绘制按钮本体（悬停时增亮并加高亮描边） */
    public static void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        boolean hovered = isOver(mouseX, mouseY, x, y);

        // 底色（悬停更亮）
        RoundedRect.roundedRect(graphics, x, y, SIZE, SIZE, 4.0F,
                hovered ? 0x40FFFFFF : 0x18FFFFFF,
                hovered ? 0x30FFFFFF : 0x10FFFFFF,
                0, 0.0F);
        if (hovered) {
            RoundedRect.roundedOutline(graphics, x, y, SIZE, SIZE, 4.0F, 0x66FFFFFF, 1.0F);
        }

        // 两根 45° 交叉细条 = X
        int cx = x + SIZE / 2;
        int cy = y + SIZE / 2;
        int color = hovered ? 0xFFFFFFFF : 0xFF9FB3B6;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45.0F));
        graphics.fill(-ARM, -1, ARM, 1, color);
        graphics.fill(-1, -ARM, 1, ARM, color);
        pose.popPose();
    }
}
