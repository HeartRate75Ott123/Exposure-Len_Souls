package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * 全屏四边形 composite 专用的 RenderType — Iris 兼容。
 * <p>
 * 替换 {@code Tesselator} 裸渲染，使 composite 经过 {@code MultiBufferSource} 管线，
 * Iris 可以拦截并正确分阶段处理。
 */
public class CompositeRenderTypes extends RenderStateShard {

    private static final ShaderStateShard GOLD_OUTLINE_SHADER =
            new ShaderStateShard(() -> FrozenOutlineManager.goldOutlineShader);

    private static final DepthTestStateShard NO_DEPTH_TEST_STATE;

    static {
        try {
            var ctor = DepthTestStateShard.class.getDeclaredConstructor(String.class, int.class);
            ctor.setAccessible(true);
            NO_DEPTH_TEST_STATE = ctor.newInstance("lensouls_always", 519); // GL_ALWAYS
        } catch (Exception e) {
            throw new RuntimeException("Failed to create NO_DEPTH_TEST StateShard", e);
        }
    }

    /** 全屏四边形 — 冻结描边用（goldOutlineShader，Sobel） */
    public static final RenderType MAIN_QUAD = RenderType.create(
            "lensouls_composite_quad",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(GOLD_OUTLINE_SHADER)
                    .setDepthTestState(NO_DEPTH_TEST_STATE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(MAIN_TARGET)
                    .createCompositeState(false)
    );

    public CompositeRenderTypes() {
        super("", () -> {}, () -> {});
    }
}
