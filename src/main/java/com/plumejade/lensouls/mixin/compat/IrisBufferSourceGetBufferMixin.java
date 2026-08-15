package com.plumejade.lensouls.mixin.compat;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.plumejade.lensouls.ability.client.BossMaskRenderTypes;
import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.ItemRenderTracker;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource", remap = false)
public abstract class IrisBufferSourceGetBufferMixin {

    @Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true, require = 0)
    private void lensouls$wrapGetBuffer(RenderType renderType, CallbackInfoReturnable<VertexConsumer> ci) {
        VertexConsumer main = ci.getReturnValue();
        if (main == null) return;
        if (renderType.format() != DefaultVertexFormat.NEW_ENTITY) return;

        // ── BOSS 镜魂捕获（Iris 路径） ──
        if (!BossOutlineManager.isInMaskWrite() && BossOutlineManager.isCapturing()) {
            int bossId = BossOutlineManager.getCaptureEntityId();
            if (bossId >= 0) {
                RenderType maskType = ItemRenderTracker.isRenderingItem()
                        ? BossMaskRenderTypes.MASK_TYPE_ITEM : BossMaskRenderTypes.MASK_TYPE;
                BossOutlineManager.setInMaskWrite(true);
                try {
                    VertexConsumer mask = BossOutlineManager.getMaskBufferSource().getBuffer(maskType);
                    ci.setReturnValue(VertexMultiConsumer.create(mask, main));
                } finally {
                    BossOutlineManager.setInMaskWrite(false);
                }
            }
        }
    }
}
