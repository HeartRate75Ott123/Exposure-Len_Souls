package com.plumejade.lensouls.client.phantom;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 虚灵 alpha 注入 VertexConsumer 包装器。
 * <p>
 * 保留实体原色（RGB 不变），仅把 alpha 固定为幻灵不透明度（0.5）。
 * 兼容模型丢弃 color 参数/仅 setWhiteAlpha 的场景。
 */
public record PhantomAlphaConsumer(
        VertexConsumer delegate,
        float alpha          // 0.0~1.0
) implements VertexConsumer {

    private int alpha255() {
        return (int) (alpha * 255f);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        delegate.setColor(r, g, b, alpha255());
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        delegate.setColor(r, g, b, alpha255());
        return this;
    }

    @Override
    public VertexConsumer setColor(float r, float g, float b, float a) {
        delegate.setColor(r, g, b, alpha);
        return this;
    }

    @Override
    public VertexConsumer setWhiteAlpha(int a) {
        delegate.setWhiteAlpha(alpha255());
        return this;
    }

    @Override
    public VertexConsumer setLight(int packedLight) {
        delegate.setLight(packedLight);
        return this;
    }

    @Override
    public VertexConsumer setOverlay(int packedOverlay) {
        delegate.setOverlay(packedOverlay);
        return this;
    }
}
