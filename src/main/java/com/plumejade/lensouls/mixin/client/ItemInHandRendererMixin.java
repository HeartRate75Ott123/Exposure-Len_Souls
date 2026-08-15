package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.ItemRenderTracker;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称手部渲染 BOSS mask 捕获。
 * <p>
 * 全程使用 MASK_TYPE_ITEM（alpha test 抠干净），
 * renderArmWithItem 的 stack.isEmpty() 判断当前手是否为空，
 * 空手时临时关闭 captureEntityId 以跳过 mask 写入。
 * 挥砍中整体跳过第一人称捕获（由第三人称管线处理），避免快速移动产生描边噪点。
 */
@Mixin(value = ItemInHandRenderer.class, priority = 900)
public abstract class ItemInHandRendererMixin {

    @Unique private static boolean lensouls$needResume = false;

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), require = 1)
    private void lensouls$beforeRenderHands(float partialTick, PoseStack poseStack,
                                             MultiBufferSource.BufferSource bufferSource,
                                             LocalPlayer player, int packedLight,
                                             CallbackInfo ci) {
        lensouls$needResume = false;

        if (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) return;

        // 挥砍中跳过第一人称 mask 捕获，避免快速移动的 item 产生描边噪点
        if (player.getAttackAnim(partialTick) > 0.001f) return;

        BossOutlineColors colors = BossOutlineColors.fromEntity(player);
        if (colors == null) return;

        BossOutlineManager.startCapture(player.getId());

        ItemRenderTracker.beginItemRender();
        BossOutlineManager.setColors(colors);
    }

    /** 时停：手臂 + 手持物品渲染 buffer 替换为玩家深度双写。 */

    // ── renderArmWithItem：stack.isEmpty() 判断空手，临时关闭 capture ──

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), require = 0)
    private void lensouls$beforeArmWithItem(AbstractClientPlayer player, float partialTicks,
                                             float pitch, InteractionHand hand, float swingProgress,
                                             ItemStack stack, float equippedProgress,
                                             PoseStack poseStack, MultiBufferSource buffer,
                                             int combinedLight, CallbackInfo ci) {
        if (stack.isEmpty() && BossOutlineManager.isCapturing()) {
            lensouls$needResume = true;
            BossOutlineManager.endCapture();
        } else {
            lensouls$needResume = false;
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"), require = 0)
    private void lensouls$afterArmWithItem(CallbackInfo ci) {
        if (lensouls$needResume) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                BossOutlineManager.startCapture(mc.player.getId());
            }
            lensouls$needResume = false;
        }
    }

    // ── 帧末 flush ──

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"), require = 1)
    private void lensouls$afterRenderHands(float partialTick, PoseStack poseStack,
                                            MultiBufferSource.BufferSource bufferSource,
                                            LocalPlayer player, int packedLight,
                                            CallbackInfo ci) {
        ItemRenderTracker.endItemRender();

        if (!BossOutlineManager.isCapturing()) return;
        BossOutlineManager.flushMask();
        BossOutlineManager.endCapture();
    }
}
