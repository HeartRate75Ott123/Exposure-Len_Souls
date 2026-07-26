package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.client.ClientAbilityCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端：挖掘前拦截非扭曲球内的方块，消除挖掘音效/裂纹闪烁。
 * <p>
 * 仅拦截左键挖掘（有本地预测动画）。右键方块无闪烁问题，不拦截。
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void lensouls$preMineFlicker(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!ClientAbilityCache.isSpatialWarpActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 blockCenter = Vec3.atCenterOf(pos);

        // 在正常触及距离内 → 放行
        double normalRange = mc.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        if (mc.player.distanceToSqr(blockCenter) < normalRange * normalRange) return;

        // 在扭曲球内 → 放行
        if (ClientAbilityCache.isInWarpSphere(blockCenter)) return;

        // 都不在 → 直接拒绝，无音效无裂纹
        cir.setReturnValue(false);
    }
}
