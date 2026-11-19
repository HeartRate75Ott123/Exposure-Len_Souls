package com.plumejade.lensouls.mixin.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 去掉拍摄时取景框的「挥击后坐」动画。
 * <p>
 * 现象：右键拍一张，整个取景框会缩一下（90%/80%）、绕 Z 轴转 5°、再往下平移 60px，看起来像框在
 * 旋转前伸。原因在上游：{@code CameraItem.use} 拍照成功返回 {@code InteractionResultHolder.success}，
 * 原版 {@code Minecraft.startUseItem} 据此播放<b>手臂挥击动画</b>（{@code player.attackAnim}），
 * 而 Exposure 的 {@code ViewfinderOverlay.applyAttackAnimation} 把这段挥击投影到取景框上。
 * <p>
 * 我们不需要「拍照=挥一下」的表现，直接在源头把这段投影取消：取景框保持静止，快门黑闪不变。
 */
@Mixin(targets = "io.github.mortuusars.exposure.client.camera.viewfinder.ViewfinderOverlay", remap = false)
public abstract class ExposureViewfinderRecoilMixin {

    @Inject(method = "applyAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void lensouls$noRecoil(PoseStack poseStack, DeltaTracker deltaTracker, CallbackInfo ci) {
        // lenosouls: 取景框不再跟随挥击动画（拍照后坐）——用户 2026-11-20 反馈该表现不合理
        ci.cancel();
    }
}
