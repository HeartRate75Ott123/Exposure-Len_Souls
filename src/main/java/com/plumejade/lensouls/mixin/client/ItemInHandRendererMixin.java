package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.ability.client.BossSoulItemState;
import com.plumejade.lensouls.ability.client.ItemRenderTracker;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称手持物品发光（BOSS 镜魂）：
 * 玩家有 Boss 镜魂 effect → 第一人称手持物品经 ItemRenderer 时由 ItemRendererMixin 包裹 SoulGlow 四色渐变。
 * 手部模型不走 ItemRenderer → 手臂天然不描边。
 */
@Mixin(value = ItemInHandRenderer.class, priority = 900)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), require = 1)
    private void lensouls$beforeRenderHands(float partialTick, PoseStack poseStack,
                                             MultiBufferSource.BufferSource bufferSource,
                                             LocalPlayer player, int packedLight,
                                             CallbackInfo ci) {
        if (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) return;

        // 挥砍中跳过，避免快速移动的物品发光产生闪烁
        if (player.getAttackAnim(partialTick) > 0.001f) return;

        BossOutlineColors colors = BossOutlineColors.fromEntity(player);
        if (colors == null) return;

        BossSoulItemState.setActive(colors);
        ItemRenderTracker.beginItemRender();
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"), require = 1)
    private void lensouls$afterRenderHands(float partialTick, PoseStack poseStack,
                                            MultiBufferSource.BufferSource bufferSource,
                                            LocalPlayer player, int packedLight,
                                            CallbackInfo ci) {
        BossSoulItemState.clearActive();
        ItemRenderTracker.endItemRender();
    }
}
