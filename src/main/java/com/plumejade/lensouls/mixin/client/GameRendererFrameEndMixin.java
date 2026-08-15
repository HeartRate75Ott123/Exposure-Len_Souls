package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
import com.plumejade.lensouls.ability.client.GlintVertexCollector;
import com.plumejade.lensouls.ability.client.GrayOutManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 帧末合成（星空 / glint / 描边）。
 * <p>
 * 全部走"直接绘制"路径（相机投影 + identity modelView，顶点已相机空间）：
 * Iris 光影下绕过批次渲染（自定义 shader 批次在光影下不渲染），
 * 与描边合成同款模式（已验证光影下正常）。
 */
@Mixin(GameRenderer.class)
public class GameRendererFrameEndMixin {

    @Shadow
    private double getFov(Camera camera, float partialTicks, boolean usedForAiming) {
        return 0.0;
    }

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void lensouls$beginFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        FrozenOutlineManager.resetFrame();
        BossOutlineManager.beginFrame();
    }

    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void lensouls$compositeAtFrameEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        var mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null) return;

        // 时停：黑洞星空帧末绘制（覆盖光影天空——定格天空盒优先于光影渲染）
        if (ClientFreezeCache.isTimeFrozen()) {
            lensouls$withCameraProjection(() -> GrayOutManager.renderBlackHoleSky(new Matrix4f()));
        }

        // 状态光效：帧末绘制（绕过 Iris 批次）
        lensouls$withCameraProjection(GlintVertexCollector::flush);

        FrozenOutlineManager.compositeIfNeeded(minecraft, mainTarget);
        BossOutlineManager.composite(minecraft, mainTarget);
    }

    /** 相机透视投影 + identity modelView 下执行绘制（顶点已相机空间）。 */
    @Unique
    private void lensouls$withCameraProjection(Runnable draw) {
        Minecraft mc = Minecraft.getInstance();
        try {
            GameRenderer gr = mc.gameRenderer;
            Camera camera = gr.getMainCamera();
            float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
            gr.resetProjectionMatrix(gr.getProjectionMatrix(this.getFov(camera, partialTick, false)));
            Matrix4fStack stack = RenderSystem.getModelViewStack();
            stack.pushMatrix();
            stack.identity();
            RenderSystem.applyModelViewMatrix();
            try {
                draw.run();
            } finally {
                stack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Lensouls] 帧末直接绘制失败", t);
        }
    }
}
