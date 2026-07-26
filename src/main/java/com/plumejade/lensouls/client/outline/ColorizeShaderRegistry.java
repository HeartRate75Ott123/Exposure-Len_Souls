package com.plumejade.lensouls.client.outline;

import net.minecraft.client.renderer.ShaderInstance;

/**
 * 实体颜色化着色器持有者。
 * <p>
 * 由 {@link com.plumejade.lensouls.LenSoulsClient#registerShaders} 注册，
 * 供 {@link com.plumejade.lensouls.ability.client.WireframeRenderTypes} 使用。
 */
public class ColorizeShaderRegistry {
    public static ShaderInstance shader;
}
