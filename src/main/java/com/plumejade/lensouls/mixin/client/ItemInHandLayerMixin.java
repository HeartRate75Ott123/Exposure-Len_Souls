package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.ability.client.ItemRenderTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称实体手持物品层：标记 ItemRenderTracker（供 glint/冻结描边按物品类型处理）。
 * 第三人称 BOSS 镜魂全身描边走原版 outline（EntityBossOutlineMixin），此处不再叠加物品 SoulGlow。
 */
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
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void lensouls$endItemRender(CallbackInfo ci) {
        ItemRenderTracker.endItemRender();
    }
}
