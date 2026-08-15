package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.function.Supplier;

/**
 * 时停黑白后处理 + 彩色豁免（星空/玩家）+ glint 彩色叠加。
 * <p>
 * 帧末管线（{@code GameRendererFrameEndMixin} RETURN 调用，先于描边合成）：
 * <ol>
 *   <li>拷贝主目标深度 → 场景深度纹理（深度豁免的遮挡基准）；</li>
 *   <li>黑白滤镜：全屏灰阶，星空/玩家深度比场景近（未被遮挡）处保持彩色；</li>
 *   <li>glint FBO 叠加回主目标（彩色，预乘混合）。</li>
 * </ol>
 * FBO 清单（尺寸跟随主目标）：
 * <ul>
 *   <li>{@code glintTarget}：状态光效（破韧红/无敌白/冻结蓝）渲染目标，
 *       由 glint RenderType 的 {@link #GLINT_OUTPUT} 导向；</li>
 *   <li>{@code starDepthFbo}：黑洞星空球面深度（渲染时写入，
 *       {@link GrayOutManager#renderBlackHoleSky} 调用 {@link #writeStarDepth}）；</li>
 *   <li>{@code playerDepthFbo}：玩家几何深度（实体/手部渲染双写，
 *       {@link PlayerDepthBufferSource} + {@link #PLAYER_DEPTH_TYPE}）；</li>
 *   <li>{@code sceneDepthFbo}：帧末从主目标深度 blit 而来，黑白滤镜的遮挡基准。</li>
 * </ul>
 * 深度比较方向：GL 深度近小远大，天空清除为 1.0；豁免条件 = 星空/玩家深度 &lt; 场景深度。
 */
@OnlyIn(Dist.CLIENT)
public class BlackWhitePost {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    public static ShaderInstance blackWhiteShader;
    public static ShaderInstance colorCopyShader;

    // ========== 全屏 quad RenderType（Iris 兼容管线） ==========

    /** NOOP 输出：绘制目标由外部显式绑定（临时 FBO / 主目标），不受 RenderType 输出 shard 干扰。 */
    private static final RenderStateShard.OutputStateShard NOOP_OUTPUT =
            new RenderStateShard.OutputStateShard("lensouls_noop_output", () -> {}, () -> {});

    /** glint 叠加用预乘混合（glint FBO 像素绘制时已按 SRC_ALPHA 预乘）。 */
    private static final RenderStateShard.TransparencyStateShard PREMULT_TRANSPARENCY;

    private static final RenderStateShard.DepthTestStateShard NO_DEPTH_TEST_STATE;

    static {
        try {
            var depthCtor = RenderStateShard.DepthTestStateShard.class
                    .getDeclaredConstructor(String.class, int.class);
            depthCtor.setAccessible(true);
            NO_DEPTH_TEST_STATE = depthCtor.newInstance("lensouls_always", 519); // GL_ALWAYS
        } catch (Exception e) {
            throw new RuntimeException("Failed to create NO_DEPTH_TEST StateShard", e);
        }
        try {
            var blendCtor = RenderStateShard.TransparencyStateShard.class
                    .getDeclaredConstructor(String.class, Runnable.class, Runnable.class);
            blendCtor.setAccessible(true);
            PREMULT_TRANSPARENCY = blendCtor.newInstance(new Object[] {
                    "lensouls_premult",
                    (Runnable) () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFuncSeparate(
                                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    },
                    (Runnable) RenderSystem::disableBlend
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PREMULT_TRANSPARENCY", e);
        }
    }

    private static RenderType quad(String name, Supplier<ShaderInstance> shader,
                                   RenderStateShard.TransparencyStateShard transparency) {
        return RenderType.create(
                name,
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS, 256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                        .setDepthTestState(NO_DEPTH_TEST_STATE)
                        .setTransparencyState(transparency)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOutputState(NOOP_OUTPUT)
                        .createCompositeState(false)
        );
    }

    /** 黑白滤镜全屏 quad。 */
    public static final RenderType BLACKWHITE_QUAD =
            quad("lensouls_blackwhite_quad", () -> blackWhiteShader, RenderStateShard.NO_TRANSPARENCY);

    /** 复制全屏 quad（临时 FBO → 主目标）。 */
    public static final RenderType COPY_QUAD =
            quad("lensouls_copy_quad", () -> colorCopyShader, RenderStateShard.NO_TRANSPARENCY);

    /** glint 叠加全屏 quad（预乘混合）。 */
    public static final RenderType GLINT_QUAD =
            quad("lensouls_glint_quad", () -> colorCopyShader, PREMULT_TRANSPARENCY);

    // ========== 输出导向（glint / 玩家深度 RenderType 引用） ==========

    /** glint 输出：画到 glint FBO。 */
    public static final RenderStateShard.OutputStateShard GLINT_OUTPUT =
            new RenderStateShard.OutputStateShard("lensouls_glint_output",
                    BlackWhitePost::bindGlintTarget, BlackWhitePost::restorePrevTarget);

    /** 玩家深度输出：玩家几何双写画到玩家深度 FBO。 */
    public static final RenderStateShard.OutputStateShard PLAYER_DEPTH_OUTPUT =
            new RenderStateShard.OutputStateShard("lensouls_player_depth_output",
                    BlackWhitePost::bindPlayerDepthTarget, BlackWhitePost::restorePrevTarget);

    /** 玩家深度 RenderType（NEW_ENTITY 几何，输出玩家深度 FBO；shader 复用 maskShader 纯白）。 */
    public static final RenderType PLAYER_DEPTH_TYPE = RenderType.create(
            "lensouls_player_depth",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> FrozenOutlineManager.maskShader))
                    .setOutputState(PLAYER_DEPTH_OUTPUT)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setCullState(RenderStateShard.CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false)
    );

    // ========== FBO 状态 ==========

    /** 帧末合成专用 buffer（与主批次隔离，杜绝残留批次被错误矩阵重放污染画面）。 */
    private static final MultiBufferSource.BufferSource COMPOSITE_BUFFERS =
            new RenderBuffers(256).bufferSource();

    private static boolean samplerUnitsReady = false;

    private static RenderTarget glintTarget;
    private static RenderTarget tempTarget;
    private static int starDepthFbo = 0;
    private static int starDepthTex = 0;
    private static int sceneDepthFbo = 0;
    private static int sceneDepthTex = 0;
    private static int playerDepthFbo = 0;
    private static int playerDepthTex = 0;
    private static int fboWidth = -1;
    private static int fboHeight = -1;

    private static RenderTarget prevTarget;

    // ========== 生命周期 ==========

    /** 帧头：清空 glint FBO（每帧）；时停时清空星空/玩家深度 FBO。 */
    public static void beginFrame() {
        ensureSamplerUnits();
        ensureTargets();
        if (glintTarget == null) return;
        glintTarget.setClearColor(0, 0, 0, 0);
        glintTarget.bindWrite(true);
        glintTarget.clear(Minecraft.ON_OSX);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        if (ClientFreezeCache.isTimeFrozen()) {
            clearDepthFbo(starDepthFbo);
            clearDepthFbo(playerDepthFbo);
        }
    }

    /**
     * Sampler0-3 的 uniform 单元值上传。
     * <p>
     * ShaderInstance 只绑定纹理（apply 按 json sampler 声明顺序绑到 GL_TEXTURE0+i），
     * 但从不给 sampler uniform 赋值——GLSL 默认全 0，Sampler1/2/3 会采样单元 0。
     * 这里链接后手动 glUniform1i(location, i) 一次（持久状态）。
     */
    public static void ensureSamplerUnits() {
        if (samplerUnitsReady) return;
        samplerUnitsReady = true;
        fixSamplerUnits(blackWhiteShader);
        fixSamplerUnits(colorCopyShader);
    }

    private static void fixSamplerUnits(ShaderInstance shader) {
        if (shader == null) return;
        try {
            var nameField = ShaderInstance.class.getDeclaredField("samplerNames");
            nameField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<String> names = (java.util.List<String>) nameField.get(shader);
            var programField = ShaderInstance.class.getDeclaredField("programId");
            programField.setAccessible(true);
            int program = programField.getInt(shader);
            GL20.glUseProgram(program);
            for (int i = 0; i < names.size(); i++) {
                int loc = GL20.glGetUniformLocation(program, names.get(i));
                if (loc >= 0) {
                    GL20.glUniform1i(loc, i);
                }
            }
            GL20.glUseProgram(0);
        } catch (Exception ignored) {
            // 失败时 uniform 保持 0：Sampler1-3 退化为采样单元 0（豁免逻辑错乱但画面可读）
        }
    }

    /** 帧末：黑白滤镜（时停）+ glint 叠加，先于描边合成调用。 */
    public static void compositeIfNeeded(Minecraft mc, RenderTarget main) {
        if (mc.level == null || main == null) return;
        ensureTargets();
        LOGGER.info("[Lensouls][BW] compositeIfNeeded frozen={} shader={} copy={}",
                ClientFreezeCache.isTimeFrozen(), blackWhiteShader != null, colorCopyShader != null);
        // 二分：PlayerDepthBufferSource.flushDepthSource() 已禁用
        if (ClientFreezeCache.isTimeFrozen()) {
            copySceneDepth(main);
            blackWhitePass(main);
            copyBackPass(main);
        }
        glintComposite(main);
    }

    /** 黑洞球渲染时同步写星空球面深度（灰阶滤镜的星空豁免基准）。 */
    public static void writeStarDepth(Matrix4f modelViewMatrix) {
        if (starDepthFbo == 0) return;
        clearDepthFbo(starDepthFbo);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, starDepthFbo);
        GL11.glViewport(0, 0, fboWidth, fboHeight);
        GrayOutManager.drawSkySphere(modelViewMatrix);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }

    // ========== 输出绑定 ==========

    private static void bindGlintTarget() {
        ensureTargets();
        if (glintTarget == null) return;
        prevTarget = Minecraft.getInstance().getMainRenderTarget();
        glintTarget.bindWrite(false);
    }

    private static void bindPlayerDepthTarget() {
        ensureTargets();
        if (playerDepthFbo == 0) return;
        prevTarget = Minecraft.getInstance().getMainRenderTarget();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, playerDepthFbo);
        GL11.glViewport(0, 0, fboWidth, fboHeight);
    }

    private static void restorePrevTarget() {
        if (prevTarget != null) {
            prevTarget.bindWrite(false);
            prevTarget = null;
        }
    }

    // ========== 内部 pass ==========

    /** 先存 samplerMap，再手动 setShader + apply：RenderSystem.setShader 有
     *  lastAppliedShader 去重，同 shader 连续渲染会跳过 apply，纹理绑定不生效。 */
    private static void blackWhitePass(RenderTarget main) {
        LOGGER.info("[Lensouls][BW] blackWhitePass shader={} copy={}",
                blackWhiteShader != null, colorCopyShader != null);
        tempTarget.bindWrite(false);
        blackWhiteShader.setSampler("Sampler0", main.getColorTextureId());
        blackWhiteShader.setSampler("Sampler1", sceneDepthTex);
        blackWhiteShader.setSampler("Sampler2", starDepthTex);
        blackWhiteShader.setSampler("Sampler3", playerDepthTex);
        RenderSystem.setShader(() -> blackWhiteShader);
        blackWhiteShader.apply();
        drawQuad(BLACKWHITE_QUAD);
    }

    private static void copyBackPass(RenderTarget main) {
        main.bindWrite(false);
        colorCopyShader.setSampler("Sampler0", tempTarget.getColorTextureId());
        RenderSystem.setShader(() -> colorCopyShader);
        colorCopyShader.apply();
        drawQuad(COPY_QUAD);
    }

    private static void glintComposite(RenderTarget main) {
        LOGGER.info("[Lensouls][BW] glintComposite target={}",
                glintTarget != null ? glintTarget.getColorTextureId() : -1);
        main.bindWrite(false);
        colorCopyShader.setSampler("Sampler0", glintTarget.getColorTextureId());
        RenderSystem.setShader(() -> colorCopyShader);
        colorCopyShader.apply();
        drawQuad(GLINT_QUAD);
    }

    /** 全屏 quad 绘制（矩阵管理照抄 FrozenOutlineManager.compositeIfNeeded）。 */
    private static void drawQuad(RenderType type) {
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();
        try {
            var consumer = COMPOSITE_BUFFERS.getBuffer(type);
            consumer.addVertex(-1, -1, 0).setUv(0, 0);
            consumer.addVertex( 1, -1, 0).setUv(1, 0);
            consumer.addVertex( 1,  1, 0).setUv(1, 1);
            consumer.addVertex(-1,  1, 0).setUv(0, 1);
            COMPOSITE_BUFFERS.endBatch(type);
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    // ========== 深度 FBO 管理 ==========

    /** 主目标深度（RBO）→ 场景深度纹理（blit）。 */
    private static void copySceneDepth(RenderTarget main) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFramebufferId(main));
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneDepthFbo);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height,
                0, 0, main.width, main.height,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /** 反射读取 RenderTarget 的 FBO id（字段非公开，映射名 frameBufferId）。 */
    private static int mainFramebufferId(RenderTarget target) {
        try {
            var field = RenderTarget.class.getDeclaredField("frameBufferId");
            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("lensouls: 无法读取 RenderTarget.frameBufferId", e);
        }
    }

    private static void clearDepthFbo(int fbo) {
        if (fbo == 0) return;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL11.glDepthMask(true);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private static void ensureTargets() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) return;
        int w = main.width;
        int h = main.height;
        if (glintTarget != null && fboWidth == w && fboHeight == h) return;

        if (glintTarget == null) {
            glintTarget = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        } else {
            glintTarget.resize(w, h, Minecraft.ON_OSX);
        }
        glintTarget.setFilterMode(GL11.GL_NEAREST);

        if (tempTarget == null) {
            tempTarget = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        } else {
            tempTarget.resize(w, h, Minecraft.ON_OSX);
        }
        tempTarget.setFilterMode(GL11.GL_NEAREST);

        int[] star = createDepthFbo(w, h);
        if (starDepthFbo != 0) deleteDepthFbo(starDepthFbo, starDepthTex);
        starDepthFbo = star[0];
        starDepthTex = star[1];

        int[] scene = createDepthFbo(w, h);
        if (sceneDepthFbo != 0) deleteDepthFbo(sceneDepthFbo, sceneDepthTex);
        sceneDepthFbo = scene[0];
        sceneDepthTex = scene[1];

        int[] player = createDepthFbo(w, h);
        if (playerDepthFbo != 0) deleteDepthFbo(playerDepthFbo, playerDepthTex);
        playerDepthFbo = player[0];
        playerDepthTex = player[1];

        fboWidth = w;
        fboHeight = h;
    }

    /** 创建仅深度纹理附件的 FBO（GL_DEPTH_COMPONENT32F 可采样）。返回 [fbo, tex]。 */
    private static int[] createDepthFbo(int w, int h) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL30.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F,
                w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, tex, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("lensouls depth FBO incomplete");
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        return new int[] { fbo, tex };
    }

    private static void deleteDepthFbo(int fbo, int tex) {
        if (fbo != 0) GL30.glDeleteFramebuffers(fbo);
        if (tex != 0) GL11.glDeleteTextures(tex);
    }

    private BlackWhitePost() {
    }
}