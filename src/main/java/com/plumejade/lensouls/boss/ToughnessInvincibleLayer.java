package com.plumejade.lensouls.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;

/**
 * 削韧无敌闪烁渲染层。
 * <p>
 * 当 BOSS 处于削韧无敌窗口（每次削韧后 3 秒）时，
 * 在实体表面叠加一层 invincible_glint 闪烁纹理。
 */
public class ToughnessInvincibleLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    public ToughnessInvincibleLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, T livingEntity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        if (!BossToughnessClientCache.isInvincible(livingEntity.getId())) return;

        RenderType renderType = InvincibleGlintRenderTypes.bodyGlint();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

        this.getParentModel().renderToBuffer(poseStack, vertexConsumer,
                0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }
}
