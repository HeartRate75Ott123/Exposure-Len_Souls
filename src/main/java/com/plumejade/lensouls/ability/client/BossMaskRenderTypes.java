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
 * BOSS 镜魂专用的 mask RenderType — OutputStateShard 绑定 {@link BossOutlineManager} 的独立 mask FBO。
 * <p>
 * 与 {@link MaskRenderTypes}（冻结描边 FBO）分离，避免 mask 写错目标。
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

    /** 实体 mask — 无纹理纯白 */
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
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false)
    );

    /** 物品 mask — 带纹理 alpha 测试，避免透明区域方框 */
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
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false)
    );

    // ---- per-纹理 BOSS mask（每层绑定自己的实体纹理，alpha==0 剔除） ----

    private static final Map<Integer, RenderType> BOSS_MASK_BY_TEXTURE = new HashMap<>();
    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/unknown.png");

    private static final class TextureIdState extends TextureStateShard {
        private final int textureId;
        TextureIdState(int textureId) {
            super(DUMMY_TEXTURE, false, false);
            this.textureId = textureId;
        }
        @Override
        public void setupRenderState() { RenderSystem.setShaderTexture(0, textureId); }
        @Override
        public void clearRenderState() { RenderSystem.setShaderTexture(0, 0); }
    }

    public static RenderType maskTypeForEntity(int textureId) {
        return BOSS_MASK_BY_TEXTURE.computeIfAbsent(textureId, id -> {
            TextureStateShard textureState = new TextureIdState(id);
            return RenderType.create(
                    "lensouls_boss_mask_entity_" + id,
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS, 256, true, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(MASK_SHADER)
                            .setTextureState(textureState)
                            .setOutputState(BOSS_MASK_OUTPUT)
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .setDepthTestState(NO_DEPTH_TEST)
                            .createCompositeState(false)
            );
        });
    }

    public BossMaskRenderTypes() {
        super("", () -> {}, () -> {});
    }
}
