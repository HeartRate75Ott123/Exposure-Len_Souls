package com.plumejade.lensouls.boss;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 削韧无敌闪烁 RenderType 工厂。
 * <p>
 * 与 {@link StunGlintRenderTypes} 同构，使用 invincible_glint_entity 纹理。
 */
public class InvincibleGlintRenderTypes {

    private static final ResourceLocation INVINCIBLE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/misc/invincible_glint_entity.png");

    private static RenderType bodyGlint = null;

    public static RenderType bodyGlint() {
        if (bodyGlint == null) bodyGlint = createBodyGlint();
        return bodyGlint;
    }

    private static RenderType createBodyGlint() {
        var shaderState    = new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeArmorEntityGlintShader);
        var textureState   = new RenderStateShard.TextureStateShard(INVINCIBLE_TEXTURE, true, false);
        var blendState     = RenderStateShard.GLINT_TRANSPARENCY;
        var depthState     = RenderStateShard.NO_DEPTH_TEST;
        var cullState      = RenderStateShard.NO_CULL;
        var lightmapState  = RenderStateShard.NO_LIGHTMAP;
        var overlayState   = RenderStateShard.NO_OVERLAY;
        var layeringState  = RenderStateShard.VIEW_OFFSET_Z_LAYERING;
        var outputState    = RenderStateShard.MAIN_TARGET;
        var texturingState = RenderStateShard.ENTITY_GLINT_TEXTURING;
        var writeState     = RenderStateShard.COLOR_WRITE;
        var colorLogicState = RenderStateShard.NO_COLOR_LOGIC;
        var lineState      = RenderStateShard.DEFAULT_LINE;

        return new RenderType(
                "lensouls_invincible_glint",
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

    private InvincibleGlintRenderTypes() {}
}
