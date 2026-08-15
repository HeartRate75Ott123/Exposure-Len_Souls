package com.plumejade.lensouls.ability.client;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;

/**
 * 状态光效（glint）顶点收集器。
 * <p>
 * 收集阶段：{@link StatusGlintBufferSource#getBuffer} 把 glint 顶点写入独立
 * BufferSource（不进入 Iris 批次——Iris 光影下自定义 shader 批次双写不渲染）。
 * <p>
 * 绘制阶段：帧末由 {@link com.plumejade.lensouls.mixin.client.GameRendererFrameEndMixin}
 * 恢复相机透视投影 + identity modelView（顶点已是相机空间）后 endBatch——
 * 与描边合成同款"直接绘制"模式（已验证在 Iris 光影下正常）。
 * <p>
 * 每帧 endBatch 一次即清空缓冲，无跨帧残留。
 */
public class GlintVertexCollector {

    private static MultiBufferSource.BufferSource glintBufferSource;

    private GlintVertexCollector() {
    }

    /** 收集入口（实体渲染时写入 glint 顶点）。 */
    public static MultiBufferSource.BufferSource getBufferSource() {
        if (glintBufferSource == null) {
            glintBufferSource = new RenderBuffers(256).bufferSource();
        }
        return glintBufferSource;
    }

    /** 帧末提交 glint 顶点（调用方需已设置相机投影 + identity modelView）。 */
    public static void flush() {
        if (glintBufferSource != null) {
            glintBufferSource.endBatch();
        }
    }
}
