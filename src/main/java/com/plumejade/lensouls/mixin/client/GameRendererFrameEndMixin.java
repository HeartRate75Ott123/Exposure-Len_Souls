package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
import com.plumejade.lensouls.ability.client.GrayPostChain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererFrameEndMixin {

    @Shadow
    private PostChain postEffect;

    @Shadow
    private boolean effectActive;

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
            PostChain chain = GrayPostChain.getOrCreate();
            if (chain != null) {
                this.postEffect = chain;
                this.effectActive = true;
            }
        } else if (this.postEffect != null) {
            this.postEffect = null;
            this.effectActive = false;
        }

        FrozenOutlineManager.compositeIfNeeded(minecraft, mainTarget);
        BossOutlineManager.composite(minecraft, mainTarget);
    }
}
