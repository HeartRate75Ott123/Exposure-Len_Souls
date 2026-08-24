package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 玩家 BOSS 镜魂描边 BufferSource 包装器（与时间定格描边同构，dispatcher 层顶点双写）。
 * <p>
 * 仿 {@link StatusGlintBufferSource}：在 {@code getBuffer} 层把实体的每个顶点同时写入
 * 主渲染类型与 BOSS mask（收集到 {@link CaptureState} 内存，帧末统一提交到
 * {@link BossOutlineManager} FBO）。对任何渲染器（原版、GeckoLib、多部件）一视同仁。
 * <p>
 * 由 {@code EntityRenderDispatcherMixin} 对「玩家 + Boss 镜魂 effect」的实体替换 buffer 参数。
 */
@OnlyIn(Dist.CLIENT)
public class BossOutlineBufferSource implements MultiBufferSource {

    private final MultiBufferSource delegate;

    public BossOutlineBufferSource(MultiBufferSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (!BossOutlineManager.isCapturing()) return delegate.getBuffer(type);
        if (type.isOutline()) return delegate.getBuffer(type);
        VertexFormat format = type.format();
        // 只双写实体模型格式，跳过线条、方块等杂项
        if (format != DefaultVertexFormat.NEW_ENTITY) return delegate.getBuffer(type);

        int textureId = CaptureState.entityTextureId(type);
        VertexConsumer main = delegate.getBuffer(type);
        RenderType maskType = ItemRenderTracker.isRenderingItem()
                ? BossMaskRenderTypes.MASK_TYPE_ITEM : BossMaskRenderTypes.maskTypeForEntity(textureId);
        VertexConsumer mask = new CaptureState.MaskColorConsumer(maskType);
        return VertexMultiConsumer.create(main, mask);
    }
}
