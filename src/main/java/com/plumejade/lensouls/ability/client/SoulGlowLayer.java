package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 暂时空置 — BOSS 描边使用平行 mask FBO + composite 方案（Sobel 边缘描边）。
 */
@OnlyIn(Dist.CLIENT)
public class SoulGlowLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    public SoulGlowLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
    }
}
