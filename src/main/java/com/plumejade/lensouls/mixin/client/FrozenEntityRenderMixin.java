package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.plumejade.lensouls.ability.client.CaptureState;
import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
import com.plumejade.lensouls.ability.client.GoldGlintRenderTypes;
import com.plumejade.lensouls.boss.BossToughnessClientCache;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class FrozenEntityRenderMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void lensouls$beginCapture(
            LivingEntity entity, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (entity != null && ClientFreezeCache.isFrozen(entity.getId())) {
            FrozenOutlineManager.ensureMaskCleared();
            CaptureState.tryStartCapture(entity.getId());
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void lensouls$renderGlintBeforePop(
            LivingEntity entity, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        int id = CaptureState.getCaptureEntityId();
        boolean isFrozen = id >= 0 && ClientFreezeCache.isFrozen(id);
        boolean isStunned = entity != null && BossToughnessClientCache.isStunned(entity.getId());
        boolean isInvincible = entity != null && BossToughnessClientCache.isInvincible(entity.getId());

        if (!isFrozen && !isStunned && !isInvincible) return;

        var renderer = (LivingEntityRenderer<?, ?>)(Object) this;
        EntityModel<?> model = renderer.getModel();

        if (isFrozen) {
            CaptureState.flushMask();
            VertexConsumer consumer = bufferSource.getBuffer(GoldGlintRenderTypes.bodyGlint());
            model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        }

        if (isStunned) {
            VertexConsumer stunConsumer = bufferSource.getBuffer(com.plumejade.lensouls.boss.StunGlintRenderTypes.bodyGlint());
            model.renderToBuffer(poseStack, stunConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        }

        if (isInvincible && !isStunned) {
            VertexConsumer invConsumer = bufferSource.getBuffer(com.plumejade.lensouls.boss.InvincibleGlintRenderTypes.bodyGlint());
            model.renderToBuffer(poseStack, invConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void lensouls$endCapture(
            LivingEntity entity, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        CaptureState.endCapture();
    }
}
