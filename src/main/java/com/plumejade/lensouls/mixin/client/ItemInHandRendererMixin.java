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
 * 绗竴浜虹О鎵嬮儴娓叉煋 BOSS mask 鎹曡幏銆? * <p>
 * 鍏ㄧ▼浣跨敤 MASK_TYPE_ITEM锛坅lpha test 鎶犲共鍑€锛夛紝
 * renderArmWithItem 鐨?stack.isEmpty() 鍒ゆ柇褰撳墠鎵嬫槸鍚︿负绌猴紝
 * 绌烘墜鏃朵复鏃跺叧闂?captureEntityId 浠ヨ烦杩?mask 鍐欏叆銆? * 鎸ョ爫涓暣浣撹烦杩囩涓€浜虹О鎹曡幏锛堢敱绗笁浜虹О绠＄嚎澶勭悊锛夛紝閬垮厤蹇€熺Щ鍔ㄤ骇鐢熸弿杈瑰櫔鐐广€? */
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

        // 鎸ョ爫涓烦杩囩涓€浜虹О mask 鎹曡幏锛岄伩鍏嶅揩閫熺Щ鍔ㄧ殑 item 浜х敓鎻忚竟鍣偣
        if (player.getAttackAnim(partialTick) > 0.001f) return;

        BossOutlineColors colors = BossOutlineColors.fromEntity(player);
        if (colors == null) return;

        BossOutlineManager.startCapture(player.getId());

        ItemRenderTracker.beginItemRender();
        BossOutlineManager.setColors(colors);
    }

    /** 鏃跺仠锛氭墜鑷?+ 鎵嬫寔鐗╁搧娓叉煋 buffer 鏇挎崲涓虹帺瀹舵繁搴﹀弻鍐欍€?*/

    // 鈹€鈹€ renderArmWithItem锛歴tack.isEmpty() 鍒ゆ柇绌烘墜锛屼复鏃跺叧闂?capture 鈹€鈹€

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

    // 鈹€鈹€ 甯ф湯 flush 鈹€鈹€

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
