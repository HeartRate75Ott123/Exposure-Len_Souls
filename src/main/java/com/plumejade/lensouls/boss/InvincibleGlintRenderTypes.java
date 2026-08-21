package com.plumejade.lensouls.boss;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.plumejade.lensouls.ability.client.CaptureState;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 削韧无敌闪烁 RenderType 工厂。
 * <p>
 * per-纹理方案：{@link #bodyGlint(int)} 每实体层一个类型（绑定该层纹理做
 * Sampler1 alpha 剔除），层切换即 flush——多纹理实体每层用自己纹理，互不误滤。
 */
public class InvincibleGlintRenderTypes {

    private static final ResourceLocation INVINCIBLE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/misc/invincible_glint_entity.png");

    // ========== 身体光效 RenderType（per-纹理） ==========

    private static final Map<Integer, RenderType> BODY_GLINT_BY_TEXTURE = new HashMap<>();

    public static RenderType bodyGlint(int textureId) {
        return BODY_GLINT_BY_TEXTURE.computeIfAbsent(textureId, InvincibleGlintRenderTypes::createBodyGlint);
    }

    private static RenderType createBodyGlint(int textureId) {
        // 恒用自定义双采样 shader（Sampler1 实体纹理 alpha 剔除——光影下也保留，
        // 输出目标由 GlintFrameBuffer 运行时决定：光影→自建 FBO 帧末合成，非光影→主 target）
        var shaderState    = new RenderStateShard.ShaderStateShard(() -> CaptureState.glintEntityShader);
        var textureState   = new RenderStateShard.TextureStateShard(INVINCIBLE_TEXTURE, true, false);
        var blendState     = RenderStateShard.GLINT_TRANSPARENCY;
        var depthState     = RenderStateShard.NO_DEPTH_TEST;
        var cullState      = RenderStateShard.CULL;
        var lightmapState  = RenderStateShard.NO_LIGHTMAP;
        var overlayState   = RenderStateShard.NO_OVERLAY;
        var layeringState  = RenderStateShard.VIEW_OFFSET_Z_LAYERING;
        var outputState    = com.plumejade.lensouls.ability.client.GlintFrameBuffer.OUTPUT_STATE;
        var texturingState = RenderStateShard.ENTITY_GLINT_TEXTURING;
        var writeState     = RenderStateShard.COLOR_WRITE;
        var colorLogicState = RenderStateShard.NO_COLOR_LOGIC;
        var lineState      = RenderStateShard.DEFAULT_LINE;

        return new RenderType(
                "lensouls_invincible_glint_" + textureId,
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                1536, false, false,
                () -> {
                    textureState.setupRenderState();
                    shaderState.setupRenderState();
                    // Sampler1 = 本层实体纹理（alpha==0 剔除透明面；反射失败白像素兜底）
                    RenderSystem.setShaderTexture(1, textureId);
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
                    RenderSystem.setShaderTexture(1, 0);
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
