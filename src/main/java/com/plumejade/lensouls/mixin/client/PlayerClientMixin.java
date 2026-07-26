package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.client.ClientAbilityCache;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端：空间扭曲激活时仅对 {@link LocalPlayer} 膨胀触及距离。
 * <p>
 * {@code instanceof LocalPlayer} 确保不污染集成服务端的 {@code ServerPlayer}，
 * 避免单机模式下全局长手。
 */
@Mixin(Player.class)
public class PlayerClientMixin {

    @Inject(method = "blockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void lensouls$clientBlockRange(CallbackInfoReturnable<Double> cir) {
        if (!(((Object) this) instanceof LocalPlayer)) return;
        if (!ClientAbilityCache.isSpatialWarpActive()) return;
        double needed = ClientAbilityCache.getWarpReachDistance();
        if (needed > cir.getReturnValue()) {
            cir.setReturnValue(needed);
        }
    }

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void lensouls$clientEntityRange(CallbackInfoReturnable<Double> cir) {
        if (!(((Object) this) instanceof LocalPlayer)) return;
        if (!ClientAbilityCache.isSpatialWarpActive()) return;
        double needed = ClientAbilityCache.getWarpReachDistance();
        if (needed > cir.getReturnValue()) {
            cir.setReturnValue(needed);
        }
    }
}
