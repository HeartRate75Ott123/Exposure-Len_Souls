package com.plumejade.lensouls.client.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 屏幕后处理特效管理器（纯客户端）。
 * <p>
 * 持有若干条 {@link ScreenFxEffect} 实例，每帧把它们叠加成一个状态，
 * 在 {@code AFTER_LEVEL}（Iris 合成之后、原版清深度之前）跑一次全屏 pass：
 * <ol>
 *   <li>把主帧缓冲颜色 blit 到常驻 {@link TextureTarget}（避免自采样 UB）；</li>
 *   <li>绑定主帧缓冲，用 {@code screen_fx} 着色器按状态重采样（拉伸/色散/速度线/闪白/染色/涟漪）；</li>
 *   <li>没有活动特效时一次都不画（零开销）。</li>
 * </ol>
 * 同一位置也是冻结天穹用的位置，Iris 下已验证不会被 shaderpack 抢回 gbuffer。
 */
@OnlyIn(Dist.CLIENT)
public final class ScreenFxManager {

    /** 屏幕特效着色器（RegisterShadersEvent 注册）。 */
    public static ShaderInstance shader;

    private static final List<ScreenFxEffect> EFFECTS = new ArrayList<>();

    private static RenderTarget colorCopy;
    private static long lastNanos;
    private static float time;
    /** 本次会话里是否已经打过"pass 生效"的诊断日志（只打一次，避免刷屏） */
    private static boolean loggedPass;
    /** 本帧是否已经画过（同一帧只画一次；每帧由 {@link #beginFrame()} 清零） */
    private static boolean drawnThisFrame;

    private ScreenFxManager() {
    }

    public static void push(ScreenFxEffect effect) {
        if (effect != null) EFFECTS.add(effect);
    }

    public static void clear() {
        EFFECTS.clear();
        lastNanos = 0L;
        loggedPass = false;
        drawnThisFrame = false;
    }

    /**
     * 每帧开头调一次（`GameRenderer.render` 的 HEAD）。
     * <p>
     * 同一帧内 {@code GameRenderer.renderLevel} 可能被调用多次（Exposure 抓帧、其它渲染通道），
     * 后处理只能画一次。以前这里用 {@code System.nanoTime()} 的 5ms 窗口去重 ——
     * 那是<b>按墙钟猜帧</b>：帧间隔本来就短（高刷/掉帧）时，<b>真帧</b>会被误判成"同帧"
     * 而整帧跳过，实机表现就是特效"几帧消失、几帧出现"的频闪。现在改成精确的帧标志。
     */
    public static void beginFrame() {
        drawnThisFrame = false;
    }

    public static boolean isIdle() {
        return EFFECTS.isEmpty();
    }

    /**
     * 只负责"取矩阵"：{@code AFTER_LEVEL} 是唯一拿得到本帧投影/视图矩阵与相机位置的地方，
     * 世界坐标 → 屏幕坐标的投影器在这里更新（{@link SoulSeverScreenFx} 用）。
     * <p>
     * <b>绘制不在这个阶段</b>：见 {@link #renderFrame()}。
     */
    @SubscribeEvent
    public static void onCaptureProjector(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        ScreenProjector.update(event.getProjectionMatrix(), event.getModelViewMatrix(),
                event.getCamera().getPosition());
    }

    /**
     * 每帧推进 + 绘制。<b>必须晚于第一人称手部渲染</b>。
     * <p>
     * 原来挂在 {@code RenderLevelStageEvent.AFTER_LEVEL}（{@code LevelRenderer.renderLevel} 末尾），
     * 而 {@code GameRenderer.renderLevel} 在它之后才清深度、画手持物 —— 结果就是
     * <b>镜面炸碎只盖住了世界，手里的相机/手还是完整的</b>（用户实测："没有叠加在最上层"）。
     * 现在由 {@code GameRendererFrameEndMixin} 在 {@code GameRenderer.renderLevel} 的 RETURN 调用，
     * 即"世界 + 手部都画完之后、HUD 之前"，整屏后处理才真正覆盖一切世界层内容。
     */
    public static void renderFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null) return;

        // ① 拍照抓帧会**额外**调一次 renderLevel（Exposure 的 BackgroundScreenshotCaptureTask，
        //    那时绑的是抓帧 FBO 而不是主目标）——这条路上绝不能画，否则会画到屏幕上/画进照片里。
        if (!RenderSystem.isOnRenderThread()) return;
        if (io.github.mortuusars.exposure.client.capture.task.BackgroundScreenshotCaptureTask.isCapturing()) return;

        // ② 同一帧里被调用多次只画一次。判据是"帧标志"而不是墙钟（见 beginFrame 的注释）：
        //    墙钟 5ms 去重会在高刷/掉帧时把真帧当成同帧跳过 → 特效频闪。
        if (drawnThisFrame) return;
        drawnThisFrame = true;

        long now = System.nanoTime();

        float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0E9F, 0.1F);
        lastNanos = now;
        time += dt;

        if (EFFECTS.isEmpty()) return;

        State state = new State();
        Iterator<ScreenFxEffect> it = EFFECTS.iterator();
        while (it.hasNext()) {
            ScreenFxEffect effect = it.next();
            if (!effect.update(dt)) {
                it.remove();
                continue;
            }
            effect.apply(state);
        }
        if (EFFECTS.isEmpty() || state.isEmpty() || shader == null) return;

        render(mc, state);
    }

    // =====================================================================
    //  渲染
    // =====================================================================

    private static void render(Minecraft mc, State state) {
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0) return;

        ensureCopy(main);
        blit(main, colorCopy);

        main.bindWrite(true);

        if (!loggedPass) {
            loggedPass = true;
            com.plumejade.lensouls.LenSouls.LOGGER.info(
                    "[ScreenFx] 后处理 pass 已生效（shard={}, flash={}, viewfinder={}）",
                    state.shard, state.flash, state.viewfinder);
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableScissor();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);

        shader.setSampler("DiffuseSampler", colorCopy.getColorTextureId());
        set2f("FxScreenSize", main.width, main.height);
        set1f("FxTime", time);
        set1f("FxWarp", state.warp);
        set2f("FxWarpCenter", state.warpCenterX, state.warpCenterY);
        set1f("FxChroma", state.chroma);
        set1f("FxStreak", state.streak);
        set1f("FxShard", state.shard);
        set1f("FxShardPhase", state.shardPhase);
        set1f("FxShardRestore", state.shardRestore);
        set1f("FxPrism", state.prism);
        set1f("FxRgb", state.rgb);
        set1f("FxBlurH", state.blurH);
        set1f("FxViewfinder", state.viewfinder);
        set1f("FxViewfinderOpen", state.viewfinderOpen);
        set1f("FxFlash", state.flash);
        set3f("FxFlashColor", state.flashR, state.flashG, state.flashB);
        set4f("FxTint", state.tintR, state.tintG, state.tintB, state.tintA);
        set4f("FxRipple", state.rippleX, state.rippleY, state.rippleRadius, state.rippleStrength);
        set3f("FxRippleColor", state.rippleR, state.rippleG, state.rippleB);
        set1f("FxMask", state.mask);
        set1f("FxMaskEdge", state.maskEdge);
        set1f("FxMaskSoft", state.maskSoft);
        set1f("FxMaskAlpha", state.maskAlpha);
        set2f("FxMaskRadius", state.maskRadiusU, state.maskRadiusV);
        set2f("FxMaskCenter", state.maskCenterX, state.maskCenterY);

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1.0F, -1.0F, 0.0F);
        buffer.addVertex(1.0F, -1.0F, 0.0F);
        buffer.addVertex(1.0F, 1.0F, 0.0F);
        buffer.addVertex(-1.0F, 1.0F, 0.0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void ensureCopy(RenderTarget main) {
        if (colorCopy != null && colorCopy.width == main.width && colorCopy.height == main.height) return;
        if (colorCopy != null) {
            colorCopy.destroyBuffers();
            colorCopy = null;
        }
        colorCopy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        colorCopy.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
    }

    /** 主帧缓冲颜色 → 常驻纹理（blit，不做自采样）。 */
    private static void blit(RenderTarget main, RenderTarget copy) {
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height,
                0, 0, copy.width, copy.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
    }

    private static void set1f(String name, float v) {
        var u = shader.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void set2f(String name, float a, float b) {
        var u = shader.getUniform(name);
        if (u != null) u.set(a, b);
    }

    private static void set3f(String name, float a, float b, float c) {
        var u = shader.getUniform(name);
        if (u != null) u.set(a, b, c);
    }

    private static void set4f(String name, float a, float b, float c, float d) {
        var u = shader.getUniform(name);
        if (u != null) u.set(a, b, c, d);
    }

    // =====================================================================
    //  叠加状态
    // =====================================================================

    /** 一帧内所有屏幕特效叠加后的参数（各特效写自己的那一部分）。 */
    public static final class State {
        public float warp;
        public float warpCenterX = 0.5F;
        public float warpCenterY = 0.5F;
        public float chroma;
        public float streak;
        public float shard;
        public float shardPhase;
        public float shardRestore;
        public float prism;
        /** RGB 脉冲（三通道错拍呼吸 + 色相跑马灯，见 {@code FxRgb}） */
        public float rgb;
        public float blurH;
        public float viewfinder;
        public float viewfinderOpen;
        public float flash;
        public float flashR = 1.0F, flashG = 1.0F, flashB = 1.0F;
        public float tintR, tintG, tintB, tintA;
        public float rippleX = 0.5F, rippleY = 0.5F, rippleRadius, rippleStrength;
        public float rippleR = 1.0F, rippleG = 1.0F, rippleB = 1.0F;

        /**
         * 椭圆羽化蒙版：0 = 不加蒙版（整屏都吃特效，与旧行为一致）；1 = 按椭圆区域裁切特效。
         * <p>
         * 蒙版把上面所有子效果的输出与<b>未处理的原画面</b>按羽化权重混合，
         * 权重 = {@code smoothstep(edge - soft, edge + soft, 椭圆归一化半径)}：
         * 前沿 {@link #maskEdge} 小 → 只剩外圈有特效；前沿大 → 连外圈也退干净。
         * 所以「前沿由大到小」= 从四周向中心覆盖，「前沿由小到大」= 中心先褪、逐圈向外退。
         * 闪光画在蒙版之后，白场始终整屏（负责盖住传送那一帧）。
         */
        public float mask;
        /**
         * 蒙版内特效的整体不透明度（0 = 全透明、只剩原画面；1 = 全实）。
         * 与前沿是两件事：前沿管"盖到哪一圈"，这个管"盖到的地方有多实"，
         * 叠起来才是「由外向内 + 由淡到浓」的慢入场。默认 1（不启用蒙版的旧路径完全不受影响）。
         */
        public float maskAlpha = 1.0F;
        /** 羽化带中心处的椭圆归一化半径（0 = 屏心，1 = 椭圆边界） */
        public float maskEdge = 1.28F;
        /** 羽化带半宽（以椭圆归一化半径为单位） */
        public float maskSoft = 0.16F;
        /** 椭圆半轴（uv 归一化，0.5 = 半屏） */
        public float maskRadiusU = 0.62F;
        public float maskRadiusV = 0.74F;
        public float maskCenterX = 0.5F;
        public float maskCenterY = 0.5F;

        public boolean isEmpty() {
            return warp <= 0.001F && chroma <= 0.001F && streak <= 0.001F && shard <= 0.001F
                    && flash <= 0.001F && tintA <= 0.001F && rippleStrength <= 0.001F
                    && blurH <= 0.001F && viewfinder <= 0.001F && rgb <= 0.001F;
        }

        /** 多个特效同时写染色时取较大者，避免互相覆盖。 */
        public void addTint(float r, float g, float b, float a) {
            if (a > tintA) {
                tintR = r;
                tintG = g;
                tintB = b;
                tintA = a;
            }
        }
    }
}
