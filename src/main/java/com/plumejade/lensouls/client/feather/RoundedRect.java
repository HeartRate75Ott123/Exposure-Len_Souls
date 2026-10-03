package com.plumejade.lensouls.client.feather;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * 圆角矩形的实际绘制（SDF，走 {@link FeatherShaders#rrect()}）。
 * <p>
 * 每个圆角矩形 = 一个 quad（4 顶点）+ 片元侧求距。顶点只传两样：
 * {@code Position}（姿态坐标系下的位置）与 {@code UV0}（<b>矩形内局部像素坐标</b>，0..w / 0..h）；
 * 尺寸、圆角、填充、渐变、描边全部走 uniform。
 * <p>
 * 移植来源：{@code plumestweaks} 的 {@code RiftDraw}（MIT 血统，见 AGENTS.md）。
 * <b>两个用血换来的坑必须照抄：</b>
 * <ol>
 *   <li><b>矩阵规则</b>：{@code ProjMat} 必须是 GUI 正交（z 1000..21000）、{@code ModelViewMat}
 *       取 {@code RenderSystem} 当前姿态（GameRenderer 已带 translate(0,0,-11000)），
 *       顶点用<b>恒等矩阵</b>写入裸 GUI 坐标。若把投影矩阵当 modelview 传、又把姿态烘进顶点，
 *       -11000 会被应用两次（z=-22000）落在近截体外 ⇒ <b>整块面板被裁、什么都看不见</b>。</li>
 *   <li><b>alpha</b>：面板不透明度必须烘进 {@code FillColor.a}（见 {@code GlassPanel} 的用法），
 *       否则相乘后为 0 ⇒ 只剩描边、面板内部全透明。</li>
 * </ol>
 * shader 未就绪时回退原版 {@link GuiGraphics#fill} 直角矩形，保证界面始终可见。
 *
 * @see RoundedRect#roundedRect
 */
public final class RoundedRect {

    private static boolean warned;

    private RoundedRect() {
    }

    /** 纯色 / 上下渐变圆角矩形 */
    public static void roundedRect(GuiGraphics graphics, int x, int y, int w, int h, float radius,
                                   int fillTop, int fillBottom, int border, float borderWidth) {
        if (w <= 0 || h <= 0) return;

        ShaderInstance shader = FeatherShaders.rrect();
        if (shader == null) {
            warnOnce();
            fallback(graphics, x, y, w, h, fillTop, border, borderWidth);
            return;
        }

        // 直接设 GL 状态（不用 RenderType —— RenderType.create/RenderStateShard 是包私有的）
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();

        RenderSystem.setShader(() -> shader);

        setColor(shader, "FillColor", fillTop, 1.0F);
        setColor(shader, "FillColor2", fillBottom, 1.0F);
        setColor(shader, "BorderColor", border, 1.0F);
        shader.safeGetUniform("Size").set((float) w, (float) h);
        shader.safeGetUniform("Radius").set(radius);
        shader.safeGetUniform("BorderWidth").set(borderWidth);
        shader.safeGetUniform("EdgeSoftness").set(1.0F);
        shader.safeGetUniform("UseTexture").set(0.0F);
        shader.safeGetUniform("Tint").set(1.0F, 1.0F, 1.0F, 0.0F);

        // ★ 矩阵规则严格照抄原版 innerBlit（见类注释的坑 1）
        var win = Minecraft.getInstance().getWindow();
        Matrix4f proj = new Matrix4f().setOrtho(
                0.0F, (float) (win.getWidth() / win.getGuiScale()),
                0.0F, (float) (win.getHeight() / win.getGuiScale()),
                1000.0F, 21000.0F);
        Matrix4f poseMat = new Matrix4f(RenderSystem.getModelViewMatrix());
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, poseMat, proj, win);

        Matrix4f identity = new Matrix4f();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX);
        // UV0 承载「矩形内局部像素坐标」，供片元侧求 SDF
        buffer.addVertex(identity, x, y, 0).setUv(0, 0);
        buffer.addVertex(identity, x, y + h, 0).setUv(0, h);
        buffer.addVertex(identity, x + w, y + h, 0).setUv(w, h);
        buffer.addVertex(identity, x + w, y, 0).setUv(w, 0);

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /** 圆角描边（内部透明） */
    public static void roundedOutline(GuiGraphics graphics, int x, int y, int w, int h,
                                      float radius, int color, float borderWidth) {
        roundedRect(graphics, x, y, w, h, radius, 0, 0, color, borderWidth);
    }

    private static void setColor(ShaderInstance shader, String name, int argb, float alphaScale) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F * alphaScale;
        float r = ((argb >>> 16) & 0xFF) / 255.0F;
        float g = ((argb >>> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        shader.safeGetUniform(name).set(r, g, b, a);
    }

    /** shader 未就绪时只提示一次，避免每帧刷屏 */
    private static void warnOnce() {
        if (!warned) {
            warned = true;
            com.plumejade.lensouls.LenSouls.LOGGER.warn(
                    "[Feather] 圆角 shader 未就绪，回退直角矩形占位绘制");
        }
    }

    private static void fallback(GuiGraphics graphics, int x, int y, int w, int h,
                                 int fill, int border, float borderWidth) {
        if (graphics == null) return;
        graphics.fill(x, y, x + w, y + h, fill);
        if (borderWidth > 0 && (border >>> 24) != 0) {
            graphics.fill(x, y, x + w, y + 1, border);
            graphics.fill(x, y + h - 1, x + w, y + h, border);
            graphics.fill(x, y + 1, x + 1, y + h - 1, border);
            graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        }
    }
}
