package com.plumejade.lensouls.client.itemoutline;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * 第一人称手持物描边：独立 mask 目标 + composite 合成（仿 yuyu 思路，但仅覆盖第一人称手持物、单色）。
 * <p>
 * 流程：
 * <ol>
 *   <li>在 {@code ItemRenderer.render} 的 HEAD，对第一人称手持物把物品「再渲染一次」进独立 mask 目标，
 *       得到干净的白色剪影（mask 的 alpha 通道即物品覆盖）。</li>
 *   <li>在 {@code RenderGuiEvent.Pre} 用 {@code item_outline_composite} 着色器对 mask 做膨胀（dilation），
 *       在主目标上围绕手持物绘制单色描边。</li>
 * </ol>
 * 与 BOSS / 冻结描边完全解耦（各自独立的 mask 目标），避免共用 FBO 导致的「描边丢失」，
 * 且不再因挥砍跳过捕获而产生抖动。
 */
public class ItemOutlinePostProcessor {

    private static RenderTarget maskTarget;
    private static final ThreadLocal<Boolean> inMaskRender = ThreadLocal.withInitial(() -> false);

    private static boolean capturedThisFrame = false;
    private static boolean maskClearedThisFrame = false;
    private static int outlineRgb = 0;
    private static int outlineRadius = 3;

    private static void ensureTarget() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null) return;
        if (maskTarget == null) {
            maskTarget = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            maskTarget.setFilterMode(GL11.GL_NEAREST);
        } else if (maskTarget.width != main.width || maskTarget.height != main.height) {
            maskTarget.resize(main.width, main.height, Minecraft.ON_OSX);
            maskTarget.setFilterMode(GL11.GL_NEAREST);
        }
    }

    /** 由 {@code ItemRendererOutlineMixin} 在 {@code ItemRenderer.render} HEAD 调用 */
    public static void onRenderHead(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                    PoseStack pose, MultiBufferSource buffer, int light, int overlay,
                                    BakedModel model, ItemRenderer itemRenderer) {
        if (inMaskRender.get()) return;
        ItemOutlineData data = ItemOutlineDispatcher.getOutline(stack, context);
        if (data == null) return;

        ensureTarget();
        if (maskTarget == null) return;

        if (!maskClearedThisFrame) {
            maskClearedThisFrame = true;
            capturedThisFrame = true;
            outlineRgb = data.rgb();
            outlineRadius = data.radiusPixels();
            maskTarget.bindWrite(true);
            maskTarget.setClearColor(0f, 0f, 0f, 0f);
            maskTarget.clear(Minecraft.ON_OSX);
        }
        maskTarget.bindWrite(false);

        inMaskRender.set(true);
        try {
            MultiBufferSource.BufferSource maskBuffer = new RenderBuffers(256).bufferSource();
            itemRenderer.render(stack, context, leftHand, pose, maskBuffer, light, overlay, model);
            maskBuffer.endBatch();
        } finally {
            inMaskRender.set(false);
        }

        var main = Minecraft.getInstance().getMainRenderTarget();
        if (main != null) main.bindWrite(false);
    }

    /** 由 {@code ItemOutlineCompatEvents} 在 {@code RenderGuiEvent.Pre} 调用 */
    public static void composite(Minecraft mc, RenderTarget main) {
        if (!capturedThisFrame) return;
        capturedThisFrame = false;
        maskClearedThisFrame = false;

        var shader = ItemOutlineShaders.itemCompositeShader;
        if (shader == null || maskTarget == null) return;

        main.bindWrite(true);

        RenderSystem.setShader(() -> shader);
        shader.setSampler("DiffuseSampler", maskTarget.getColorTextureId());

        float r = ((outlineRgb >> 16) & 0xFF) / 255f;
        float g = ((outlineRgb >> 8) & 0xFF) / 255f;
        float b = (outlineRgb & 0xFF) / 255f;
        if (shader.getUniform("OutlineColor") != null) shader.getUniform("OutlineColor").set(r, g, b);
        if (shader.getUniform("OutlineWidth") != null) shader.getUniform("OutlineWidth").set((float) outlineRadius);
        if (shader.getUniform("ScreenSize") != null) shader.getUniform("ScreenSize").set((float) main.width, (float) main.height);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            var bs = mc.renderBuffers().bufferSource();
            var consumer = bs.getBuffer(ItemOutlineRenderTypes.ITEM_OUTLINE_QUAD);
            consumer.addVertex(-1, -1, 0).setUv(0, 0);
            consumer.addVertex(1, -1, 0).setUv(1, 0);
            consumer.addVertex(1, 1, 0).setUv(1, 1);
            consumer.addVertex(-1, 1, 0).setUv(0, 1);
            bs.endBatch(ItemOutlineRenderTypes.ITEM_OUTLINE_QUAD);
            RenderSystem.disableBlend();
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }
}
