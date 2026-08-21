package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.plumejade.lensouls.integration.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * 光影（Iris 光影包激活）下的 glint 自绘画布。
 * <p>
 * Iris 不识别模组自定义 CoreShader（光影包激活时被忽略），原版 glint shader
 * 又不采样 Sampler1（无按贴图 alpha 剔除）——光影下物品 glint 会铺满整个模型
 * 网格（方片）。解法：glint 顶点画进自建 FBO（自定义双采样 shader 照常执行，
 * Iris 不拦截自定义 FBO 输出），帧末把 FBO 内容合成回主画面。
 * <p>
 * glint 顶点写入用本类自持的独立 BufferSource（不走世界渲染的 MultiBufferSource）：
 * Iris 激活时世界 buffer 是 BufferSourceWrapper，getBuffer 会经 typeChanger 改写
 * RenderType——自定义 shader 与 OutputStateShard 不生效。独立 buffer 的 endBatch
 * 完全自控（仿 CaptureState.maskBufferSource / Adorable Armory MASK_BUFFER_SOURCE）。
 * <p>
 * 非光影路径不受影响：{@link #OUTPUT_STATE} 的绑定函数运行时判断——
 * 非光影直接绑主 target（等价原 MAIN_TARGET 语义），光影才绑 glint FBO。
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class GlintFrameBuffer {

    private static RenderTarget glintTarget;
    private static RenderTarget prevTarget;
    private static boolean glintUsedInFrame = false;
    private static MultiBufferSource.BufferSource glintBufferSource;

    /** glint FBO 合成回主画面的着色器（POSITION_TEX 全屏 quad）。 */
    public static ShaderInstance glintCompositeShader;

    private GlintFrameBuffer() {}

    /** 各 glint RenderType 的输出状态：运行时决定绑主 target（非光影）还是 glint FBO（光影）。 */
    public static final RenderStateShard.OutputStateShard OUTPUT_STATE =
            new RenderStateShard.OutputStateShard(
                    "lensouls_glint_output",
                    GlintFrameBuffer::setupOutput,
                    GlintFrameBuffer::restoreOutput);

    /** glint 顶点写入的独立 BufferSource（绕开 Iris 的 BufferSourceWrapper）。 */
    public static MultiBufferSource.BufferSource getBufferSource() {
        if (glintBufferSource == null) {
            glintBufferSource = new RenderBuffers(256).bufferSource();
        }
        return glintBufferSource;
    }

    public static void markGlintFrame() {
        glintUsedInFrame = true;
    }

    /** 帧末把收集的 glint 顶点绘制到输出目标（非光影→主 target，光影→glint FBO）。 */
    public static void endBatchGlint() {
        if (glintBufferSource != null) {
            glintBufferSource.endBatch();
        }
    }

    private static void setupOutput() {
        prevTarget = Minecraft.getInstance().getMainRenderTarget();
        if (IrisCompat.isShadersActive()) {
            ensureTarget();
            glintTarget.bindWrite(false);
        } else {
            prevTarget.bindWrite(false);
        }
    }

    private static void restoreOutput() {
        if (prevTarget != null) {
            prevTarget.bindWrite(false);
            prevTarget = null;
        }
    }

    private static void ensureTarget() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (glintTarget == null) {
            glintTarget = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            glintTarget.setFilterMode(GL11.GL_NEAREST);
        } else if (glintTarget.width != main.width || glintTarget.height != main.height) {
            glintTarget.resize(main.width, main.height, Minecraft.ON_OSX);
            glintTarget.setFilterMode(GL11.GL_NEAREST);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            glintUsedInFrame = false;
            if (IrisCompat.isShadersActive() && glintTarget != null) {
                var mc = Minecraft.getInstance();
                var main = mc.getMainRenderTarget();
                glintTarget.bindWrite(true);
                glintTarget.setClearColor(0, 0, 0, 0);
                glintTarget.clear(Minecraft.ON_OSX);
                main.bindWrite(false);
            }
        }
    }

    /** 帧末（GameRendererFrameEndMixin）把 glint FBO 合成回主画面（仅光影需要）。 */
    public static void compositeIfNeeded(Minecraft mc, RenderTarget main) {
        if (!IrisCompat.isShadersActive() || !glintUsedInFrame) return;
        glintUsedInFrame = false;
        if (glintTarget == null) return;
        if (glintCompositeShader == null) return;
        if (mc.level == null) return;
        if (mc.options.hideGui || mc.screen != null) return;

        // 全屏四边形以 NDC (-1..1) 直接铺满屏幕，必须用 identity 投影/视图矩阵——
        // 此刻（render RETURN）残留的是世界/HUD 矩阵，不重置会把四边形投影错位
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        try {
            main.bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(() -> glintCompositeShader);
            glintCompositeShader.setSampler("DiffuseSampler", glintTarget.getColorTextureId());

            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            var consumer = bufferSource.getBuffer(CompositeRenderTypes.GLINT_COMPOSITE_QUAD);
            consumer.addVertex(-1, -1, 0).setUv(0, 0);
            consumer.addVertex(1, -1, 0).setUv(1, 0);
            consumer.addVertex(1, 1, 0).setUv(1, 1);
            consumer.addVertex(-1, 1, 0).setUv(0, 1);
            bufferSource.endBatch(CompositeRenderTypes.GLINT_COMPOSITE_QUAD);

            RenderSystem.disableBlend();
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }
}
