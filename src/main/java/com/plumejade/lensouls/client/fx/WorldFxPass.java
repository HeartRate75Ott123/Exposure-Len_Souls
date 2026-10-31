package com.plumejade.lensouls.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.plumejade.lensouls.ability.client.GravityTetherRenderer;
import com.plumejade.lensouls.ability.client.SpatialWarpOutlineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

/**
 * 世界空间特效的统一绘制入口（纯客户端）。
 *
 * <h3>为什么在 AFTER_LEVEL 画</h3>
 * 这是本模组解决 "自定义着色器 + Iris 光影" 的唯一干净方案：
 * Iris 只会在"世界渲染进行中"把未知着色器的帧缓冲抢回 gbuffer；
 * 在 {@code LevelRenderer.renderLevel} 返回之后（{@code AFTER_LEVEL}）它已经
 * {@code finalizeLevelRendering}，抢缓冲的逻辑不再生效，于是自定义着色器的
 * 柔边/脉冲/生长全套效果都能正常落在主目标上——和冻结天穹同一条路径。
 * <p>
 * 此时主目标深度仍是场景深度（原版清深度在手部渲染前），所以 {@code GL_LEQUAL}
 * 深度测试照旧让地形遮挡特效，不写深度则不会污染后续渲染。
 */
@OnlyIn(Dist.CLIENT)
public final class WorldFxPass {

    private static long lastNanos;
    /** 推进中的累计时间（秒），供脉冲/闪烁使用。 */
    public static float time;

    private WorldFxPass() {
    }

    public static void reset() {
        lastNanos = 0L;
        time = 0.0F;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.screen != null) return;

        // 拍照抓帧那一趟是画进抓帧 FBO 的：不能画（否则照片里会有特效），也不能吃掉本帧的绘制机会
        // 抓帧那一趟是画进抓帧 FBO 的：不能画（否则照片里会有特效）
        if (io.github.mortuusars.exposure.client.capture.task.BackgroundScreenshotCaptureTask.isCapturing()) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0E9F, 0.1F);
        lastNanos = now;
        time += dt;

        boolean warp = SpatialWarpOutlineRenderer.wantsRender();
        boolean tether = GravityTetherRenderer.wantsRender();
        boolean bursts = WorldFxBursts.wantsRender();
        boolean staff = Level2StaffFx.wantsRender();
        if (!warp && !tether && !bursts && !staff) return;

        Vec3 camera = event.getCamera().getPosition();
        Matrix4f identity = new Matrix4f();

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(event.getProjectionMatrix()),
                VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();

        var main = mc.getMainRenderTarget();
        main.bindWrite(true);
        RenderSystem.disableCull();
        RenderSystem.disableScissor();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        // 多边形偏移：把特效在深度比较上稍微"拉近"，避免贴着方块/水面时断续（穿模感）
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0F, -2.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (warp) SpatialWarpOutlineRenderer.render(camera, identity, time);
        if (tether) GravityTetherRenderer.render(mc, camera, identity, time, dt, partialTick);
        if (bursts) WorldFxBursts.render(mc, camera, identity, time, dt);
        if (staff) Level2StaffFx.render(mc, camera, identity, time, dt);

        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }
}
