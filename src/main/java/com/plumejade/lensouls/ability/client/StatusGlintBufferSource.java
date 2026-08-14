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

    /** 解析实体状态，互斥顺序：破定 > 定格 > 无敌（冻结优先于白霸体）。 */
    public static State resolveState(Entity entity) {
        if (entity == null) return State.NONE;
        int id = entity.getId();
        if (BossToughnessClientCache.isStunned(id)) return State.STUNNED;
        if (ClientFreezeCache.isFrozen(id)) return State.FROZEN;
        if (BossToughnessClientCache.isInvincible(id)) return State.INVINCIBLE;
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
        if (state == State.FROZEN && format == DefaultVertexFormat.NEW_ENTITY) {
            // 冰蓝描边 mask（独立 buffer，由 dispatcher RETURN 的 flushMask 提交）。
            // 只双写实体模型格式：eyes 类发光层（POSITION_COLOR_TEX_LIGHTMAP）不进 mask
            // （原版光灵箭 outline 同样排除），避免异常坐标/颜色污染描边；
            // 手持物品用 alpha 测试类型，避免透明纹理区域产生方框
            RenderType maskType = ItemRenderTracker.isRenderingItem()
                    ? MaskRenderTypes.MASK_TYPE_ITEM : MaskRenderTypes.MASK_TYPE;
            // mask 顶点颜色强制白色（仿原版 EntityOutlineGenerator：丢弃 setColor 等），
            // sobel 边缘检测只对形状边缘响应
            VertexConsumer mask = new MaskColorConsumer(
                    CaptureState.getMaskBufferSource().getBuffer(maskType));
            // 必须嵌套 Double：GeckoLib 只重建 VertexMultiConsumer.Double，
            // Multiple（3+ consumer）不会被重建，flush 后写入已关闭 buffer 会崩溃
            return VertexMultiConsumer.create(VertexMultiConsumer.create(main, glint), mask);
        }
        return VertexMultiConsumer.create(main, glint);
    }

    private RenderType glintType() {
        // 手持物品：物品模型是方块网格，纹理含透明像素——
        // 必须用带图集 alpha 测试的类型，否则透明区域也铺上光效形成完整方片
        if (ItemRenderTracker.isRenderingItem()) {
            return StatusGlintItemRenderTypes.itemGlint(state);
        }
        return switch (state) {
            case STUNNED -> StunGlintRenderTypes.bodyGlint();
            case INVINCIBLE -> InvincibleGlintRenderTypes.bodyGlint();
            case FROZEN -> FrozenBlueGlintRenderTypes.bodyGlint();
            default -> throw new IllegalStateException("NONE 已在 getBuffer 提前返回");
        };
    }

    /**
     * mask 顶点颜色强制白色（仿原版 {@code EntityOutlineGenerator}）：
     * addVertex 时显式置白、丢弃 setColor，保证 mask 内容 alpha 恒为 255，
     * Sobel 边缘检测只响应形状边缘。
     * <p>
     * 注意：mask 是 {@code NEW_ENTITY} 格式，与原版 outline 的
     * {@code POSITION_TEX_COLOR} 不同——setUv1/setUv2/setNormal 必须转发，
     * 否则顶点元素缺失会在下一个 addVertex 时抛
     * {@code Missing elements in vertex: UV1, UV2, Normal}。
     */
    private static final class MaskColorConsumer implements VertexConsumer {

        private final VertexConsumer delegate;

        MaskColorConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            CaptureState.recordMaskVertex(x, y, z);
            this.delegate.addVertex(x, y, z).setColor(255, 255, 255, 255);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            this.delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }
}
