package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.ability.client.BossSoulItemState;
import com.plumejade.lensouls.ability.client.ItemRenderTracker;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void lensouls$beginItemRender(PoseStack poseStack, MultiBufferSource bufferSource,
                                           int packedLight, LivingEntity entity,
                                           float limbSwing, float limbSwingAmount,
                                           float partialTick, float ageInTicks,
                                           float netHeadYaw, float headPitch,
                                           CallbackInfo ci) {
        ItemRenderTracker.beginItemRender();

        // 屏幕（背包、箱子等 GUI）中不激活 BOSS 发光 — 防止 BufferBuilder "Not building!" 崩溃
        if (Minecraft.getInstance().screen != null) return;

        BossOutlineColors colors = BossOutlineColors.fromEntity(entity);
        if (colors != null) {
            BossSoulItemState.setActive(colors);
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void lensouls$endItemRender(CallbackInfo ci) {
        BossSoulItemState.clearActive();
        ItemRenderTracker.endItemRender();
    }
}
