package com.plumejade.lensouls.client.phantom;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.plumejade.lensouls.ability.client.CaptureState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 虚灵半透明 BufferSource 包装器（dispatcher 层，仿 glint 同构）。
 * <p>
 * 把实体模型的每个 RenderType 换成 {@code entityTranslucent}（原纹理），
 * 并用 {@link PhantomAlphaConsumer} 改写顶点 alpha——只改 alpha、保留实体原色。
 * <p>
 * 覆盖所有渲染层（身体/盔甲/持物）与多部件/GeckoLib 实体（经 dispatcher 层统一包装）。
 * <p>
 * 两个用途共用本类：
 * <ul>
 *   <li>幻灵（虚影 BOSS）：固定 {@link #PHANTOM_ALPHA}（0.5）；</li>
 *   <li>幻术师照片的幻影幻翼：alpha 逐 tick 变化，见
 *       {@link com.plumejade.lensouls.entity.SwarmPhantomFade}——走带 alpha 的第二个构造器。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class PhantomBufferSource implements MultiBufferSource {

    /** 幻灵固定不透明度 0.5 */
    public static final float PHANTOM_ALPHA = 0.5f;

    private final MultiBufferSource delegate;
    /** 顶点 alpha，0~1 */
    private final float alpha;

    public PhantomBufferSource(MultiBufferSource delegate) {
        this(delegate, PHANTOM_ALPHA);
    }

    public PhantomBufferSource(MultiBufferSource delegate, float alpha) {
        this.delegate = delegate;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (type.isOutline()) return delegate.getBuffer(type);
        VertexFormat format = type.format();
        // 只透明实体模型格式（含眼睛/发光部位）
        if (format != DefaultVertexFormat.NEW_ENTITY
                && format != DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
            return delegate.getBuffer(type);
        }
        ResourceLocation tex = CaptureState.entityTextureLocation(type);
        if (tex == null) return delegate.getBuffer(type);
        VertexConsumer base = delegate.getBuffer(RenderType.entityTranslucent(tex));
        return new PhantomAlphaConsumer(base, alpha);
    }
}
