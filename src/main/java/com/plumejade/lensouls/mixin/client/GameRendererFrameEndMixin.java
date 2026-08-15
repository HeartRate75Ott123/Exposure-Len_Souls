package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
import io.github.mortuusars.exposure.client.camera.CameraClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.GameType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

        if (ClientFreezeCache.isTimeFrozen()) {
            // 时停发动后自动关闭取景框（Exposure），避免遮挡时停画面
            try {
                if (CameraClient.viewfinder() != null) CameraClient.removeViewfinder();
            } catch (Throwable ignored) {
            }
        }

        FrozenOutlineManager.compositeIfNeeded(minecraft, mainTarget);
        BossOutlineManager.composite(minecraft, mainTarget);

        // 描边合成画在主目标上会盖住第一人称手，重绘手使其位于描边之上（手遮挡描边）
        if (FrozenOutlineManager.wasCompositedThisFrame() || BossOutlineManager.wasCompositedThisFrame()) {
            lensouls$redrawFirstPersonHand();
        }
    }

    /**
     * 帧末重绘第一人称手（复刻原版 {@code GameRenderer.renderItemInHand} 的矩阵设置）。
     * <p>
     * 时停中玩家姿态冻结（tick 冻结），重绘结果与主 pass 一致；灰度链已把场景灰化，
     * 重绘的手为彩色，天然实现"手在描边之上"与"手彩色"两件事。
     */
    @Unique
    private void lensouls$redrawFirstPersonHand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui || mc.screen != null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;
        if (mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;
        if (mc.player.isSleeping()) return;

        try {
            GameRenderer gameRenderer = mc.gameRenderer;
            Camera camera = gameRenderer.getMainCamera();
            float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);

            gameRenderer.resetProjectionMatrix(gameRenderer.getProjectionMatrix(this.getFov(camera, partialTick, false)));
            // 相机旋转共轭矩阵（原版 renderLevel 里构造后传入 renderItemInHand）
            Matrix4f cameraMatrix = new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()));
            PoseStack poseStack = new PoseStack();
            poseStack.pushPose();
            poseStack.mulPose(cameraMatrix.invert(new Matrix4f()));
            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewStack.mul(cameraMatrix);
            RenderSystem.applyModelViewMatrix();

            try {
                mc.gameRenderer.lightTexture().turnOnLightLayer();
                var bufferSource = mc.renderBuffers().bufferSource();
                var player = mc.player;
                int packedLight = mc.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick);
                mc.gameRenderer.itemInHandRenderer.renderHandsWithItems(partialTick, poseStack, bufferSource, player, packedLight);
                bufferSource.endBatch();
                mc.gameRenderer.lightTexture().turnOffLightLayer();
            } finally {
                modelViewStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
                poseStack.popPose();
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Lensouls] 帧末重绘第一人称手失败", t);
        }
    }
}
