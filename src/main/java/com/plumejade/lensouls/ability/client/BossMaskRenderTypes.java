package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * BOSS 镜魂专用的 mask RenderType — OutputStateShard 绑定 {@link BossOutlineManager} 的独立 mask FBO。
 */
public class BossMaskRenderTypes extends RenderStateShard {

    private static final ShaderStateShard MASK_SHADER =
            new ShaderStateShard(() -> FrozenOutlineManager.maskShader);

    private static final ShaderStateShard MASK_ITEM_SHADER =
            new ShaderStateShard(() -> FrozenOutlineManager.itemMaskShader);

    private static final OutputStateShard BOSS_MASK_OUTPUT = new OutputStateShard(
            "lensouls_boss_mask_output",
            BossOutlineManager::bindMaskTarget,
            BossOutlineManager::restoreMainTarget
    );

    /** LESS 深度测试（反射创建）— 正反面深度相同时只通过最先渲染的那面，消除薄物品 Z-fighting */
    private static final DepthTestStateShard LESS_DEPTH_TEST;
    static {
        try {
            var ctor = DepthTestStateShard.class.getDeclaredConstructor(String.class, int.class);
            ctor.setAccessible(true);
            LESS_DEPTH_TEST = ctor.newInstance("lensouls_less", 513); // GL_LESS
        } catch (Exception e) {
            throw new RuntimeException("Failed to create LESS_DEPTH_TEST", e);
        }
    }

    /** 身体 mask — LESS 深度测试，消除薄模型正反面 Z-fighting */
    public static final RenderType MASK_TYPE = RenderType.create(
            "lensouls_boss_mask_entity",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(MASK_SHADER)
                    .setOutputState(BOSS_MASK_OUTPUT)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LESS_DEPTH_TEST)
                    .createCompositeState(false)
    );

    /** 物品 mask — alpha 抠图 + CULL（剔除背面），防止 crossed-quad 工具两 quad 交叉处 Z-fighting */
    public static final RenderType MASK_TYPE_ITEM = RenderType.create(
            "lensouls_boss_mask_item",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(MASK_ITEM_SHADER)
                    .setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                    .setOutputState(BOSS_MASK_OUTPUT)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LESS_DEPTH_TEST)
                    .createCompositeState(false)
    );

    public BossMaskRenderTypes() {
        super("", () -> {}, () -> {});
    }
}
