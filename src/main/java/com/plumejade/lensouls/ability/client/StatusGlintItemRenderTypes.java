package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.EnumMap;

/**
 * 手持物品状态光效 RenderType 工厂（破定/无敌/冻结的 glint 物品版）。
 * <p>
 * 物品模型是方块网格，纹理含透明像素——直接复用身体 glint 类型会把透明区域也铺上
 * 光效，形成完整方片。本类型额外采样物品图集（Sampler1）做 alpha 测试，
 * 透明像素 discard，光效只出现在实体纹理区域。
 */
public class StatusGlintItemRenderTypes {

    public static ShaderInstance itemGlintShader;

    private static final ResourceLocation STUN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/misc/stun_glint_entity.png");
    private static final ResourceLocation INVINCIBLE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/misc/invincible_glint_entity.png");
    private static final ResourceLocation FROZEN_BLUE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/misc/enchanted_glint_entity.png");

    private static final EnumMap<StatusGlintBufferSource.State, RenderType> CACHE =
            new EnumMap<>(StatusGlintBufferSource.State.class);

    public static RenderType itemGlint(StatusGlintBufferSource.State state) {
        return CACHE.computeIfAbsent(state, StatusGlintItemRenderTypes::create);
    }

    private static RenderType create(StatusGlintBufferSource.State state) {
        ResourceLocation glintTexture = switch (state) {
            case STUNNED -> STUN_TEXTURE;
            case INVINCIBLE -> INVINCIBLE_TEXTURE;
            case FROZEN -> FROZEN_BLUE_TEXTURE;
            default -> throw new IllegalStateException("NONE 不应进入物品 glint");
        };

        var shaderState   = new RenderStateShard.ShaderStateShard(() -> itemGlintShader);
        var textureState  = new RenderStateShard.TextureStateShard(glintTexture, true, false);
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
                "lensouls_status_glint_item",
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                1536, false, false,
                () -> {
                    textureState.setupRenderState();
                    // Sampler1 = 物品图集：与物品模型 UV 同源，alpha 测试剔除透明像素
                    RenderSystem.setShaderTexture(1,
                            Minecraft.getInstance().getTextureManager()
                                    .getTexture(InventoryMenu.BLOCK_ATLAS).getId());
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
                    RenderSystem.setShaderTexture(1, 0);
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

    private StatusGlintItemRenderTypes() {}
}
