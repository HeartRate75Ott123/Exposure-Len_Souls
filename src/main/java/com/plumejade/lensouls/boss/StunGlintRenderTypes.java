package com.plumejade.lensouls.boss;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 定身红色脉冲闪烁 RenderType 工厂。
 * <p>
 * 与 {@link com.plumejade.lensouls.ability.client.GoldGlintRenderTypes} 完全同构，
 * 使用原版 {@link GameRenderer#getRendertypeArmorEntityGlintShader} 着色器。
 * 纹理为附魔闪烁纹理，由纹理本身提供颜色。
 */
public class StunGlintRenderTypes {

    private static final ResourceLocation STUN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/misc/stun_glint_entity.png");

    // ========== 身体光效 RenderType（懒加载，避免类加载时序） ==========

    private static RenderType bodyGlint = null;

    public static RenderType bodyGlint() {
        if (bodyGlint == null) bodyGlint = createBodyGlint();
        return bodyGlint;
    }

    private static RenderType createBodyGlint() {
        var shaderState   = new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeArmorEntityGlintShader);
        var textureState  = new RenderStateShard.TextureStateShard(STUN_TEXTURE, true, false);
        var blendState    = RenderStateShard.GLINT_TRANSPARENCY;
        var depthState    = RenderStateShard.NO_DEPTH_TEST;
        var cullState     = RenderStateShard.NO_CULL;
        var lightmapState = RenderStateShard.NO_LIGHTMAP;
        var overlayState  = RenderStateShard.NO_OVERLAY;
        var layeringState = RenderStateShard.VIEW_OFFSET_Z_LAYERING;
        var outputState   = RenderStateShard.MAIN_TARGET;
        var texturingState = RenderStateShard.ENTITY_GLINT_TEXTURING;
        var writeState    = RenderStateShard.COLOR_WRITE;
        var colorLogicState = RenderStateShard.NO_COLOR_LOGIC;
        var lineState     = RenderStateShard.DEFAULT_LINE;

        return new RenderType(
                "lensouls_stun_glint",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                1536, false, false,
                () -> {
                    textureState.setupRenderState();
                    shaderState.setupRenderState();
                    blendState.setupRenderState();
                    depthState.setupRenderState();
                    cullState.setupRenderState();
                    lightmapState.setupRenderState();
                    overlayState.setupRenderState();
                    layeringState.setupRenderState();
                    outputState.setupRenderState();
                    texturingState.setupRenderState();
                    writeState.setupRenderState();
                    colorLogicState.setupRenderState();
                    lineState.setupRenderState();
                },
                () -> {
                    textureState.clearRenderState();
                    shaderState.clearRenderState();
                    blendState.clearRenderState();
                    depthState.clearRenderState();
                    cullState.clearRenderState();
                    lightmapState.clearRenderState();
                    overlayState.clearRenderState();
                    layeringState.clearRenderState();
                    outputState.clearRenderState();
                    texturingState.clearRenderState();
                    writeState.clearRenderState();
                    colorLogicState.clearRenderState();
                    lineState.clearRenderState();
                }
        ) {};
    }

    private StunGlintRenderTypes() {}
}
