package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.plumejade.lensouls.ability.client.BossSoulItemState;
import com.plumejade.lensouls.ability.client.SoulGlowRenderTypes;
import com.plumejade.lensouls.ability.client.SoulGlowShader;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 物品发光 — 直接 RenderType 模式（仿金色 glint）。
 * <p>
 * 在 {@code renderModelLists} 调用前拦截 VertexConsumer，
 * 若 {@link BossSoulItemState} 活跃则包裹双发光层（aura + surface）。
 * 递归守卫防止二次包裹。
 */
@Mixin(value = ItemRenderer.class, priority = 900)
public abstract class ItemRendererMixin {

    @Unique private static boolean lensouls$glowGuard = false;

    @ModifyVariable(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(" +
                            "Lnet/minecraft/client/resources/model/BakedModel;" +
                            "Lnet/minecraft/world/item/ItemStack;II" +
                            "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                            "Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"),
            ordinal = 0,
            require = 0)
    private VertexConsumer lensouls$wrapItemGlow(VertexConsumer consumer,
                                                  ItemStack stack, ItemDisplayContext context,
                                                  boolean leftHand, PoseStack poseStack,
                                                  MultiBufferSource bufferSource, int light, int overlay,
                                                  BakedModel model) {
        if (!BossSoulItemState.isActive()) return consumer;
        if (lensouls$glowGuard) return consumer;
        if (consumer == null) return consumer;

        BossOutlineColors colors = BossSoulItemState.getColors();
        if (colors == null) return consumer;

        // 设置着色器 Uniform
        SoulGlowShader.setBossColors(colors);
        SoulGlowShader.setGlowIntensity(1.0f);
        SoulGlowShader.setOutlineWidth(0.04f);
        SoulGlowShader.setUseGlowExpansion(true);
        SoulGlowShader.setUseTextureAlpha(false);

        lensouls$glowGuard = true;
        try {
            VertexConsumer aura = bufferSource.getBuffer(SoulGlowRenderTypes.itemAura());
            VertexConsumer surface = bufferSource.getBuffer(SoulGlowRenderTypes.itemSurface());
            return VertexMultiConsumer.create(aura, surface, consumer);
        } finally {
            lensouls$glowGuard = false;
        }
    }
}
