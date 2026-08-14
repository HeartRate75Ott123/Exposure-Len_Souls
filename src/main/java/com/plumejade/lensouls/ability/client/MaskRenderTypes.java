package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.HashMap;
import java.util.Map;

/**
 * 蒙版渲染专用的 RenderType 工厂 — AdorableArmory 模式。
 * <p>
 * 使用 OutputStateShard 把渲染输出导向 mask FBO。
 * 提供两种蒙版：
 * - {@link #MASK_TYPE}：无纹理纯白（实体身体/盔甲用）
 * - {@link #MASK_TYPE_ITEM}：带纹理 alpha 测试（手持物用，避免透明区域方框）
 * - {@link #maskTypeForEntity(int)}：per-纹理类型（每层绑定自己的实体纹理，
 *   mask shader 据此做 alpha==0 剔除——原版 OutlineBufferSource 同款：类型切换即
 *   flush，多纹理实体每层用自己纹理，互不误滤）
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
                    .setCullState(CULL)
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
                    .setCullState(CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false)
    );

    // ---- per-纹理实体蒙版（每层绑定自己的实体纹理，alpha==0 剔除） ----
    private static final Map<Integer, RenderType> MASK_TYPE_BY_TEXTURE = new HashMap<>();

    /** 占位纹理（仅满足 TextureStateShard 构造的 requireNonNull，setup 被覆写不走父类）。 */
    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/unknown.png");

    /** 覆写 setup/clear 直接绑定 GL 纹理句柄，不经过父类的资源加载。 */
    private static final class TextureIdState extends TextureStateShard {
        private final int textureId;

        TextureIdState(int textureId) {
            super(DUMMY_TEXTURE, false, false);
            this.textureId = textureId;
        }

        @Override
        public void setupRenderState() {
            RenderSystem.setShaderTexture(0, textureId);
        }

        @Override
        public void clearRenderState() {
            RenderSystem.setShaderTexture(0, 0);
        }
    }

    public static RenderType maskTypeForEntity(int textureId) {
        return MASK_TYPE_BY_TEXTURE.computeIfAbsent(textureId, id -> {
            TextureStateShard textureState = new TextureIdState(id);
            return RenderType.create(
                    "lensouls_mask_entity_" + id,
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 256, true, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(MASK_SHADER)
                            .setTextureState(textureState)
                            .setOutputState(MASK_OUTPUT)
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setWriteMaskState(COLOR_DEPTH_WRITE)
                            .setCullState(CULL)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .createCompositeState(false)
            );
        });
    }

    public MaskRenderTypes() {
        super("", () -> {}, () -> {});
    }
}
