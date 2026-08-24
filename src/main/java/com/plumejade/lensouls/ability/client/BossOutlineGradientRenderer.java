package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * 玩家 BOSS 镜魂全身描边渐变合成器。
 * <p>
 * 复用原版 outline 管线（实体画进原版 {@code entityTarget}，无 alpha 剔除 → 无洞无噪点），
 * 在 {@code LevelRenderer.doEntityOutline} 中原版 blit 前拦截，读取 {@code entityTarget} 纹理，
 * 用渐变 shader 把 {@link BossOutlineColors#MARKER_COLOR} 标记色轮廓替换为四元素四色渐变描边。
 */
public class BossOutlineGradientRenderer extends RenderStateShard {

    public static ShaderInstance gradientShader;

    private static RenderType quadType;

    public static void render(RenderTarget entityTarget, BossOutlineColors colors, Minecraft mc) {
        if (gradientShader == null || entityTarget == null || mc.level == null) return;
        var main = mc.getMainRenderTarget();
        if (main == null) return;

        main.bindWrite(true);

        RenderSystem.setShader(() -> gradientShader);
        gradientShader.setSampler("DiffuseSampler", entityTarget.getColorTextureId());
        setVec4(gradientShader, "BossColor1", colors.color1());
        setVec4(gradientShader, "BossColor2", colors.color2());
        setVec4(gradientShader, "BossColor3", colors.color3());
        setVec4(gradientShader, "BossColor4", colors.color4());
        setVec4(gradientShader, "MarkerColor", BossOutlineColors.MARKER_RGB);
        if (gradientShader.getUniform("BossGlowStrength") != null)
            gradientShader.getUniform("BossGlowStrength").set(colors.glowStrength());
        // 原版 tick 时间驱动（渐变流动跟随游戏时间）
        if (gradientShader.getUniform("Time") != null) {
            long wrapped = Math.floorMod(mc.level.getGameTime(), 240000L);
            float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
            gradientShader.getUniform("Time").set((wrapped + partialTick) * 0.05f);
        }

        // 全屏四边形以 NDC (-1..1) 直接铺满屏幕，必须用 identity 投影/视图矩阵
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        try {
            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            var consumer = bufferSource.getBuffer(quadType());
            consumer.addVertex(-1, -1, 0).setUv(0, 0);
            consumer.addVertex( 1, -1, 0).setUv(1, 0);
            consumer.addVertex( 1,  1, 0).setUv(1, 1);
            consumer.addVertex(-1,  1, 0).setUv(0, 1);
            bufferSource.endBatch(quadType());
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static RenderType quadType() {
        if (quadType == null) {
            quadType = RenderType.create(
                    "lensouls_boss_gradient_quad",
                    DefaultVertexFormat.POSITION_TEX,
                    VertexFormat.Mode.QUADS, 256, false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> gradientShader))
                            .setDepthTestState(NO_DEPTH_TEST)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(MAIN_TARGET)
                            .createCompositeState(false)
            );
        }
        return quadType;
    }

    private static void setVec4(ShaderInstance inst, String name, float[] v) {
        var uniform = inst.getUniform(name);
        if (uniform != null && v != null && v.length >= 3) {
            uniform.set(v[0], v[1], v[2], 1f);
        }
    }

    public BossOutlineGradientRenderer() {
        super("", () -> {}, () -> {});
    }
}
