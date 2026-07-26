package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * RenderType 工厂：引力枪牵引磁力闪电弧。
 * <p>
 * 使用原版 {@code rendertype_lightning} 着色器（Iris 原生兼容），
 * 闪电纹理在后端 CPU 预计算后编码到顶点颜色中。
 */
public class GravityTetherRenderTypes {

    /** 使用原版闪电着色器，Iris 原生兼容 */
    public static final RenderStateShard.ShaderStateShard MAGNETIC_LIGHTNING_SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLightningShader);

    // ── 闪电弧主体（TRIANGLE_STRIP） ──

    private static volatile RenderType cachedArc;

    public static RenderType lightningArc() {
        RenderType rt = cachedArc;
        if (rt == null) {
            synchronized (GravityTetherRenderTypes.class) {
                rt = cachedArc;
                if (rt == null) {
                    rt = createArc();
                    cachedArc = rt;
                }
            }
        }
        return rt;
    }

    private static RenderType createArc() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(MAGNETIC_LIGHTNING_SHADER)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setOutputState(RenderStateShard.MAIN_TARGET)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .createCompositeState(false);

        return RenderType.create(
                "lensouls_magnetic_lightning_arc",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.TRIANGLE_STRIP,
                512, false, false, state
        );
    }
}
