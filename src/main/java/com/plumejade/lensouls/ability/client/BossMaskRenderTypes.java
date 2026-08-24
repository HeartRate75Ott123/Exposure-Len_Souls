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
 * BOSS 闀滈瓊涓撶敤鐨?mask RenderType 鈥?OutputStateShard 缁戝畾 {@link BossOutlineManager} 鐨勭嫭绔?mask FBO銆? * <p>
 * 涓?{@link MaskRenderTypes}锛堝喕缁撴弿杈?FBO锛夊垎绂伙紝閬垮厤 mask 鍐欓敊鐩爣銆? */
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

    /** 瀹炰綋 mask 鈥?鏃犵汗鐞嗙函鐧?*/
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

    /** 鐗╁搧 mask 鈥?甯︾汗鐞?alpha 娴嬭瘯锛岄伩鍏嶉€忔槑鍖哄煙鏂规 */
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

    // ---- per-绾圭悊 BOSS mask锛堟瘡灞傜粦瀹氳嚜宸辩殑瀹炰綋绾圭悊锛宎lpha==0 鍓旈櫎锛?----

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
