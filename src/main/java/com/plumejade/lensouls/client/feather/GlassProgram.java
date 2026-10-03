package com.plumejade.lensouls.client.feather;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 「磨砂玻璃合成」原生 GL 程序（不走 Minecraft ShaderInstance）。
 * <p>
 * 移植来源：{@code plumestweaks} 的 {@code RiftGlassGuiProgram}（其源自 ReGlass，
 * {@code LiquidGlassPipelines}，MIT）。最小形态：全屏 quad + {@code Sampler0}（清晰主画面拷贝）
 * + {@code Sampler1}（预计算模糊）+ 矩形遮罩与圆角（{@code Rect}/{@code RadiusPx}/{@code FbSize}）。
 * <p>
 * 之所以不走 Minecraft 的 shader 管线：{@code GuiGraphics} 的 packed 渲染状态在 GUI 阶段与
 * 自定义 uniform/多 Sampler 的组合冲突（参考项目实测踩过三种失败）。
 */
public final class GlassProgram {

    private static int program = -1;

    private GlassProgram() {
    }

    /** @return 原生 OpenGL 程序句柄；失败时 -1（调用方回退为无磨砂面板） */
    public static synchronized int get() {
        if (program == -1) {
            try {
                program = build();
            } catch (Throwable t) {
                com.plumejade.lensouls.LenSouls.LOGGER.error(
                        "[Feather] 玻璃合成 shader 编译失败，面板将退化为无磨砂", t);
                program = -2; // 标记已尝试，避免每帧重试
            }
        }
        return program == -2 ? -1 : program;
    }

    private static int build() throws Exception {
        int vs = compile(GL20.GL_VERTEX_SHADER, read("shaders/core/blit_fullscreen.vsh"));
        int fs = compile(GL20.GL_FRAGMENT_SHADER, read("shaders/program/rift_glass_gui.fsh"));

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
            throw new IllegalStateException("Program link error: " + log);
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return p;
    }

    private static int compile(int type, String source) {
        int handle = GL20.glCreateShader(type);
        GL20.glShaderSource(handle, source);
        GL20.glCompileShader(handle);
        if (GL20.glGetShaderi(handle, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(handle);
            GL20.glDeleteShader(handle);
            throw new IllegalStateException("Shader compile error: " + log);
        }
        return handle;
    }

    private static String read(String path) throws Exception {
        try (InputStream in = GlassProgram.class.getResourceAsStream("/assets/lensouls/" + path)) {
            if (in == null) throw new IllegalStateException("Resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
