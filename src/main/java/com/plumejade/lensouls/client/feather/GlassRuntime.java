package com.plumejade.lensouls.client.feather;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「磨砂玻璃」预计算运行时：对主画面做两趟 GPU 高斯（水平 → temp → 垂直 → per-radius FBO）。
 * <p>
 * 移植来源：{@code plumestweaks} 的 {@code RiftGlassRuntime}（其架构照搬 ReGlass，MIT）。
 * <b>刻意不用</b> Minecraft 的 {@code ShaderInstance}/{@code PostPass} —— 参考项目在 GUI 阶段
 * 实测踩过三种失败（GPU 反馈回路 / Ortho 矩阵 NPE / 背景 blit 后消失）。这里全部走原生
 * {@code org.lwjgl.opengl.*} + 全屏三角带 {@code glDrawArrays}，绕开 RenderSystem 的 shader 全局态，
 * 并逐项保存/恢复 GL 状态，确保不破坏原版渲染。
 * <p>
 * <b>UBO 上传的致命坑（原样保留）</b>：LWJGL 原生调用只接受 <b>direct</b> 缓冲；
 * 若用 {@code ByteBuffer.allocate}（堆缓冲）传给 {@code glBufferSubData}，驱动会拿到无效指针
 * ⇒ {@code EXCEPTION_ACCESS_VIOLATION} ⇒ JVM 秒崩（参考项目 hs_err 崩在 atio6axx.dll）。
 * 因此两处上传都走 {@link org.lwjgl.system.MemoryStack}。
 */
public final class GlassRuntime {

    private static final GlassRuntime INSTANCE = new GlassRuntime();

    public static GlassRuntime get() {
        return INSTANCE;
    }

    private static final int MAX_RADIUS = 64;

    /** UBO 绑定点 */
    private static final int SI_BINDING = 0;
    private static final int CFG_BINDING = 1;

    private int program = -1;
    private int uSampler = -1;

    private int uboSamplerInfo = -1;
    private int uboConfigX = -1;
    private int uboConfigY = -1;

    private int quadVao = -1;
    private int quadVbo = -1;

    /** 中间目标（水平 pass 的输出 = 垂直 pass 的输入） */
    private RenderTarget tempFb;

    /** per-radius 输出 */
    private final Map<Integer, RenderTarget> outputByRadius = new HashMap<>();

    private List<Integer> requestedRadii = List.of();

    private GlassRuntime() {
    }

    /** 由玻璃合成前调用：确定本帧需要哪几档半径 */
    public void setRequestedRadii(List<Integer> ordered) {
        this.requestedRadii = ordered == null ? List.of() : List.copyOf(ordered);
    }

    /** 执行两趟高斯（逐项保存/恢复 GL 状态，不改变任何可观察渲染状态） */
    public void run() {
        initProgram();
        initUbos();
        initQuad();

        var mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        if (w <= 0 || h <= 0) return;

        ensureTempFb(w, h);
        uploadSamplerInfo(w, h);

        int max = requestedRadii.size();
        if (max == 0) {
            requestedRadii = List.of(4);
            max = 1;
        }

        // ── 保存 GL 状态 ─────────────────────────────────────────
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTexture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean depthWasOn = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendWasOn = GL11.glIsEnabled(GL11.GL_BLEND);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);

        GL20.glUseProgram(program);
        GL20.glUniform1i(uSampler, 0); // DiffuseSampler = texture unit 0
        GL31.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, SI_BINDING, uboSamplerInfo);

        for (int k = 0; k < max; k++) {
            int radius = requestedRadii.get(k);
            if (radius <= 0) continue;

            ensureOutputFb(w, h, radius);

            // Pass 1：水平模糊（主画面 → temp）
            uploadConfig(uboConfigX, 1.0F, 0.0F, radius);
            GL31.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, CFG_BINDING, uboConfigX);
            tempFb.bindWrite(true);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, main.getColorTextureId());
            GL30.glBindVertexArray(quadVao);
            GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);

            // Pass 2：垂直模糊（temp → output[radius]）
            uploadConfig(uboConfigY, 0.0F, 1.0F, radius);
            GL31.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, CFG_BINDING, uboConfigY);
            RenderTarget output = outputByRadius.get(radius);
            output.bindWrite(true);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tempFb.getColorTextureId());
            GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        }

        GL30.glBindVertexArray(prevVao);

        // ── 恢复 GL 状态 ─────────────────────────────────────────
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture0);
        GL13.glActiveTexture(prevActiveTexture);
        GL20.glUseProgram(prevProgram);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        if (depthWasOn) GL11.glEnable(GL11.GL_DEPTH_TEST);
        if (blendWasOn) GL11.glEnable(GL11.GL_BLEND);
    }

    /** 取某个 radius 的模糊纹理；未算过的返回 -1 */
    public int getBlurredTextureForRadius(int radius) {
        RenderTarget fb = outputByRadius.get(radius);
        return fb != null ? fb.getColorTextureId() : -1;
    }

    // ========== 资源初始化（懒加载） ==========

    private static String readResource(String path) {
        try (InputStream is = GlassRuntime.class.getResourceAsStream("/assets/lensouls/" + path)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load resource: " + path, e);
        }
        throw new RuntimeException("Resource not found: " + path);
    }

    private static int compile(int type, String source) {
        int handle = GL20.glCreateShader(type);
        GL20.glShaderSource(handle, source);
        GL20.glCompileShader(handle);
        if (GL20.glGetShaderi(handle, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(handle);
            GL20.glDeleteShader(handle);
            throw new RuntimeException("Shader compile error: " + log);
        }
        return handle;
    }

    private int buildProgram() {
        int vs = compile(GL20.GL_VERTEX_SHADER, readResource("shaders/core/blit_fullscreen.vsh"));
        int fs = compile(GL20.GL_FRAGMENT_SHADER, readResource("shaders/program/rift_blur.fsh"));

        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, vs);
        GL20.glAttachShader(p, fs);
        GL20.glBindAttribLocation(p, 0, "Position"); // 与 VAO 布局一致

        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(p);
            GL20.glDeleteProgram(p);
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
            throw new RuntimeException("Program link error: " + log);
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return p;
    }

    private void initProgram() {
        if (program != -1) return;
        program = buildProgram();
        uSampler = GL20.glGetUniformLocation(program, "DiffuseSampler");

        int siIdx = GL31.glGetUniformBlockIndex(program, "SamplerInfo");
        if (siIdx != GL31.GL_INVALID_INDEX) {
            GL31.glUniformBlockBinding(program, siIdx, SI_BINDING);
        }
        int cfgIdx = GL31.glGetUniformBlockIndex(program, "Config");
        if (cfgIdx != GL31.GL_INVALID_INDEX) {
            GL31.glUniformBlockBinding(program, cfgIdx, CFG_BINDING);
        }
    }

    private void initUbos() {
        if (uboSamplerInfo != -1) return;
        uboSamplerInfo = createUbo(16);
        int cfgSize = 16 + (MAX_RADIUS + 1) * 16;
        uboConfigX = createUbo(cfgSize);
        uboConfigY = createUbo(cfgSize);
    }

    private static int createUbo(int sizeBytes) {
        int handle = GL30.glGenBuffers();
        GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, handle);
        GL30.glBufferData(GL31.GL_UNIFORM_BUFFER, sizeBytes, GL30.GL_DYNAMIC_DRAW);
        GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
        return handle;
    }

    private void initQuad() {
        if (quadVao != -1) return;

        quadVao = GL30.glGenVertexArrays();
        quadVbo = GL30.glGenBuffers();

        GL30.glBindVertexArray(quadVao);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, quadVbo);

        // 单位四角：顶点 shader 把 [0,1] 映射到 NDC [-1,1]
        float[] verts = {
                0.0F, 0.0F, 0.0F,
                1.0F, 0.0F, 0.0F,
                0.0F, 1.0F, 0.0F,
                1.0F, 1.0F, 0.0F
        };
        FloatBuffer buf = org.lwjgl.BufferUtils.createFloatBuffer(verts.length);
        buf.put(verts).flip();
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, buf, GL30.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 12, 0L);
        GL20.glEnableVertexAttribArray(0);

        GL30.glBindVertexArray(0);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
    }

    private void ensureTempFb(int w, int h) {
        if (tempFb != null && tempFb.width == w && tempFb.height == h) return;
        if (tempFb != null) tempFb.destroyBuffers();
        tempFb = allocateFb(w, h);
    }

    private void ensureOutputFb(int w, int h, int radius) {
        RenderTarget fb = outputByRadius.get(radius);
        if (fb != null && fb.width == w && fb.height == h) return;
        if (fb != null) fb.destroyBuffers();
        outputByRadius.put(radius, allocateFb(w, h));
    }

    /** 每趟的输出：TextureTarget（可 resize；MainTarget 的 width/height 是 final） */
    private static RenderTarget allocateFb(int w, int h) {
        RenderTarget fb = new TextureTarget(w, h, false, false);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fb.getColorTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        return fb;
    }

    // ========== 高斯核 ==========

    private static float[] gaussian(int radius) {
        radius = Math.max(0, Math.min(radius, MAX_RADIUS));
        float sigma = radius / 3.0F;
        if (radius == 0) return new float[]{1.0F};

        float[] kernel = new float[radius + 1];
        float sum = 0.0F;
        for (int i = 0; i <= radius; i++) {
            float w = (float) Math.exp(-0.5 * ((float) i * (float) i) / (sigma * sigma));
            kernel[i] = w;
            sum += (i == 0) ? w : (2.0F * w);
        }
        for (int i = 0; i <= radius; i++) kernel[i] /= sum;
        return kernel;
    }

    // ========== UBO 上传（必须用 MemoryStack，见类注释） ==========

    private void uploadSamplerInfo(int w, int h) {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            ByteBuffer buf = stack.malloc(16);
            buf.putFloat((float) w).putFloat((float) h)
                    .putFloat((float) w).putFloat((float) h).flip();

            GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uboSamplerInfo);
            GL30.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, buf);
            GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
        }
    }

    private void uploadConfig(int ubo, float dx, float dy, int radius) {
        radius = Math.max(0, Math.min(radius, MAX_RADIUS));
        float[] weights = gaussian(radius);

        int size = 16 + (MAX_RADIUS + 1) * 16;
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            ByteBuffer buf = stack.malloc(size);

            // vec4 Params: 方向 (dx, dy), 半径, pad
            buf.putFloat(dx).putFloat(dy).putFloat((float) radius).putFloat(0.0F);

            // float Weights[65] —— std140 里每个数组元素占 16 字节
            for (int i = 0; i <= MAX_RADIUS; i++) {
                buf.putFloat((i <= radius) ? weights[i] : 0.0F);
                buf.putFloat(0.0F);
                buf.putFloat(0.0F);
                buf.putFloat(0.0F);
            }
            buf.flip();

            GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
            GL30.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, buf);
            GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
        }
    }
}
