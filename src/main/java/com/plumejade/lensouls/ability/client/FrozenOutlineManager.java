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
            CaptureState.setMainPassActive(true);
            GrayOutManager.frameStart();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            CaptureState.setMainPassActive(false);
            GrayOutManager.markActive();
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
        // 原版 tick 时间驱动（描边渐变跟随游戏时间；时停定身不冻结世界 tick）
        float time = mc.level.getGameTime() * 0.05f;
        if (shader.getUniform("Time") != null) shader.getUniform("Time").set(time);
        if (shader.getUniform("ScreenSize") != null) shader.getUniform("ScreenSize").set((float) main.width, (float) main.height);

        // 瀹氳韩鎻忚竟涔熺敤 boss 娓愬彉鏍峰紡锛堢函鍐拌摑绯伙紝鍥涜壊鍧囦笉鍚櫧锛?        if (shader.getUniform("BossGlowStrength") != null) shader.getUniform("BossGlowStrength").set(1.2f);
        if (shader.getUniform("BossColor1") != null) shader.getUniform("BossColor1").set(0.15f, 0.45f, 1.0f, 1f);
        if (shader.getUniform("BossColor2") != null) shader.getUniform("BossColor2").set(0.3f, 0.6f, 1.15f, 1f);
        if (shader.getUniform("BossColor3") != null) shader.getUniform("BossColor3").set(0.5f, 0.75f, 1.3f, 1f);
        if (shader.getUniform("BossColor4") != null) shader.getUniform("BossColor4").set(0.7f, 0.9f, 1.5f, 1f);

        // 閫氳繃 RenderType 绠＄嚎娓叉煋鍏ㄥ睆鍥涜竟褰?鈫?Iris 鍏煎銆?        // 鍏抽敭锛氬叏灞忓洓杈瑰舰浠?NDC 鍧愭爣 (-1..1) 鐩存帴閾烘弧灞忓箷锛屽繀椤荤敤 identity 鎶曞奖/瑙嗗浘鐭╅樀锛?        // 姝ゅ埢锛坮enderItemInHand RETURN锛塕enderSystem 娈嬬暀鐨勬槸涓栫晫娓叉煋鐨勭浉鏈洪€忚鐭╅樀锛?        // 涓嶉噸缃細鎶婂洓杈瑰舰鎶曞奖鎴愬睆骞曚笅鏂圭殑瑙勫垯鐭╁舰锛?鎻忚竟鍦ㄥ湴涓?锛夈€?        RenderSystem.backupProjectionMatrix();
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
