package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.lang.reflect.Constructor;
import java.util.OptionalDouble;

/**
 * RenderType 工厂：空间扭曲线框球体。
 * <p>
 * 实体冻结金边外描边已迁移至 {@link EntityOutlineRenderType}。
 */
public class WireframeRenderTypes {

    private static final RenderStateShard.ShaderStateShard RENDERTYPE_LINES_SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLinesShader);

    // 反射构造 LineStateShard
    private static final RenderStateShard.LineStateShard LINE_STATE;
    private static final RenderStateShard.LayeringStateShard VIEW_OFFSET_Z_LAYERING;

    static {
        try {
            Constructor<RenderStateShard.LineStateShard> lineCtor =
                    RenderStateShard.LineStateShard.class.getDeclaredConstructor(OptionalDouble.class);
            lineCtor.setAccessible(true);
            LINE_STATE = lineCtor.newInstance(OptionalDouble.of(2.0));

            var layeringField = RenderStateShard.class.getDeclaredField("VIEW_OFFSET_Z_LAYERING");
            layeringField.setAccessible(true);
            VIEW_OFFSET_Z_LAYERING = (RenderStateShard.LayeringStateShard) layeringField.get(null);
        } catch (Exception e) {
            throw new RuntimeException("[WireframeRenderTypes] 反射初始化失败", e);
        }
    }

    // ── 线框球体 ──

    private static volatile RenderType cachedMain;

    public static RenderType sphereOutline() {
        RenderType rt = cachedMain;
        if (rt == null) {
            synchronized (WireframeRenderTypes.class) {
                rt = cachedMain;
                if (rt == null) {
                    rt = create();
                    cachedMain = rt;
                }
            }
        }
        return rt;
    }

    private static RenderType create() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_LINES_SHADER)
                .setLineState(LINE_STATE)
                .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setOutputState(RenderStateShard.MAIN_TARGET)
                .createCompositeState(false);

        return RenderType.create(
                "lensouls_lines_main",
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,
                1024, false, false, state
        );
    }
}
