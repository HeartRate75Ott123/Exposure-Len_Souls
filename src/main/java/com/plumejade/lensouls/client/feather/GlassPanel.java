package com.plumejade.lensouls.client.feather;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * 「磨砂玻璃」合成器：把面板矩形区域换成「本帧主画面的高斯模糊 + 色调 + 高光」，
 * 圆角由 SDF 遮罩给出，<b>面板外原样输出</b>（不影响背景其余部分）。
 * <p>
 * 移植来源：{@code plumestweaks} 的 {@code RiftGlass}（MIT 血统，见 AGENTS.md）。
 * <p>
 * <b>调用时机（层级关键）</b>：必须在 {@code Screen.render()} 内、<b>背景画完且
 * {@code graphics.flush()} 之后、正文（文字/图标）之前</b>调用 —— 玻璃垫在底层，
 * 文字天然压在上面保持清晰。参考项目曾把它挂在帧末（{@code GameRenderer.render} TAIL），
 * 结果<b>盖住了全部文字</b>，实测被否；那个 {@code GameRendererMixin} 在当前参考项目里
 * 已经不存在，只剩三处过时注释还在提它。
 * <p>
 * {@code graphics.flush()} 不能省：GUI 的 {@code fill}/{@code blit} 是<b>延迟批处理</b>，
 * 不刷到主画面就拷贝不到背景，玻璃会糊成上一帧的内容。
 */
public final class GlassPanel {

    /** 玻璃模糊半径（物理像素的高斯 σ = radius/3） */
    private static final int BLUR_RADIUS = 10;

    private static int cachedVao = -1;
    private static int cachedVbo = -1;
    private static int cachedTmpTex = -1;
    private static int cachedTmpFbo = -1;
    private static boolean failed;

    private GlassPanel() {
    }

    public static boolean available() {
        return !failed;
    }

    /**
     * 在当前主画面的 (x,y,w,h)（GUI 单位）处合成磨砂玻璃面板。
     *
     * @param radiusGui 圆角半径（GUI 像素）
     * @param tint      叠加色调（ARGB，a = 混合强度）
     */
    public static void renderPanelGlass(float x, float y, float w, float h, float radiusGui, int tint) {
        if (failed) return;

        var mc = Minecraft.getInstance();
        if (mc == null || mc.getMainRenderTarget() == null) return;
        RenderTarget mainFb = mc.getMainRenderTarget();
        int fbW = mainFb.width;
        int fbH = mainFb.height;
        if (fbW <= 0 || fbH <= 0) return;

        try {
            // ── 1. 预计算模糊 ─────────────────────────────────────
            GlassRuntime runtime = GlassRuntime.get();
            runtime.setRequestedRadii(java.util.List.of(BLUR_RADIUS));
            runtime.run();
            int blurredTexId = runtime.getBlurredTextureForRadius(BLUR_RADIUS);
            if (blurredTexId <= 0) return;

            // ── 2. 合成程序 ──────────────────────────────────────
            int program = GlassProgram.get();
            if (program <= 0) return;

            // 面板矩形 → 物理像素（GL 原点在左下，y 翻转）
            double guiScale = mc.getWindow().getGuiScale();
            float px0 = (float) (x * guiScale);
            float py0 = (float) (fbH - (y + h) * guiScale);
            float px1 = (float) ((x + w) * guiScale);
            float py1 = (float) (fbH - y * guiScale);
            float sr = (float) (radiusGui * guiScale);

            // ── 3. 状态保存 ──────────────────────────────────────
            int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int[] prevTextures = new int[2];
            for (int i = 0; i < prevTextures.length; i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                prevTextures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
            boolean depthWasOn = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean blendWasOn = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean scissorWasOn = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            boolean cullWasOn = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean depthMaskWasOn = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);

            // ── 4. 主画面拷贝到 tmpTex（避免读写同一 FBO）─────────
            if (cachedTmpTex == -1) cachedTmpTex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, cachedTmpTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, fbW, fbH, 0,
                    GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            if (cachedTmpFbo == -1) cachedTmpFbo = GL30.glGenFramebuffers();
            int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, cachedTmpFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, cachedTmpTex, 0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevDrawFbo);
            GL30.glBlitFramebuffer(0, 0, fbW, fbH, 0, 0, fbW, fbH,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);

            // ── 5. 绑定纹理与 uniform ────────────────────────────
            GL20.glUseProgram(program);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, cachedTmpTex);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Sampler0"), 0);

            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, blurredTexId);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Sampler1"), 1);

            GL20.glUniform4f(GL20.glGetUniformLocation(program, "Rect"), px0, py0, px1, py1);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "RadiusPx"), sr);
            GL20.glUniform4f(GL20.glGetUniformLocation(program, "Tint"),
                    ((tint >>> 16) & 0xFF) / 255.0F,
                    ((tint >>> 8) & 0xFF) / 255.0F,
                    (tint & 0xFF) / 255.0F,
                    ((tint >>> 24) & 0xFF) / 255.0F);
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "FbSize"), fbW, fbH);

            // ── 6. 全屏 quad ─────────────────────────────────────
            if (cachedVao == -1) initQuad();
            if (cachedVao == -1) {
                restoreAll(prevProgram, prevFbo, prevVao, prevActiveTexture, prevTextures,
                        depthWasOn, blendWasOn, scissorWasOn, cullWasOn, depthMaskWasOn);
                return;
            }

            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glViewport(0, 0, fbW, fbH);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevDrawFbo); // 写回主画面

            GL30.glBindVertexArray(cachedVao);
            GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
            GL30.glBindVertexArray(prevVao);

            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

            // ── 7. 恢复 ──────────────────────────────────────────
            restoreAll(prevProgram, prevFbo, prevVao, prevActiveTexture, prevTextures,
                    depthWasOn, blendWasOn, scissorWasOn, cullWasOn, depthMaskWasOn);
        } catch (Throwable t) {
            failed = true;
            LenSouls.LOGGER.error("[Feather] 玻璃合成失败，已停用（面板回退为描边层）", t);
        }
    }

    private static void initQuad() {
        cachedVao = GL30.glGenVertexArrays();
        if (cachedVao == 0) {
            cachedVao = -1;
            return;
        }
        cachedVbo = GL30.glGenBuffers();
        GL30.glBindVertexArray(cachedVao);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, cachedVbo);

        // BL, BR, TL, TR —— TRIANGLE_STRIP 顺序；[0,1] 单位坐标
        float[] verts = {
                0, 0, 0, 0, 0,
                1, 0, 0, 1, 0,
                0, 1, 0, 0, 1,
                1, 1, 0, 1, 1,
        };
        java.nio.FloatBuffer buf = org.lwjgl.BufferUtils.createFloatBuffer(verts.length);
        buf.put(verts).flip();
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buf, GL30.GL_STATIC_DRAW);

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * 4, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5 * 4, 3 * 4);

        GL30.glBindVertexArray(0);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
    }

    private static void restoreAll(int prevProgram, int prevFbo, int prevVao, int prevActiveTexture,
                                   int[] prevTextures, boolean depthWasOn, boolean blendWasOn,
                                   boolean scissorWasOn, boolean cullWasOn, boolean depthMaskWasOn) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTextures[0]);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTextures[1]);
        GL13.glActiveTexture(prevActiveTexture);
        GL11.glDepthMask(depthMaskWasOn);
        if (depthWasOn) GL11.glEnable(GL11.GL_DEPTH_TEST);
        if (blendWasOn) GL11.glEnable(GL11.GL_BLEND);
        if (scissorWasOn) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        if (cullWasOn) GL11.glEnable(GL11.GL_CULL_FACE);
        GL20.glUseProgram(prevProgram);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL30.glBindVertexArray(prevVao);
    }
}
