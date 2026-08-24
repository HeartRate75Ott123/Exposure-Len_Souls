package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.ability.client.HeldItemGlowRenderer;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 第一人称手持物品发光（仿 ItemGlint）：
 * {@code GameRenderer.renderItemInHand} 里正常渲染手部后，若玩家有 Boss 镜魂 effect，
 * 再把手持物品（非空手）渲染进 mask FBO，用元素主色 composite 发光。
 */
@Mixin(value = GameRenderer.class, priority = 900)
public abstract class GameRendererHeldGlowMixin {

    @Shadow @Final private Minecraft minecraft;

    @Redirect(method = "renderItemInHand",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"),
            require = 0)
    private void lensouls$renderHeldGlow(ItemInHandRenderer renderer, float tickDelta, PoseStack poseStack,
                                         MultiBufferSource.BufferSource bufferSource, LocalPlayer player,
                                         int packedLight) {
        renderer.renderHandsWithItems(tickDelta, poseStack, bufferSource, player, packedLight);

        if (!HeldItemGlowRenderer.shouldRender(minecraft)) return;
        BossOutlineColors colors = BossOutlineColors.fromEntity(player);
        if (colors == null) return;

        HeldItemGlowRenderer.beginCapture(minecraft);
        try {
            // 再渲染一次：手持物品（非空手）画进 mask FBO
            renderer.renderHandsWithItems(tickDelta, poseStack, bufferSource, player, packedLight);
            bufferSource.endBatch();
        } finally {
            HeldItemGlowRenderer.endCapture();
        }
        HeldItemGlowRenderer.composite(minecraft, colors);
    }
}
