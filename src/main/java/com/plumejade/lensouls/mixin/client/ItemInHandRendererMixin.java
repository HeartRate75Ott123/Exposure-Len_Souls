package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.ability.client.HeldItemGlowRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称手持物品发光：mask FBO 捕获时，空手那只跳过（无物品模型，不发光）。
 */
@Mixin(value = ItemInHandRenderer.class, priority = 900)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void lensouls$skipEmptyHand(AbstractClientPlayer player, float partialTick, float pitch,
                                        InteractionHand hand, float swingProgress, ItemStack stack,
                                        float equipProgress, PoseStack poseStack, MultiBufferSource bufferSource,
                                        int packedLight, CallbackInfo ci) {
        if (HeldItemGlowRenderer.isCapturing() && stack.isEmpty()) {
            ci.cancel();
        }
    }
}
