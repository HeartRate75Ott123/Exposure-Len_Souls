package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 900)
public abstract class GameRendererMixin {

    @Shadow @Final
    private Minecraft minecraft;

    @Inject(method = "renderItemInHand", at = @At("RETURN"), require = 1)
    private void lensouls$endHandRender(Camera camera, float partialTick, Matrix4f modelViewMatrix,
                                          CallbackInfo ci) {
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null) return;
        // 灰度合成已提前到 renderLevel RETURN（LevelRendererMixin），
        // 此处只做描边合成（在灰度与手部渲染之后）
        FrozenOutlineManager.compositeIfNeeded(minecraft, mainTarget);
    }
}
