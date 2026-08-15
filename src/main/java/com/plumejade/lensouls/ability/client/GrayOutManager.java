package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * 时间定格视觉管理器。
 * <p>
 * 黑洞星空天空球（renderSky 阶段绘制）：星空铺满后由地形渲染覆盖——
 * 天然只显示天空区域。
 */
@OnlyIn(Dist.CLIENT)
public class GrayOutManager {

    /** 黑洞星空 shader（POSITION_COLOR，时停天空球）。 */
    public static ShaderInstance blackHoleShader;

    // ========== 生命周期（保留调用点兼容：FrozenOutlineManager 帧事件调用） ==========

    public static void frameStart() {
    }

    public static void markActive() {
    }

    // ========== 黑洞星空天空球 ==========

    /** 天空球半径（远于地形视距，深度靠后）。 */
    private static final float SKY_SPHERE_RADIUS = 512.0F;
    private static final int SKY_SPHERE_SEGMENTS = 32;
    private static final int SKY_SPHERE_RINGS = 32;

    /**
     * 渲染黑洞星空天空球（时停时替代原版天空盒）。
     * <p>
     * 球心位于相机（renderSky 的 modelView 无平移），完整闭合球面 360° 覆盖屏幕；
     * fsh 为屏幕空间星空效果，顶点位置只负责提供片元覆盖与深度（球面深度远 → 地形近处覆盖）。
     * 星空动画用墙钟时间驱动（时停中依然旋转）。
     */
    public static void renderBlackHoleSky(Matrix4f modelViewMatrix) {
        if (blackHoleShader == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // 墙钟时间驱动（时停中星空保持旋转）：setShader 前设置，
        // ShaderInstance.apply() 自动上传 GAME_TIME；SCREEN_SIZE 由 apply() 用窗口尺寸上传。
        RenderSystem.setShaderGameTime((long) (System.nanoTime() / 5.0E7), 0.0F);

        drawSkySphere(modelViewMatrix);
    }

    /** 画星空球（主目标）。 */
    public static void drawSkySphere(Matrix4f modelViewMatrix) {
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> blackHoleShader);
        RenderSystem.setShaderTexture(0, ResourceLocation.withDefaultNamespace("textures/misc/white.png"));

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        float radius = SKY_SPHERE_RADIUS;
        int segments = SKY_SPHERE_SEGMENTS;
        int rings = SKY_SPHERE_RINGS;
        for (int ring = 0; ring < rings; ring++) {
            float phi1 = (float) (ring * Math.PI / rings);
            float phi2 = (float) ((ring + 1) * Math.PI / rings);

            for (int seg = 0; seg < segments; seg++) {
                float theta1 = (float) (seg * 2 * Math.PI / segments);
                float theta2 = (float) ((seg + 1) * 2 * Math.PI / segments);

                float x11 = radius * Mth.sin(phi1) * Mth.cos(theta1);
                float y11 = radius * Mth.cos(phi1);
                float z11 = radius * Mth.sin(phi1) * Mth.sin(theta1);

                float x12 = radius * Mth.sin(phi1) * Mth.cos(theta2);
                float y12 = radius * Mth.cos(phi1);
                float z12 = radius * Mth.sin(phi1) * Mth.sin(theta2);

                float x21 = radius * Mth.sin(phi2) * Mth.cos(theta1);
                float y21 = radius * Mth.cos(phi2);
                float z21 = radius * Mth.sin(phi2) * Mth.sin(theta1);

                float x22 = radius * Mth.sin(phi2) * Mth.cos(theta2);
                float y22 = radius * Mth.cos(phi2);
                float z22 = radius * Mth.sin(phi2) * Mth.sin(theta2);

                buffer.addVertex(modelViewMatrix, x11, y11, z11).setColor(0, 0, 0, 255);
                buffer.addVertex(modelViewMatrix, x12, y12, z12).setColor(0, 0, 0, 255);
                buffer.addVertex(modelViewMatrix, x22, y22, z22).setColor(0, 0, 0, 255);

                buffer.addVertex(modelViewMatrix, x11, y11, z11).setColor(0, 0, 0, 255);
                buffer.addVertex(modelViewMatrix, x22, y22, z22).setColor(0, 0, 0, 255);
                buffer.addVertex(modelViewMatrix, x21, y21, z21).setColor(0, 0, 0, 255);
            }
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private GrayOutManager() {
    }
}
