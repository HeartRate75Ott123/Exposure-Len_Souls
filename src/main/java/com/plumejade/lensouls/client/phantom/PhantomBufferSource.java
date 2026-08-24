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
 * 幻灵实体渲染时，把实体模型的每个 RenderType 换成 {@code entityTranslucent}（原纹理），
 * 并用 {@link PhantomAlphaConsumer} 固定 0.5 不透明度——只改 alpha、保留实体原色。
 * <p>
 * 覆盖所有渲染层（身体/盔甲/持物）与多部件/GeckoLib 实体（经 dispatcher 层统一包装）。
 */
@OnlyIn(Dist.CLIENT)
public class PhantomBufferSource implements MultiBufferSource {

    /** 不透明度 0.5 */
    public static final float PHANTOM_ALPHA = 0.5f;

    private final MultiBufferSource delegate;

    public PhantomBufferSource(MultiBufferSource delegate) {
        this.delegate = delegate;
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
        return new PhantomAlphaConsumer(base, PHANTOM_ALPHA);
    }
}
