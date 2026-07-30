package com.plumejade.lensouls.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 幻灵专用 RenderType 管线。
 * <p>
 * 基于 entityTranslucent 的半透明管线，用于 BufferSourceGetBufferMixin
 * 在幻灵渲染时替换原始 RenderType，实现半透明+元素色叠加效果。
 */
public class PhantomRenderType {

    /**
     * 获取幻灵专用渲染类型。
     * 基于 entityTranslucent，确保 alpha 混合和深度测试正确。
     *
     * @param texture      幻灵纹理
     * @param elementColor 元素色 ARGB（暂未直接使用，颜色由 PhantomVertexConsumer 处理）
     */
    public static RenderType getPhantomRenderType(ResourceLocation texture, int elementColor) {
        return RenderType.entityTranslucent(texture);
    }
}
