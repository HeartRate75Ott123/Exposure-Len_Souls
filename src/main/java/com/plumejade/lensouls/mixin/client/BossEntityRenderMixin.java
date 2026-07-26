package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class BossEntityRenderMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void lensouls$beginBossCapture(LivingEntity entity, float yaw, float partialTick,
                                            PoseStack poseStack, MultiBufferSource bufferSource,
                                            int packedLight, CallbackInfo ci) {
        if (!(entity instanceof Player)) return;
        BossOutlineColors colors = BossOutlineColors.fromEntity(entity);
        if (colors == null) return;

        if (!BossOutlineManager.tryStartCapture(entity.getId())) return;

        BossOutlineManager.setColors(colors);
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void lensouls$endBossCapture(CallbackInfo ci) {
        if (!BossOutlineManager.isCapturing()) return;
        BossOutlineManager.flushMask();
        BossOutlineManager.endCapture();
    }
}
