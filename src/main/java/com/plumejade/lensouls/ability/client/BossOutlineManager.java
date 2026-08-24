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
 * BOSS 闂€婊堢搳閹诲繗绔熺粻锛勬倞閸?閳?閻欘剛鐝?mask FBO + composite閵? * composite 閻?goldOutlineShader閿涘湯obel 缁绢垵绔熺紓妯诲伎鏉?+ BOSS 閼硅绱氶敍宀冭泲 RenderType 缁狅紕鍤?Iris 閸忕厧顔愰妴? * <p>
 * 鐢呴獓閸樺鍣搁張鍝勫煑閿涘牅璞?{@link CaptureState}閿?
 * - AFTER_SKY 濞撳懐鈹?mask + 濞撳懐鈹栭崢濠氬櫢闂嗗棗鎮? * - Shadow pass 閹规洝骞忛埆鎺曨潶 AFTER_SKY 娑撱垹绱? * - 娑?pass 閹规洝骞忛埆鎺撴付缂佸牅绻氶悾? */
@EventBusSubscriber(value = Dist.CLIENT)
public class BossOutlineManager {

    public static ShaderInstance bossCompositeShader;

    private static RenderTarget maskTarget;
    private static RenderTarget prevTarget;
    private static final ThreadLocal<Integer> captureEntityId = ThreadLocal.withInitial(() -> -1);
    private static final ThreadLocal<Boolean> inMaskWrite = ThreadLocal.withInitial(() -> false);
    private static MultiBufferSource.BufferSource maskBufferSource;
    private static BossOutlineColors currentColors;

    /** 鐢呴獓閸樺鍣搁梿鍡楁値閿涘牅璞?CaptureState閿?*/
    private static final IntOpenHashSet capturedThisFrame = new IntOpenHashSet();

    public static boolean tryStartCapture(int entityId) {
        if (!capturedThisFrame.add(entityId)) return false;
        captureEntityId.set(entityId);
        return true;
    }

    /** AFTER_SKY 閺冭埖绔荤粚鍝勫箵闁插秹娉﹂崥鍫礉娑撳绔撮梼鑸殿唽閿涘牅瀵?pass閿涘娈戠€圭偘缍嬮崣顖炲櫢閺傜増宕熼懢?*/
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

    // ========== 妫版粏澹?==========

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

    /** 濞撳懐鈹?mask 妫版粏澹?+ 濞ｅ崬瀹?*/
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

    public static void setCompositeShader(ShaderInstance shader) { bossCompositeShader = shader; }
    public static void beginFrame() { currentColors = null; }

    // ========== 鐢傜皑娴?==========

    /**
     * AFTER_SKY 閳?濞撳懐鈹?mask + 濞撳懐鈹栭崢濠氬櫢闂嗗棗鎮庨妴?     * Iris shadow pass 閸?AFTER_SKY 娑斿澧犳潻鎰攽閿涘苯鍙鹃幑鏇″箯缂佹挻鐏夌悮顐ｎ劃閺傝纭舵稉銏犵磾閿?     * 娑?pass 閸?AFTER_SKY 娑斿鎮楁潻鎰攽閿涘本宕熼懢椋庣波閺嬫粈绻氶悾娆忓煂鐢勬汞 composite閵?     */
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
        var shader = bossCompositeShader;
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
        // 鍘熺増 tick 鏃堕棿椹卞姩锛堟弿杈规笎鍙樿窡闅忔父鎴忔椂闂达級
        if (shader.getUniform("Time") != null && mc.level != null) {
            long wrapped = Math.floorMod(mc.level.getGameTime(), 240000L);
            float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
            shader.getUniform("Time").set((wrapped + partialTick) * 0.05f);
        }

        // 閸忋劌鐫嗛崶娑滅珶瑜邦澀浜?NDC (-1..1) 閻╁瓨甯撮柧鐑樺姬鐏炲繐绠烽敍灞界箑妞よ崵鏁?identity 閹舵洖濂?鐟欏棗娴橀惌鈺呮█閿?        // 瑜版挸澧?RenderSystem 濞堝鏆€娑撴牜鏅〒鍙夌厠閻ㄥ嫮娴夐張娲偓蹇氼潒閻晠妯€閿涘奔绗夐柌宥囩枂娴兼碍濮囪ぐ杈ㄥ灇閸︿即娼伴惌鈺佽埌
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        try {
            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            var consumer = bufferSource.getBuffer(CompositeRenderTypes.BOSS_COMPOSITE_QUAD);
            consumer.addVertex(-1, -1, 0).setUv(0, 0);
            consumer.addVertex( 1, -1, 0).setUv(1, 0);
            consumer.addVertex( 1,  1, 0).setUv(1, 1);
            consumer.addVertex(-1,  1, 0).setUv(0, 1);
            bufferSource.endBatch(CompositeRenderTypes.BOSS_COMPOSITE_QUAD);
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }
}
