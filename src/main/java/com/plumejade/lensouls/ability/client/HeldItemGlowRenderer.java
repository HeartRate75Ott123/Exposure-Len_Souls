package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.GameType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * 第一人称手持物品发光（FBO 捕获 + composite，仿 ItemGlint）。
 * <p>
 * {@code GameRenderer.renderItemInHand} 里正常渲染手部后，再把手持物品（非空手）渲染进
 * 独立 mask FBO，用元素主色 composite 发光。空手那只跳过（无物品模型，不发光）。
 */
public class HeldItemGlowRenderer extends RenderStateShard {

    public static ShaderInstance glowShader;

    private static RenderTarget maskTarget;
    private static RenderTarget restoreTarget;
    private static boolean capturing;
    private static RenderType quadType;

    /** 是否渲染第一人称手持物发光（第一人称 / 非隐藏 GUI / 非旁观 / 玩家有 Boss 镜魂 effect） */
    public static boolean shouldRender(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return false;
        if (!mc.options.getCameraType().isFirstPerson()) return false;
        if (mc.options.hideGui) return false;
        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return false;
        return BossOutlineColors.fromEntity(p) != null;
    }

    public static boolean isCapturing() { return capturing; }

    public static void beginCapture(Minecraft mc) {
        ensureTarget(mc);
        restoreTarget = mc.getMainRenderTarget();
        maskTarget.setClearColor(0, 0, 0, 0);
        maskTarget.clear(Minecraft.ON_OSX);
        maskTarget.bindWrite(true);
        capturing = true;
    }

    public static void endCapture() {
        if (restoreTarget != null) restoreTarget.bindWrite(false);
        restoreTarget = null;
        capturing = false;
    }

    public static void composite(Minecraft mc, BossOutlineColors colors) {
        if (glowShader == null || maskTarget == null) return;
        var main = mc.getMainRenderTarget();
        if (main == null) return;

        main.bindWrite(true);

        RenderSystem.setShader(() -> glowShader);
        glowShader.setSampler("DiffuseSampler", maskTarget.getColorTextureId());
        setVec4(glowShader, "GlowColor", primaryColor(colors));

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            var consumer = bufferSource.getBuffer(quadType());
            consumer.addVertex(-1, -1, 0).setUv(0, 0);
            consumer.addVertex( 1, -1, 0).setUv(1, 0);
            consumer.addVertex( 1,  1, 0).setUv(1, 1);
            consumer.addVertex(-1,  1, 0).setUv(0, 1);
            bufferSource.endBatch(quadType());
            RenderSystem.disableBlend();
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static float[] primaryColor(BossOutlineColors colors) {
        int c = colors.primaryColor();
        return new float[]{((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f};
    }

    private static void setVec4(ShaderInstance inst, String name, float[] v) {
        var uniform = inst.getUniform(name);
        if (uniform != null && v != null && v.length >= 3) uniform.set(v[0], v[1], v[2], 1f);
    }

    private static RenderType quadType() {
        if (quadType == null) {
            quadType = RenderType.create(
                    "lensouls_held_glow_quad",
                    DefaultVertexFormat.POSITION_TEX,
                    VertexFormat.Mode.QUADS, 256, false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> glowShader))
                            .setDepthTestState(NO_DEPTH_TEST)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(MAIN_TARGET)
                            .createCompositeState(false)
            );
        }
        return quadType;
    }

    private static void ensureTarget(Minecraft mc) {
        var main = mc.getMainRenderTarget();
        if (maskTarget == null || maskTarget.width != main.width || maskTarget.height != main.height) {
            maskTarget = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        }
    }

    public HeldItemGlowRenderer() {
        super("", () -> {}, () -> {});
    }
}
