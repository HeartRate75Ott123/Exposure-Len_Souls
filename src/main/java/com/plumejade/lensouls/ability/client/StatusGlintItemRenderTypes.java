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
 * 鎵嬫寔鐗╁搧鐘舵€佸厜鏁?RenderType 宸ュ巶锛堢牬瀹?鏃犳晫/鍐荤粨鐨?glint 鐗╁搧鐗堬級銆? * <p>
 * 鐗╁搧妯″瀷鏄柟鍧楃綉鏍硷紝绾圭悊鍚€忔槑鍍忕礌鈥斺€旂洿鎺ュ鐢ㄨ韩浣?glint 绫诲瀷浼氭妸閫忔槑鍖哄煙涔熼摵涓? * 鍏夋晥锛屽舰鎴愬畬鏁存柟鐗囥€傛湰绫诲瀷棰濆閲囨牱鐗╁搧鍥鹃泦锛圫ampler1锛夊仛 alpha 娴嬭瘯锛? * 閫忔槑鍍忕礌 discard锛屽厜鏁堝彧鍑虹幇鍦ㄥ疄浣撶汗鐞嗗尯鍩熴€? */
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
            default -> throw new IllegalStateException("NONE 涓嶅簲杩涘叆鐗╁搧 glint");
        };

        var shaderState   = new RenderStateShard.ShaderStateShard(() -> com.plumejade.lensouls.integration.IrisCompat.isShadersActive() ? net.minecraft.client.renderer.GameRenderer.getRendertypeGlintShader() : itemGlintShader);
        var textureState  = new RenderStateShard.TextureStateShard(glintTexture, true, false);
        var blendState    = RenderStateShard.GLINT_TRANSPARENCY;
        var depthState    = RenderStateShard.NO_DEPTH_TEST;
        var cullState     = RenderStateShard.CULL;
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
                    // Sampler1 = 鐗╁搧鍥鹃泦锛氫笌鐗╁搧妯″瀷 UV 鍚屾簮锛宎lpha 娴嬭瘯鍓旈櫎閫忔槑鍍忕礌
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
