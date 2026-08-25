package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.handler.AnvilUpgradeHandler;
import com.plumejade.lensouls.item.LensoulItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 镜魂等级叠加：在 GUI 渲染镜魂物品时，于左上角叠加绿色阿拉伯数字表示该镜魂等级。
 * <p>
 * 原版物品上的数字（堆叠数量）在 {@link GuiGraphics#renderItemDecorations} 绘制，该处文本
 * 着色器/混合状态就绪，文本渲染可靠。与此同源，避免使用 ItemRenderer.render 的 TAIL 注入
 * （那里文本状态未就绪，font.drawInBatch 不显示）。
 * 等级读取 {@link AnvilUpgradeHandler#getSoulLevel}（含默认等级 1）。
 */
@Mixin(GuiGraphics.class)
public abstract class SoulLevelOverlayMixin {

    private static final int LEVEL_GREEN = 0x33FF33;

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
            at = @At("TAIL"))
    private void lensouls$overlaySoulLevel(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        if (!(stack.getItem() instanceof LensoulItem)) return;

        int level = AnvilUpgradeHandler.getSoulLevel(stack);
        if (level <= 0) return;

        GuiGraphics gui = (GuiGraphics) (Object) this;
        font.drawInBatch(String.valueOf(level), x + 1, y + 1, LEVEL_GREEN, true,
                gui.pose().last().pose(), gui.bufferSource(),
                Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }
}
