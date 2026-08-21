package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.plumejade.lensouls.integration.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

/**
 * 光影（Iris 光影包激活）下的 glint 自绘画布。
 * <p>
 * Iris 不识别模组自定义 CoreShader（光影包激活时被忽略），原版 glint shader
 * 又不采样 Sampler1（无按贴图 alpha 剔除）——光影下物品 glint 会铺满整个模型
 * 网格（方片）。解法：glint 顶点画进自建 FBO（自定义双采样 shader 照常执行，
 * Iris 不拦截自定义 FBO 输出），帧末把 FBO 内容合成回主画面。
 * <p>
 * 非光影路径不受影响：{@link #OUTPUT_STATE} 的绑定函数运行时判断——
 * 非光影直接绑主 target（等价原 MAIN_TARGET 语义），光影才绑 glint FBO。
 */
@EventBusSubscriber(value = Dist.CLIENT)
public final class GlintFrameBuffer {

    private static RenderTarget glintTarget;
    private static RenderTarget prevTarget;
    private static boolean glintUsedInFrame = false;
    private static boolean needsComposite = false;

    private GlintFrameBuffer() {}

    /** 各 glint RenderType 的输出状态：运行时决定绑主 target（非光影）还是 glint FBO（光影）。 */
    public static final RenderStateShard.OutputStateShard OUTPUT_STATE =
            new RenderStateShard.OutputStateShard(
                    "lensouls_glint_output",
                    GlintFrameBuffer::setupOutput,
                    GlintFrameBuffer::restoreOutput);

    public static void markGlintFrame() {
        glintUsedInFrame = true;
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
            needsComposite = false;
            if (IrisCompat.isShadersActive() && glintTarget != null) {
                var mc = Minecraft.getInstance();
                var main = mc.getMainRenderTarget();
                glintTarget.bindWrite(true);
                glintTarget.setClearColor(0, 0, 0, 0);
                glintTarget.clear(Minecraft.ON_OSX);
                main.bindWrite(false);
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (IrisCompat.isShadersActive() && glintUsedInFrame) {
                needsComposite = true;
            }
        }
    }

    /** 帧末（GameRendererFrameEndMixin）把 glint FBO 合成回主画面。 */
    public static void compositeIfNeeded(Minecraft mc, RenderTarget main) {
        if (!needsComposite) return;
        needsComposite = false;
        if (glintTarget == null) return;
        if (!IrisCompat.isShadersActive()) return;
        if (mc.level == null) return;
        if (mc.options.hideGui || mc.screen != null) return;

        main.bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, glintTarget.getColorTextureId());

        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        var consumer = bufferSource.getBuffer(CompositeRenderTypes.MAIN_QUAD);
        consumer.addVertex(-1, -1, 0).setUv(0, 0);
        consumer.addVertex(1, -1, 0).setUv(1, 0);
        consumer.addVertex(1, 1, 0).setUv(1, 1);
        consumer.addVertex(-1, 1, 0).setUv(0, 1);
        bufferSource.endBatch(CompositeRenderTypes.MAIN_QUAD);

        RenderSystem.disableBlend();
    }
}
