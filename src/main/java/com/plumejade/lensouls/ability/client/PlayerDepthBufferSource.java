package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 玩家几何深度双写（黑白滤镜的玩家豁免基准）。
 * <p>
 * 本地玩家（第三人称实体渲染 / 第一人称手部）渲染时，每个 {@code NEW_ENTITY}
 * 顶点同时写入主目标与 {@link BlackWhitePost#PLAYER_DEPTH_TYPE}（输出玩家深度
 * FBO）。帧末由 {@link #flushDepthSource()} 兜底提交。
 * <p>
 * 嵌套安全：只包一层 {@code VertexMultiConsumer.Double}（主 + 深度），
 * 魂光等上层再包 Multiple 时与现状等价，GeckoLib 不重建的问题不受影响。
 */
@OnlyIn(Dist.CLIENT)
public class PlayerDepthBufferSource implements MultiBufferSource {

    private static final MultiBufferSource.BufferSource DEPTH_SOURCE =
            new RenderBuffers(256).bufferSource();

    private final MultiBufferSource delegate;

    public PlayerDepthBufferSource(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    /** 帧末兜底提交玩家深度顶点（层切换已大部分提交）。 */
    public static void flushDepthSource() {
        DEPTH_SOURCE.endBatch();
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (type.format() != DefaultVertexFormat.NEW_ENTITY) {
            return delegate.getBuffer(type);
        }
        // 先取主类型：BufferSource 类型切换即 endBatch，主模型先画、深度后画无碍
        return VertexMultiConsumer.create(
                delegate.getBuffer(type),
                DEPTH_SOURCE.getBuffer(BlackWhitePost.PLAYER_DEPTH_TYPE));
    }
}