package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.integration.BossPhotoProjHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.bettercombat.network.Packets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BetterCombat 每次完整挥砍开始（服务端 handleAttackRequest，含空手/空挥）触发 Boss 照片弹幕判定。
 */
@Mixin(targets = "net.bettercombat.network.ServerNetwork", remap = false)
public abstract class BetterCombatAttackMixin {

    @Inject(method = "handleAttackRequest", at = @At("HEAD"), remap = false)
    private static void lensouls$onSwing(Packets.C2S_AttackRequest request,
                                         MinecraftServer server,
                                         ServerPlayer player,
                                         ServerGamePacketListenerImpl handler,
                                         CallbackInfo ci) {
        BossPhotoProjHelper.onSwing(player);
    }
}
