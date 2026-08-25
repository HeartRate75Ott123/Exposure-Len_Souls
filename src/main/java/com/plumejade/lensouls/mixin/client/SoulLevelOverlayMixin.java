package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.handler.AnvilUpgradeHandler;
import com.plumejade.lensouls.item.LensoulItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 镜魂等级叠加：在 GUI 渲染镜魂物品时，于左上角叠加绿色阿拉伯数字表示该镜魂等级。
 * <p>
 * 与照片角标同理，在 ItemRenderer.render 的 TAIL 注入，仅 GUI 上下文、且物品为镜魂时绘制。
 * 读取 {@link AnvilUpgradeHandler#getSoulLevel}（含默认等级 1）。
 */
@Mixin(value = ItemRenderer.class, priority = 1002)
public abstract class SoulLevelOverlayMixin {

    private static final int LEVEL_GREEN = 0x33FF33;

    @Inject(method = "render", at = @At("TAIL"))
    private void lensouls$overlaySoulLevel(ItemStack itemStackIn, ItemDisplayContext itemDisplayContext,
            boolean leftHand, PoseStack poseStackIn, MultiBufferSource bufferIn, int combinedLightIn,
            int combinedOverlayIn, BakedModel modelIn, CallbackInfo ci) {
        if (itemDisplayContext != ItemDisplayContext.GUI) return;
        if (!(itemStackIn.getItem() instanceof LensoulItem)) return;

        int level = AnvilUpgradeHandler.getSoulLevel(itemStackIn);
        if (level <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        String text = String.valueOf(level);

        poseStackIn.pushPose();
        poseStackIn.translate(1.0, 1.0, 0.5);
        // 关闭深度测试，使数字始终绘制在镜魂图标之上（z 在前、不被遮挡）
        RenderSystem.disableDepthTest();
        try {
            font.drawInBatch(text, 0, 0, LEVEL_GREEN, true,
                    poseStackIn.last().pose(), bufferIn,
                    Font.DisplayMode.NORMAL, 0, 0xF000F0);
        } finally {
            RenderSystem.enableDepthTest();
        }
        poseStackIn.popPose();
    }
}
