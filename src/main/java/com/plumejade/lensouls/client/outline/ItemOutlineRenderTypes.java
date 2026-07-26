package com.plumejade.lensouls.client.outline;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * 镜魂描边遮罩 RenderType — 已废弃。
 * <p>
 * 镜魂描边系统已迁移到 {@link com.plumejade.lensouls.ability.client.SoulGlowLayer}。
 * 此类保留为占位壳，防止引用此类的代码崩溃。
 */
public final class ItemOutlineRenderTypes {

    private ItemOutlineRenderTypes() {}

    public static void setMaskShader(@Nullable ShaderInstance shader) {}

    @Nullable
    public static ShaderInstance getMaskShader() { return null; }

    public static RenderType itemMask() {
        // 返回一个合法的空 RenderType（不触发实际渲染）
        return RenderType.translucent();
    }
}
