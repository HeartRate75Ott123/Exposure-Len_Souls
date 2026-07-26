package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 空间扭曲球体描边渲染器。
 * <p>
 * 使用自定义 RenderType（rendertype_lines + MAIN_TARGET）在
 * {@link RenderLevelStageEvent.Stage#AFTER_ENTITIES} 阶段绘制橙黄色渐变线框球体。
 * 仅当空间扭曲激活时渲染，渲染位置为照片拍摄坐标，半径 = BLOCK_INTERACTION_RANGE。
 */
public class SpatialWarpOutlineRenderer {

    /** 经线数量（越多球体越圆滑） */
    private static final int LONGITUDES = 16;
    /** 纬线环数 */
    private static final int LATITUDES = 8;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        // ── 仅空间扭曲激活时渲染 ──
        if (!ClientAbilityCache.isSpatialWarpActive()) return;

        Vec3 center = ClientAbilityCache.getWarpCenter();
        if (center == null) return;

        // 维度检查
        String dimId = ClientAbilityCache.getWarpDimension();
        String currentDim = player.level().dimension().location().toString();
        if (!currentDim.equals(dimId)) return;

        // 半径 = 方块交互距离属性
        double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        float radius = (float) range;

        Vec3 camPos = event.getCamera().getPosition();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // 平移到球心坐标
        poseStack.translate(
                center.x - camPos.x,
                center.y - camPos.y,
                center.z - camPos.z
        );

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        var poseMatrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(WireframeRenderTypes.sphereOutline());

        drawSphereWireframe(poseMatrix, consumer, radius, LONGITUDES, LATITUDES);

        bufferSource.endBatch(WireframeRenderTypes.sphereOutline());
        poseStack.popPose();
    }

    /**
     * 绘制线框球体，颜色从顶部黄色渐变到底部橙色。
     */
    private static void drawSphereWireframe(Matrix4f poseMatrix, VertexConsumer consumer,
                                             float radius, int lons, int lats) {
        // ── 经线（从北极到南极的弧线） ──
        for (int i = 0; i < lons; i++) {
            float theta = (float) (2 * Math.PI * i / lons);
            for (int j = 0; j < lats; j++) {
                float phi1 = (float) (Math.PI * j / lats);
                float phi2 = (float) (Math.PI * (j + 1) / lats);

                float x1 = radius * (float) (Math.sin(phi1) * Math.cos(theta));
                float y1 = radius * (float) Math.cos(phi1);
                float z1 = radius * (float) (Math.sin(phi1) * Math.sin(theta));

                float x2 = radius * (float) (Math.sin(phi2) * Math.cos(theta));
                float y2 = radius * (float) Math.cos(phi2);
                float z2 = radius * (float) (Math.sin(phi2) * Math.sin(theta));

                // 球面法向 = 归一化位置
                float nx1 = x1 / radius, ny1 = y1 / radius, nz1 = z1 / radius;
                float nx2 = x2 / radius, ny2 = y2 / radius, nz2 = z2 / radius;

                // 颜色渐变：顶部(phi=0)黄色 → 底部(phi=PI)橙色
                float t = j / (float) lats;
                int g = (int) (255 - 128 * t);
                int r = 255;
                int b = 0;

                consumer.addVertex(poseMatrix, x1, y1, z1).setColor(r, g, b, 255).setNormal(nx1, ny1, nz1);
                consumer.addVertex(poseMatrix, x2, y2, z2).setColor(r, g, b, 255).setNormal(nx2, ny2, nz2);
            }
        }

        // ── 纬线环 ──
        for (int j = 1; j < lats; j++) {
            float phi = (float) (Math.PI * j / lats);
            float ringY = radius * (float) Math.cos(phi);
            float ringR = radius * (float) Math.sin(phi);

            float t = j / (float) lats;
            int g = (int) (255 - 128 * t);
            int r = 255;
            int b = 0;

            for (int i = 0; i < lons; i++) {
                float theta1 = (float) (2 * Math.PI * i / lons);
                float theta2 = (float) (2 * Math.PI * (i + 1) / lons);

                float x1 = ringR * (float) Math.cos(theta1);
                float z1 = ringR * (float) Math.sin(theta1);
                float x2 = ringR * (float) Math.cos(theta2);
                float z2 = ringR * (float) Math.sin(theta2);

                float len = (float) Math.sqrt(x1 * x1 + ringY * ringY + z1 * z1);
                float nx1 = x1 / len, ny1 = ringY / len, nz1 = z1 / len;
                float len2 = (float) Math.sqrt(x2 * x2 + ringY * ringY + z2 * z2);
                float nx2 = x2 / len2, ny2 = ringY / len2, nz2 = z2 / len2;

                consumer.addVertex(poseMatrix, x1, ringY, z1).setColor(r, g, b, 255).setNormal(nx1, ny1, nz1);
                consumer.addVertex(poseMatrix, x2, ringY, z2).setColor(r, g, b, 255).setNormal(nx2, ny2, nz2);
            }
        }
    }
}
