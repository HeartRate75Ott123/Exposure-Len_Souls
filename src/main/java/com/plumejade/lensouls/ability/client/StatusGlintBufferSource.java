package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.plumejade.lensouls.boss.BossToughnessClientCache;
import com.plumejade.lensouls.boss.InvincibleGlintRenderTypes;
import com.plumejade.lensouls.boss.StunGlintRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 状态光效 BufferSource 包装器（顶点双写方案）。
 * <p>
 * 仿原版 {@code OutlineBufferSource}（光灵箭发光）：在 {@code getBuffer} 层把
 * 实体的每个顶点同时写入主渲染类型与状态光效类型，对任何渲染器（原版、
 * GeckoLib、多部件 BOSS 等）一视同仁，不依赖具体渲染器实现。
 * <p>
 * 由 {@link com.plumejade.lensouls.mixin.client.EntityRenderDispatcherMixin} 在
 * {@code EntityRenderDispatcher.render()} 中对 {@code EntityRenderer.render} 的参数
 * buffer 做替换。
 * <p>
 * 顺序保证：{@code MultiBufferSource.BufferSource} 在类型切换时立即 endBatch
 * （共享 buffer + lastSharedType），因此先取主类型、后取 glint 类型即保证
 * 主模型先绘制、glint 叠加其上。
 */
@OnlyIn(Dist.CLIENT)
public class StatusGlintBufferSource implements MultiBufferSource {

    /** 状态互斥顺序：破定 > 无敌 > 定格。 */
    public enum State {
        NONE, STUNNED, INVINCIBLE, FROZEN
    }

    private final MultiBufferSource delegate;
    private final State state;

    public StatusGlintBufferSource(MultiBufferSource delegate, State state) {
        this.delegate = delegate;
        this.state = state;
    }

    /** 解析实体状态，互斥顺序：破定 > 无敌 > 定格。 */
    public static State resolveState(Entity entity) {
        if (entity == null) return State.NONE;
        int id = entity.getId();
        if (BossToughnessClientCache.isStunned(id)) return State.STUNNED;
        if (BossToughnessClientCache.isInvincible(id)) return State.INVINCIBLE;
        if (ClientFreezeCache.isFrozen(id)) return State.FROZEN;
        return State.NONE;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (state == State.NONE) return delegate.getBuffer(type);
        if (type.isOutline()) return delegate.getBuffer(type);
        VertexFormat format = type.format();
        // 只双写实体模型格式（含眼睛/发光部位），跳过线条、方块等杂项
        if (format != DefaultVertexFormat.NEW_ENTITY
                && format != DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
            return delegate.getBuffer(type);
        }
        // 先取主类型：BufferSource 类型切换即 endBatch，先主后 glint = 先画主后画光效
        VertexConsumer main = delegate.getBuffer(type);
        VertexConsumer glint = delegate.getBuffer(glintType());
        if (state == State.FROZEN) {
            // 冰蓝描边 mask（独立 buffer，由 dispatcher RETURN 的 flushMask 提交）；
            // 手持物品用 alpha 测试类型，避免透明纹理区域产生方框
            RenderType maskType = ItemRenderTracker.isRenderingItem()
                    ? MaskRenderTypes.MASK_TYPE_ITEM : MaskRenderTypes.MASK_TYPE;
            VertexConsumer mask = CaptureState.getMaskBufferSource().getBuffer(maskType);
            // 必须嵌套 Double：GeckoLib 只重建 VertexMultiConsumer.Double，
            // Multiple（3+ consumer）不会被重建，flush 后写入已关闭 buffer 会崩溃
            return VertexMultiConsumer.create(VertexMultiConsumer.create(main, glint), mask);
        }
        return VertexMultiConsumer.create(main, glint);
    }

    private RenderType glintType() {
        return switch (state) {
            case STUNNED -> StunGlintRenderTypes.bodyGlint();
            case INVINCIBLE -> InvincibleGlintRenderTypes.bodyGlint();
            case FROZEN -> FrozenBlueGlintRenderTypes.bodyGlint();
            default -> throw new IllegalStateException("NONE 已在 getBuffer 提前返回");
        };
    }
}
