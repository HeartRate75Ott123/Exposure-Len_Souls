package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

/**
 * BOSS 镜魂描边管理器 — 独立 mask FBO + composite。
 * composite 用 goldOutlineShader（Sobel 纯边缘描边 + BOSS 色），走 RenderType 管线 Iris 兼容。
 * <p>
 * 帧级去重机制（仿 {@link CaptureState}）:
 * - AFTER_SKY 清空 mask + 清空去重集合
 * - Shadow pass 捕获→被 AFTER_SKY 丢弃
 * - 主 pass 捕获→最终保留
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class BossOutlineManager {

    public static ShaderInstance bossCompositeShader;

    private static RenderTarget maskTarget;
    private static RenderTarget prevTarget;
    private static final ThreadLocal<Integer> captureEntityId = ThreadLocal.withInitial(() -> -1);
    private static final ThreadLocal<Boolean> inMaskWrite = ThreadLocal.withInitial(() -> false);
    private static MultiBufferSource.BufferSource maskBufferSource;
    private static BossOutlineColors currentColors;

    /** 帧级去重集合（仿 CaptureState） */
    private static final IntOpenHashSet capturedThisFrame = new IntOpenHashSet();

    public static boolean tryStartCapture(int entityId) {
        if (!capturedThisFrame.add(entityId)) return false;
        captureEntityId.set(entityId);
        return true;
    }

    /** AFTER_SKY 时清空去重集合，下一阶段（主 pass）的实体可重新捕获 */
    public static void clearFrameCaptures() {
        capturedThisFrame.clear();
    }

    // ========== Capture ==========

    public static boolean isCapturing() { return captureEntityId.get() >= 0; }
    public static int getCaptureEntityId() { return captureEntityId.get(); }
    public static void startCapture(int entityId) { captureEntityId.set(entityId); }
    public static void endCapture() { captureEntityId.set(-1); }
    public static boolean isInMaskWrite() { return inMaskWrite.get(); }
    public static void setInMaskWrite(boolean v) { inMaskWrite.set(v); }

    // ========== 颜色 ==========

    public static void setColors(BossOutlineColors colors) { currentColors = colors; }
    public static BossOutlineColors getCurrentColors() { return currentColors; }

    // ========== Mask FBO ==========

    public static MultiBufferSource.BufferSource getMaskBufferSource() {
        if (maskBufferSource == null) maskBufferSource = new RenderBuffers(256).bufferSource();
        return maskBufferSource;
    }

    public static void bindMaskTarget() {
        ensureTarget();
        prevTarget = Minecraft.getInstance().getMainRenderTarget();
        maskTarget.bindWrite(false);
    }

    public static void restoreMainTarget() {
        if (prevTarget != null) { prevTarget.bindWrite(false); prevTarget = null; }
    }

    private static void ensureTarget() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null) return;
        if (maskTarget == null || maskTarget.width != main.width || maskTarget.height != main.height) {
            maskTarget = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            maskTarget.setFilterMode(GL11.GL_NEAREST);
        }
    }

    /** 清空 mask 颜色 + 深度 */
    public static void clearAndBind() {
        ensureTarget();
        maskTarget.bindWrite(true);
        maskTarget.setClearColor(0, 0, 0, 0);
        maskTarget.clear(Minecraft.ON_OSX);
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (main != null) main.bindWrite(false);
    }

    public static void flushMask() {
        if (maskBufferSource == null) return;
        if (maskTarget == null) return;
        maskTarget.bindWrite(false);
        maskBufferSource.endBatch();
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (main != null) main.bindWrite(false);
    }

    public static void setCompositeShader(ShaderInstance shader) {}
    public static void beginFrame() { currentColors = null; }

    // ========== 帧事件 ==========

    /**
     * AFTER_SKY → 清空 mask + 清空去重集合。
     * Iris shadow pass 在 AFTER_SKY 之前运行，其捕获结果被此方法丢弃；
     * 主 pass 在 AFTER_SKY 之后运行，捕获结果保留到帧末 composite。
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            clearAndBind();
            clearFrameCaptures();
        }
    }

    // ========== Composite ==========

    public static void composite(Minecraft mc, RenderTarget main) {
        if (currentColors == null) return;
        var shader = FrozenOutlineManager.goldOutlineShader;
        if (shader == null || maskTarget == null) return;
        if (mc.options.hideGui || mc.screen != null) return;

        main.bindWrite(true);

        RenderSystem.setShader(() -> shader);
        shader.setSampler("DiffuseSampler", maskTarget.getColorTextureId());

        float[] c1 = currentColors.color1();
        float[] c2 = currentColors.color2();
        float[] c3 = currentColors.color3();
        float[] c4 = currentColors.color4();
        if (shader.getUniform("BossColor1") != null) shader.getUniform("BossColor1").set(c1[0], c1[1], c1[2], 1f);
        if (shader.getUniform("BossColor2") != null) shader.getUniform("BossColor2").set(c2[0], c2[1], c2[2], 1f);
        if (shader.getUniform("BossColor3") != null) shader.getUniform("BossColor3").set(c3[0], c3[1], c3[2], 1f);
        if (shader.getUniform("BossColor4") != null) shader.getUniform("BossColor4").set(c4[0], c4[1], c4[2], 1f);
        if (shader.getUniform("BossGlowStrength") != null) shader.getUniform("BossGlowStrength").set(currentColors.glowStrength() * 1.5f);
        if (shader.getUniform("BossOutlineWidth") != null) shader.getUniform("BossOutlineWidth").set(currentColors.outlineWidth());
        if (shader.getUniform("ScreenSize") != null) shader.getUniform("ScreenSize").set((float) main.width, (float) main.height);
        if (shader.getUniform("Time") != null && mc.level != null) {
            long wrapped = Math.floorMod(mc.level.getGameTime(), 240000L);
            float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
            shader.getUniform("Time").set((wrapped + partialTick) * 0.05f);
        }

        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        var consumer = bufferSource.getBuffer(CompositeRenderTypes.MAIN_QUAD);
        consumer.addVertex(-1, -1, 0).setUv(0, 0);
        consumer.addVertex( 1, -1, 0).setUv(1, 0);
        consumer.addVertex( 1,  1, 0).setUv(1, 1);
        consumer.addVertex(-1,  1, 0).setUv(0, 1);
        bufferSource.endBatch(CompositeRenderTypes.MAIN_QUAD);
    }
}
