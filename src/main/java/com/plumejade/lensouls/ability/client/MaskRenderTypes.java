package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * 蒙版渲染专用的 RenderType 工厂 — AdorableArmory 模式。
 * <p>
 * 使用 OutputStateShard 把渲染输出导向 mask FBO。
 * 提供两种蒙版：
 * - {@link #MASK_TYPE}：无纹理纯白（实体身体/盔甲用）
 * - {@link #MASK_TYPE_ITEM}：带纹理 alpha 测试（手持物用，避免透明区域方框）
 */
public class MaskRenderTypes extends RenderStateShard {

    // 实体蒙版着色器（纯白，无纹理）
    private static final ShaderStateShard MASK_SHADER =
            new ShaderStateShard(() -> FrozenOutlineManager.maskShader);

    // 物品蒙版着色器（带纹理 alpha 测试，避免方框）
    private static final ShaderStateShard MASK_ITEM_SHADER =
            new ShaderStateShard(() -> FrozenOutlineManager.itemMaskShader);

    // OutputStateShard：通过 FrozenOutlineManager 绑定/恢复
    private static final OutputStateShard MASK_OUTPUT = new OutputStateShard(
            "lensouls_mask_output",
            FrozenOutlineManager::bindMaskTarget,
            FrozenOutlineManager::restoreMainTarget
    );

    // ---- 实体蒙版（无纹理，纯白） ----
    public static final RenderType MASK_TYPE = RenderType.create(
            "lensouls_mask_entity",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(MASK_SHADER)
                    .setOutputState(MASK_OUTPUT)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false)
    );

    // ---- 物品蒙版（带纹理 alpha 测试，避免透明区域方框） ----
    public static final RenderType MASK_TYPE_ITEM = RenderType.create(
            "lensouls_mask_item",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(MASK_ITEM_SHADER)
                    .setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                    .setOutputState(MASK_OUTPUT)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false)
    );

    public MaskRenderTypes() {
        super("", () -> {}, () -> {});
    }
}
