package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.plumejade.lensouls.ability.client.BossMaskRenderTypes;
import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.CaptureState;
import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import com.plumejade.lensouls.ability.client.ItemRenderTracker;
import com.plumejade.lensouls.ability.client.MaskRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net/minecraft/client/renderer/MultiBufferSource$BufferSource", remap = false)
public abstract class BufferSourceGetBufferMixin {

    @Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true)
    private void lensouls$wrapGetBuffer(RenderType renderType, CallbackInfoReturnable<VertexConsumer> ci) {
        VertexConsumer main = ci.getReturnValue();
        if (main == null) return;

        if (renderType.format() != DefaultVertexFormat.NEW_ENTITY) return;

        // ── BOSS 镜魂捕获 ──
        if (!BossOutlineManager.isInMaskWrite() && BossOutlineManager.isCapturing()) {
            int bossId = BossOutlineManager.getCaptureEntityId();
            if (bossId >= 0) {
                boolean useItemMask = ItemRenderTracker.isRenderingItem();
                RenderType maskType = useItemMask ? BossMaskRenderTypes.MASK_TYPE_ITEM : BossMaskRenderTypes.MASK_TYPE;
                BossOutlineManager.setInMaskWrite(true);
                try {
                    VertexConsumer mask = BossOutlineManager.getMaskBufferSource().getBuffer(maskType);
                    ci.setReturnValue(VertexMultiConsumer.create(mask, main));
                } finally {
                    BossOutlineManager.setInMaskWrite(false);
                }
                return;
            }
        }

        // ── 冻结描边捕获 ──
        if (CaptureState.isInMaskWrite()) return;
        int id = CaptureState.getCaptureEntityId();
        if (id < 0 || !ClientFreezeCache.isFrozen(id)) return;

        RenderType maskType = ItemRenderTracker.isRenderingItem()
                ? MaskRenderTypes.MASK_TYPE_ITEM : MaskRenderTypes.MASK_TYPE;

        CaptureState.setInMaskWrite(true);
        try {
            VertexConsumer mask = CaptureState.getMaskBufferSource().getBuffer(maskType);
            ci.setReturnValue(VertexMultiConsumer.create(mask, main));
        } finally {
            CaptureState.setInMaskWrite(false);
        }
    }
}
