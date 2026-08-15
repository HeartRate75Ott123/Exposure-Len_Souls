package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
import io.github.mortuusars.exposure.client.camera.CameraClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererFrameEndMixin {

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
    }
}
