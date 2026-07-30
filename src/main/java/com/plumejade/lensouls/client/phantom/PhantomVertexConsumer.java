package com.plumejade.lensouls.client.phantom;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 幻灵 VertexConsumer 包装器。
 * <p>
 * 拦截模型对 VertexConsumer.setColor() 的调用，
 * 将所有顶点颜色替换为元素色 × 幻灵透明度（alpha）。
 * <p>
 * 模型调用 addVertex(pose, x, y, z).setColor(r,g,b,a).setUv().setNormal() 链式调用，
 * 颜色通过 setColor() 拦截替换。
 * <p>
 * 适配 1.21.1 VertexConsumer API（addVertex/setColor 命名风格）。
 */
public record PhantomVertexConsumer(
        VertexConsumer delegate,
        int elementR, int elementG, int elementB,
        int alpha       // 0-255
) implements VertexConsumer {

    public PhantomVertexConsumer(VertexConsumer delegate, int elementColor, int alpha) {
        this(delegate,
                (elementColor >> 16) & 0xFF,
                (elementColor >> 8) & 0xFF,
                elementColor & 0xFF,
                alpha);
    }

    // ========== 抽象方法（必须实现） ==========

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        // 替换为元素色 + 幻灵 alpha
        delegate.setColor(elementR, elementG, elementB, alpha);
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

    // ========== 默认方法覆盖（颜色相关） ==========

    @Override
    public VertexConsumer setColor(int argb) {
        // 覆盖默认实现（不再拆包，直接替换）
        delegate.setColor(elementR, elementG, elementB, alpha);
        return this;
    }

    @Override
    public VertexConsumer setColor(float r, float g, float b, float a) {
        delegate.setColor(elementR / 255f, elementG / 255f, elementB / 255f, alpha / 255f);
        return this;
    }

    @Override
    public VertexConsumer setWhiteAlpha(int a) {
        delegate.setColor(elementR, elementG, elementB, alpha);
        return this;
    }

    // ========== 其他默认方法透传 ==========

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
