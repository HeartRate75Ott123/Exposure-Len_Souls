package com.plumejade.lensouls.client.itemoutline;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * 第一人称手持物描边专属全屏四边形 RenderType（Iris 兼容，经 {@code MultiBufferSource} 管线）。
 * 使用 {@link ItemOutlineShaders#itemCompositeShader} 与无深度测试，直接叠加到主目标。
 */
public class ItemOutlineRenderTypes extends RenderStateShard {

    private static final ShaderStateShard ITEM_OUTLINE_SHADER =
            new ShaderStateShard(() -> ItemOutlineShaders.itemCompositeShader);

    private static final DepthTestStateShard NO_DEPTH_TEST_STATE;

    static {
        try {
            var ctor = DepthTestStateShard.class.getDeclaredConstructor(String.class, int.class);
            ctor.setAccessible(true);
            NO_DEPTH_TEST_STATE = ctor.newInstance("lensouls_item_outline_always", 519); // GL_ALWAYS
        } catch (Exception e) {
            throw new RuntimeException("Failed to create NO_DEPTH_TEST StateShard", e);
        }
    }

    public static final RenderType ITEM_OUTLINE_QUAD = RenderType.create(
            "lensouls_item_outline_quad",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(ITEM_OUTLINE_SHADER)
                    .setDepthTestState(NO_DEPTH_TEST_STATE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(MAIN_TARGET)
                    .createCompositeState(false)
    );

    public ItemOutlineRenderTypes() {
        super("", () -> {}, () -> {});
    }
}
