package com.plumejade.lensouls.client.photogallery;

import com.plumejade.lensouls.client.feather.RoundedRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 「快捷功能」弹出列表（点右上角按钮展开，三行）。
 *
 * <h2>三行</h2>
 * <ol>
 *   <li>清空当前装备的全部照片饰品；</li>
 *   <li>剔除重复的照片饰品；</li>
 *   <li>点击规则切换行 —— 文案本身就是状态：<br>
 *       {@code 当前：点击网格槽位时发放照片到物品栏（点击更改规则）} ⇄
 *       {@code 当前：点击网格槽位时直接装备（点击更改规则）}</li>
 * </ol>
 * 第 2 行的用户可见文案**不得**出现「同种生物多张 / 开发设置的局限」之类字眼（用户明确要求）；
 * 真实原因写在 {@code PhotoGallerySettlement#dedupe} 的注释里。
 */
public final class PhotoGalleryShortcutPanel {

    /** 行高亮相对底板的左右/上下内缩（同补全弹窗，避免 SDF 抗锯齿溢到 1px 描边上） */
    private static final int ROW_INSET_X = 3;
    private static final int ROW_INSET_Y = 2;
    /** 底板**上方**额外外扩的像素：只放大底板矩形，三行内容与行高亮位置一律不动 */
    private static final int PANEL_EXTEND_TOP = 2;

    public static final int ROW_CLEAR = 0;
    public static final int ROW_DEDUPE = 1;
    public static final int ROW_RULE = 2;

    private final int x;
    private final int y;
    private final int width;
    private final int rowHeight;
    /** 圆角半径（由界面按 k 传入，与其它模块统一） */
    private final int radius;

    private boolean open;

    public PhotoGalleryShortcutPanel(int x, int y, int width, int rowHeight, int radius) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.rowHeight = rowHeight;
        this.radius = Math.max(2, radius);
    }

    public boolean isOpen() {
        return open;
    }

    public void toggle() {
        open = !open;
    }

    public void close() {
        open = false;
    }

    public int height() {
        return rowHeight * 3;
    }

    public boolean isOver(double mouseX, double mouseY) {
        // 命中区与底板一致（含上方外扩的 2px），免得那一条点穿到下面的控件
        return open && mouseX >= x && mouseX < x + width
                && mouseY >= y - PANEL_EXTEND_TOP && mouseY < y + height();
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!isOver(mouseX, mouseY)) return -1;
        int row = (int) ((mouseY - y) / rowHeight);
        return row >= 0 && row < 3 ? row : -1;
    }

    /**
     * 渲染列表。文案由上到下依次为「清空 / 去重 / 规则行」。
     *
     * @param ruleLabel 规则行的当前文案（由界面按菜单里的规则生成）
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, Component ruleLabel) {
        if (!open) return;
        // 不透明底板（0xF0 会让下方文字隐约透出，看着像重叠）
        RoundedRect.roundedRect(graphics, x, y - PANEL_EXTEND_TOP, width, height() + PANEL_EXTEND_TOP, radius,
                0xFF10161E, 0xFF0A0F16, 0x40FFFFFF, 1.0F);

        List<Component> rows = List.of(
                Component.translatable("gui.lensouls.photos.shortcut.clear"),
                Component.translatable("gui.lensouls.photos.shortcut.dedupe"),
                ruleLabel);

        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < rows.size(); i++) {
            int rowY = y + i * rowHeight;
            if (i == hovered) {
                // 与补全弹窗同口径：圆角 + 内缩（原来 graphics.fill 直角矩形贴到描边上）
                RoundedRect.roundedRect(graphics, x + ROW_INSET_X, rowY + ROW_INSET_Y,
                        width - ROW_INSET_X * 2, rowHeight - ROW_INSET_Y * 2,
                        Math.max(2, radius - ROW_INSET_X),
                        0x33FFFFFF, 0x33FFFFFF, 0, 0.0F);
            }
            drawFitted(graphics, rows.get(i), x + ROW_INSET_X + 3, rowY + (rowHeight - 8) / 2,
                    i == ROW_RULE ? 0xFF7CFF9B : 0xFFE6ECF5);
        }
    }

    /** 单行文本，超出列表宽度时等比缩字号（下限 0.55）—— 小窗口下规则行也不会溢出面板 */
    private void drawFitted(GuiGraphics graphics, Component text, int textX, int textY, int color) {
        var font = Minecraft.getInstance().font;
        String s = text.getString();
        int textWidth = font.width(s);
        int maxWidth = width - 10;
        if (textWidth <= maxWidth) {
            graphics.drawString(font, s, textX, textY, color, false);
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        float scale = Math.max(0.55F, maxWidth / (float) textWidth);
        pose.translate(textX, textY, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-textX, -textY, 0.0F);
        graphics.drawString(font, s, textX, textY, color, false);
        pose.popPose();
    }

    /**
     * @param onRow 命中的行回调
     * @return true = 事件被列表消费（不再往网格/面板分发）
     */
    public boolean mouseClicked(double mouseX, double mouseY, java.util.function.IntConsumer onRow) {
        if (!open) return false;
        int row = rowAt(mouseX, mouseY);
        if (row < 0) {
            // 落在底板的外扩条里：算消费（不触发任何行），避免点穿到下面的控件
            return isOver(mouseX, mouseY);
        }
        if (row != ROW_RULE) open = false;
        onRow.accept(row);
        return true;
    }
}
