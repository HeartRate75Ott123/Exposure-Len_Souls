package com.plumejade.lensouls.client;

import com.plumejade.lensouls.util.WeaknessLensPhoto;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/**
 * 弱点透镜照片的耐久条（物品栏里那一小条）。
 * <p>
 * 走 NeoForge 的 item decorator 而不是原版 {@code DataComponents.DAMAGE}：
 * 一旦带原版耐久组件，照片就会变成**不可堆叠**（原版对 damageable 物品强制 stackSize=1），
 * 而照片堆叠是既有玩法（{@code StackedPhotographsItem}、时空回溯按堆查找都依赖它）。
 * 所以耐久存自定义键 {@code lensouls:durability}，条在这里自绘。
 * <p>
 * 绘制口径与 {@code ItemRenderer.renderBar} 一致（13×2 像素、左上角 +2/+13、
 * 底色纯黑、前景按剩余比例走 HSV 绿→红），并且**满耐久不画**（与原版一致，平时不占视觉）。
 */
public class WeaknessLensDurabilityDecorator implements IItemDecorator {

    /** 与 {@code ItemStack.getBarWidth()} 的 13 像素一致 */
    private static final int BAR_WIDTH = 13;
    private static final int BAR_BLANK = 0xFF000000;

    @Override
    public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        // 无耐久标记（旧照片/其它能力照片）返回 -1，不画条
        int left = WeaknessLensPhoto.getDurabilityOrNone(stack);
        if (left < 0 || left >= WeaknessLensPhoto.MAX_DURABILITY) return false;

        float ratio = left / (float) WeaknessLensPhoto.MAX_DURABILITY;
        int width = Math.round(BAR_WIDTH * ratio);
        int x = xOffset + 2;
        int y = yOffset + 13;
        // 原版款式（照 `ItemRenderer.renderGuiItemDecorations`）：底色 13×2 纯黑，
        // 前景（彩色）只占**上面 1 行**。此前前景也画 2 行，视觉上就显得比原版粗一倍。
        guiGraphics.fill(RenderType.guiOverlay(), x, y, x + BAR_WIDTH, y + 2, BAR_BLANK);
        guiGraphics.fill(RenderType.guiOverlay(), x, y, x + width, y + 1,
                Mth.hsvToRgb(ratio / 3.0F, 1.0F, 1.0F) | 0xFF000000);
        return true;
    }
}
