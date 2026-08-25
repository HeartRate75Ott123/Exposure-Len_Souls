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
 * 原版画堆叠数量（如 "64"）在 {@code GuiGraphics.renderItemDecorations} 的 5-arg 重载中，
 * 通过 {@link GuiGraphics#drawString} 绘制（文本状态正确）。物品栏/容器/快捷栏槽位由屏幕直接
 * 调用该 5-arg 重载，故 hook 它即可在所有 GUI 槽位生效（4-arg 重载内部亦委托此 5-arg，单次触发）。
 * 等级读取 {@link AnvilUpgradeHandler#getSoulLevel}（含默认等级 1）。
 */
@Mixin(GuiGraphics.class)
public abstract class SoulLevelOverlayMixin {

    private static final int LEVEL_GREEN = 0x33FF33;

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("TAIL"))
    private void lensouls$overlaySoulLevel(Font font, ItemStack stack, int x, int y, String countText, CallbackInfo ci) {
        if (!(stack.getItem() instanceof LensoulItem)) return;

        int level = AnvilUpgradeHandler.getSoulLevel(stack);
        if (level <= 0) return;

        GuiGraphics gui = (GuiGraphics) (Object) this;
        gui.pose().pushPose();
        gui.pose().translate(0.0, 0.0, 200.0);
        gui.drawString(font, String.valueOf(level), x + 1, y + 1, LEVEL_GREEN, true);
        gui.pose().popPose();
    }
}
