package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.network.PhotoSwingPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 捕获玩家每次左键攻击意图（含空手/对空气），发送挥击信号到服务端，
 * 触发 Boss 照片弹幕判定（覆盖原版空手连续左键）。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftSwingMixin {

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void lensouls$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        PacketDistributor.sendToServer(new PhotoSwingPacket());
    }
}
