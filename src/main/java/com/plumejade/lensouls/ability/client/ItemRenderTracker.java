package com.plumejade.lensouls.ability.client;

/**
 * 手持物品渲染追踪。
 * <p>
 * 在 {@link net.minecraft.client.renderer.entity.layers.ItemInHandLayer#render}
 * HEAD/RETURN 中设置/清除标记，
 * {@link com.plumejade.lensouls.mixin.client.BufferSourceGetBufferMixin} 据此
 * 跳过物品渲染的蒙版写入，避免透明纹理区域产生方框边缘。
 */
public class ItemRenderTracker {

    private static final ThreadLocal<Boolean> RENDERING_ITEM = ThreadLocal.withInitial(() -> false);

    public static void beginItemRender() {
        RENDERING_ITEM.set(true);
    }

    public static boolean isRenderingItem() {
        return RENDERING_ITEM.get();
    }

    public static void endItemRender() {
        RENDERING_ITEM.set(false);
    }
}
