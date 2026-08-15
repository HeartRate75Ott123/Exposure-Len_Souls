package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.client.BlackWhitePost;
import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.DeltaTracker;
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
        // 二分：BlackWhitePost.beginFrame() 已禁用
    }

    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void lensouls$compositeAtFrameEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        var mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null) return;

        // 二分：BlackWhitePost.compositeIfNeeded(minecraft, mainTarget) 已禁用
        FrozenOutlineManager.compositeIfNeeded(minecraft, mainTarget);
        BossOutlineManager.composite(minecraft, mainTarget);
    }
}
