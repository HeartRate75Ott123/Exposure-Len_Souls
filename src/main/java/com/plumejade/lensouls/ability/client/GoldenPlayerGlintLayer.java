package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 玩家身体金色光效调试层。
 * <p>
 * 自包含 RenderType {@link GoldGlintRenderTypes#bodyGlint()}，
 * 直接绑定金色贴图 + 原版 glint 着色器 + UV 滚动动画。
 * 不依赖任何 mixin 拦截，不影响其他物品/实体光效。
 */
@OnlyIn(Dist.CLIENT)
public class GoldenPlayerGlintLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public static boolean visible = false;

    public GoldenPlayerGlintLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!visible) return;

        VertexConsumer consumer = bufferSource.getBuffer(GoldGlintRenderTypes.bodyGlint());
        getParentModel().renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
    }
}
