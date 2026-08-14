package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * BOSS 镜魂发光 RenderType 工厂。
 * <p>
 * 物品使用 {@code NEW_ENTITY} 格式（与 {@code ItemRenderer.renderModelLists} 兼容）。
 * 实体使用 {@code POSITION_TEX} 格式（与 {@code FrozenBlueGlintRenderTypes} 一致，
 * {@code EntityModel.renderToBuffer()} 会忽略多余元素）。
 */
public class SoulGlowRenderTypes {

    private static final RenderStateShard.ShaderStateShard GLOW_SHADER =
            new RenderStateShard.ShaderStateShard(SoulGlowShader::getShader);

    private static final RenderStateShard.TransparencyStateShard TRANS =
            RenderStateShard.TRANSLUCENT_TRANSPARENCY;

    // === 物品 RenderType（NEW_ENTITY 格式） ===

    private static RenderType itemSurface;
    private static RenderType itemAura;

    public static RenderType itemSurface() {
        if (itemSurface == null) itemSurface = createItem("lensouls_soul_surface", RenderStateShard.NO_DEPTH_TEST);
        return itemSurface;
    }

    public static RenderType itemAura() {
        if (itemAura == null) itemAura = createItem("lensouls_soul_aura", RenderStateShard.LEQUAL_DEPTH_TEST);
        return itemAura;
    }

    // === 实体发光 RenderType（POSITION_TEX 格式，仿 FrozenBlueGlintRenderTypes） ===

    private static RenderType entityBossGlow;

    public static RenderType entityBossGlow() {
        if (entityBossGlow == null) entityBossGlow = createEntity();
        return entityBossGlow;
    }

    // === 工厂 ===

    private static RenderType createItem(String name, RenderStateShard.DepthTestStateShard depth) {
        var state = RenderType.CompositeState.builder()
                .setShaderState(GLOW_SHADER)
                .setTransparencyState(TRANS)
                .setCullState(RenderStateShard.NO_CULL)
                .setDepthTestState(depth)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setOverlayState(RenderStateShard.NO_OVERLAY)
                .createCompositeState(false);
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, false, true, state);
    }

    private static RenderType createEntity() {
        var state = RenderType.CompositeState.builder()
                .setShaderState(GLOW_SHADER)
                .setTransparencyState(TRANS)
                .setCullState(RenderStateShard.NO_CULL)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setOverlayState(RenderStateShard.NO_OVERLAY)
                .setTextureState(RenderStateShard.NO_TEXTURE)
                .setOutputState(RenderStateShard.MAIN_TARGET)
                .createCompositeState(false);
        return RenderType.create("lensouls_soul_entity_glow",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1536, false, false, state);
    }

    private SoulGlowRenderTypes() {}
}
