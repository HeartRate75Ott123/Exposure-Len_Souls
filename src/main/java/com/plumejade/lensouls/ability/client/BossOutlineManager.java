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
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * BOSS 闀滈瓊鎻忚竟绠＄悊鍣?鈥?鐙珛 mask FBO + composite銆? * composite 鐢?goldOutlineShader锛圫obel 绾竟缂樻弿杈?+ BOSS 鑹诧級锛岃蛋 RenderType 绠＄嚎 Iris 鍏煎銆? * <p>
 * 甯х骇鍘婚噸鏈哄埗锛堜豢 {@link CaptureState}锛?
 * - AFTER_SKY 娓呯┖ mask + 娓呯┖鍘婚噸闆嗗悎
 * - Shadow pass 鎹曡幏鈫掕 AFTER_SKY 涓㈠純
 * - 涓?pass 鎹曡幏鈫掓渶缁堜繚鐣? */
@EventBusSubscriber(value = Dist.CLIENT)
public class BossOutlineManager {

    public static ShaderInstance bossCompositeShader;

    private static RenderTarget maskTarget;
    private static RenderTarget prevTarget;
    private static final ThreadLocal<Integer> captureEntityId = ThreadLocal.withInitial(() -> -1);
    private static final ThreadLocal<Boolean> inMaskWrite = ThreadLocal.withInitial(() -> false);
    private static MultiBufferSource.BufferSource maskBufferSource;
    private static BossOutlineColors currentColors;

    /** 甯х骇鍘婚噸闆嗗悎锛堜豢 CaptureState锛?*/
    private static final IntOpenHashSet capturedThisFrame = new IntOpenHashSet();

    public static boolean tryStartCapture(int entityId) {
        if (!capturedThisFrame.add(entityId)) return false;
        captureEntityId.set(entityId);
        return true;
    }

    /** AFTER_SKY 鏃舵竻绌哄幓閲嶉泦鍚堬紝涓嬩竴闃舵锛堜富 pass锛夌殑瀹炰綋鍙噸鏂版崟鑾?*/
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

    // ========== 棰滆壊 ==========

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

    /** 娓呯┖ mask 棰滆壊 + 娣卞害 */
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

    // ========== 甯т簨浠?==========

    /**
     * AFTER_SKY 鈫?娓呯┖ mask + 娓呯┖鍘婚噸闆嗗悎銆?     * Iris shadow pass 鍦?AFTER_SKY 涔嬪墠杩愯锛屽叾鎹曡幏缁撴灉琚鏂规硶涓㈠純锛?     * 涓?pass 鍦?AFTER_SKY 涔嬪悗杩愯锛屾崟鑾风粨鏋滀繚鐣欏埌甯ф湯 composite銆?     */
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
        // 墙钟时间驱动（时停中 tick 冻结，渐变保持流动）
        if (shader.getUniform("Time") != null) {
            shader.getUniform("Time").set((float) (System.nanoTime() / 5.0E7) * 0.05f);
        }

        // 鍏ㄥ睆鍥涜竟褰互 NDC (-1..1) 鐩存帴閾烘弧灞忓箷锛屽繀椤荤敤 identity 鎶曞奖/瑙嗗浘鐭╅樀锛?        // 褰撳墠 RenderSystem 娈嬬暀涓栫晫娓叉煋鐨勭浉鏈洪€忚鐭╅樀锛屼笉閲嶇疆浼氭姇褰辨垚鍦伴潰鐭╁舰
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        try {
            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            var consumer = bufferSource.getBuffer(CompositeRenderTypes.MAIN_QUAD);
            consumer.addVertex(-1, -1, 0).setUv(0, 0);
            consumer.addVertex( 1, -1, 0).setUv(1, 0);
            consumer.addVertex( 1,  1, 0).setUv(1, 1);
            consumer.addVertex(-1,  1, 0).setUv(0, 1);
            bufferSource.endBatch(CompositeRenderTypes.MAIN_QUAD);
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }
}
