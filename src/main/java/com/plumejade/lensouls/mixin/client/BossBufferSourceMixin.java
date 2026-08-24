package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.client.BossMaskRenderTypes;
import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.ItemRenderTracker;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net/minecraft/client/renderer/MultiBufferSource$BufferSource", remap = false)
public abstract class BossBufferSourceMixin {

    @Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true, require = 0)
    private void lensouls$wrapForBoss(RenderType renderType, CallbackInfoReturnable<VertexConsumer> ci) {
        // 第三人称实体描边由 dispatcher 层 BossOutlineBufferSource 双写处理；
        // 此 getBuffer 拦截仅补第一人称手部物品（不走 dispatcher）的 mask 写入通道
        if (!ItemRenderTracker.isRenderingItem()) return;

        if (BossOutlineManager.isInMaskWrite()) return;

        int id = BossOutlineManager.getCaptureEntityId();
        if (id < 0) return;

        VertexConsumer main = ci.getReturnValue();
        if (main == null) return;

        if (renderType.format() != DefaultVertexFormat.NEW_ENTITY) return;


        RenderType maskType = BossMaskRenderTypes.MASK_TYPE_ITEM;

        BossOutlineManager.setInMaskWrite(true);
        try {
            VertexConsumer mask = BossOutlineManager.getMaskBufferSource().getBuffer(maskType);
            ci.setReturnValue(VertexMultiConsumer.create(mask, main));
        } finally {
            BossOutlineManager.setInMaskWrite(false);
        }
    }
}
