package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 定格蓝色光效 RenderType 工厂。
 * <p>
 * 自包含方案：不依赖任何 mixin 拦截，直接绑定蓝色光效贴图
 * （enchanted_glint_entity.png，纹理本身为蓝色）。
 */
public class FrozenBlueGlintRenderTypes {

    public static boolean enabled = false;

    private static final ResourceLocation FROZEN_BLUE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/misc/enchanted_glint_entity.png");

    // ========== 身体光效 RenderType ==========

    private static final RenderType BODY_GLINT = createBodyGlint();

    public static RenderType bodyGlint() {
        return BODY_GLINT;
    }

    private static RenderType createBodyGlint() {
        var shaderState   = new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeArmorEntityGlintShader);
        var textureState  = new RenderStateShard.TextureStateShard(FROZEN_BLUE_TEXTURE, true, false);
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
                "lensouls_frozen_blue_glint",
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

    private FrozenBlueGlintRenderTypes() {}
}