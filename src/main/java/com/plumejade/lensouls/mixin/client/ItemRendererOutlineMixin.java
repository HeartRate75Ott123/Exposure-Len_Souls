package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.client.itemoutline.ItemOutlinePostProcessor;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称手持物描边捕获：在 {@code ItemRenderer.render} 的 HEAD 对第一人称手持物把物品
 * 再渲染一次进独立 mask 目标。非第一人称/无活跃元素配色时直接跳过，不影响其他渲染。
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererOutlineMixin {

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"),
            require = 1)
    private void lensouls$onRenderHead(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                       PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight,
                                       int combinedOverlay, BakedModel model, CallbackInfo ci) {
        ItemOutlinePostProcessor.onRenderHead(stack, context, leftHand, poseStack, bufferSource,
                combinedLight, combinedOverlay, model, (ItemRenderer) (Object) this);
    }
}
