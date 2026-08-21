package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
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

        FrozenOutlineManager.compositeIfNeeded(minecraft, mainTarget);
        BossOutlineManager.composite(minecraft, mainTarget);
        // glint 顶点已在实体渲染 RETURN 时提交（EntityRenderDispatcherMixin），
        // 这里只需把光影下自建 FBO 的内容合成回主画面
        com.plumejade.lensouls.ability.client.GlintFrameBuffer.compositeIfNeeded(minecraft, mainTarget);
    }
}
