package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

@EventBusSubscriber(value = Dist.CLIENT)
public class FrozenOutlineManager {

    private static RenderTarget maskTarget;
    private static RenderTarget prevTarget;

    public static ShaderInstance goldOutlineShader;
    public static ShaderInstance maskShader;
    public static ShaderInstance itemMaskShader;

    public static void resetFrame() {
    }

    private static boolean needsComposite = false;
    private static boolean maskClearedInFrame = false;

    public static void bindMaskTarget() {
        if (maskTarget == null) ensureTarget();
        prevTarget = Minecraft.getInstance().getMainRenderTarget();
        maskTarget.bindWrite(false);
    }

    public static void restoreMainTarget() {
        if (prevTarget != null) {
            prevTarget.bindWrite(false);
            prevTarget = null;
        }
    }

    public static void clearAndBindMask() {
        ensureTarget();
        maskTarget.bindWrite(true);
        maskTarget.setClearColor(0, 0, 0, 0);
        maskTarget.clear(Minecraft.ON_OSX);
    }

    public static void ensureMaskCleared() {
        if (maskClearedInFrame) return;
        maskClearedInFrame = true;
        ensureTarget();
        var mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        maskTarget.bindWrite(true);
        maskTarget.setClearColor(0, 0, 0, 0);
        maskTarget.clear(Minecraft.ON_OSX);
        main.bindWrite(false);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            ensureTarget();
            var mc = Minecraft.getInstance();
            var main = mc.getMainRenderTarget();
            maskTarget.bindWrite(true);
            maskTarget.setClearColor(0, 0, 0, 0);
            maskTarget.clear(Minecraft.ON_OSX);
            main.bindWrite(false);
            maskClearedInFrame = false;
            CaptureState.clearFrameCaptures();
            // �?pass 开始（Iris 阴影 pass 在此之前，已全部结束�?            CaptureState.setMainPassActive(true);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            CaptureState.setMainPassActive(false);
            if (goldOutlineShader != null && maskTarget != null
                    && (ClientFreezeCache.isTestMode() || ClientFreezeCache.hasAnyFrozen())) {
                needsComposite = true;
            }
        }
    }

    public static void compositeIfNeeded(Minecraft mc, RenderTarget main) {
        if (!needsComposite) return;
        needsComposite = false;

        if (goldOutlineShader == null || maskTarget == null) return;
        if (!ClientFreezeCache.isTestMode() && !ClientFreezeCache.hasAnyFrozen()) return;
        if (mc.level == null) return;
        if (mc.options.hideGui || mc.screen != null) return;

        main.bindWrite(true);

        var shader = goldOutlineShader;
        float time = mc.level.getGameTime() * 0.05f;
        if (shader.getUniform("Time") != null) shader.getUniform("Time").set(time);
        if (shader.getUniform("ScreenSize") != null) shader.getUniform("ScreenSize").set((float) main.width, (float) main.height);

        // 定身描边也用 boss 渐变样式（纯冰蓝系，四色均不含白�?        if (shader.getUniform("BossGlowStrength") != null) shader.getUniform("BossGlowStrength").set(1.2f);
        if (shader.getUniform("BossColor1") != null) shader.getUniform("BossColor1").set(0.15f, 0.45f, 1.0f, 1f);
        if (shader.getUniform("BossColor2") != null) shader.getUniform("BossColor2").set(0.3f, 0.6f, 1.15f, 1f);
        if (shader.getUniform("BossColor3") != null) shader.getUniform("BossColor3").set(0.5f, 0.75f, 1.3f, 1f);
        if (shader.getUniform("BossColor4") != null) shader.getUniform("BossColor4").set(0.7f, 0.9f, 1.5f, 1f);

        // 通过 RenderType 管线渲染全屏四边�?�?Iris 兼容�?        // 关键：全屏四边形�?NDC 坐标 (-1..1) 直接铺满屏幕，必须用 identity 投影/视图矩阵�?        // 此刻（renderItemInHand RETURN）RenderSystem 残留的是世界渲染的相机透视矩阵�?        // 不重置会把四边形投影成屏幕下方的规则矩形�?描边在地�?）�?        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(() -> shader);
            shader.setSampler("DiffuseSampler", maskTarget.getColorTextureId());

            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            var consumer = bufferSource.getBuffer(CompositeRenderTypes.MAIN_QUAD);
            consumer.addVertex(-1, -1, 0).setUv(0, 0);
            consumer.addVertex( 1, -1, 0).setUv(1, 0);
            consumer.addVertex( 1,  1, 0).setUv(1, 1);
            consumer.addVertex(-1,  1, 0).setUv(0, 1);
            bufferSource.endBatch(CompositeRenderTypes.MAIN_QUAD);

            RenderSystem.disableBlend();
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static void ensureTarget() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (maskTarget == null) {
            maskTarget = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            maskTarget.setFilterMode(GL11.GL_NEAREST);
        } else if (maskTarget.width != main.width || maskTarget.height != main.height) {
            maskTarget.resize(main.width, main.height, Minecraft.ON_OSX);
            maskTarget.setFilterMode(GL11.GL_NEAREST);
        }
    }
}
