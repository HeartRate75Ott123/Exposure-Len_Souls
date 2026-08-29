package com.plumejade.lensouls.mixin.client;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * 防御性拦截：服务端下发的 boss 条更新包，若客户端 {@code events} 表中没有对应条目（boss 条被移除
 * 或尚未注册时进度包在途），原版会 {@code events.get(id).setXxx()} 抛 NPE 并断线。此处缺失时直接取消
 * 该次更新，避免崩溃。
 * <p>
 * add / remove 操作正常处理（add 会创建条目、remove 会移除条目，缺 id 也安全），仅拦截读取型更新。
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {

    @Shadow
    private Map<UUID, LerpingBossEvent> events;

    private static UUID lensouls$id(ClientboundBossEventPacket packet) {
        return ((ClientboundBossEventPacketAccessor) packet).lensouls$getId();
    }

    @Inject(method = "updateProgress", at = @At("HEAD"), cancellable = true, require = 0)
    private void lensouls$guardProgress(ClientboundBossEventPacket packet, CallbackInfo ci) {
        if (!events.containsKey(lensouls$id(packet))) ci.cancel();
    }

    @Inject(method = "updateName", at = @At("HEAD"), cancellable = true, require = 0)
    private void lensouls$guardName(ClientboundBossEventPacket packet, CallbackInfo ci) {
        if (!events.containsKey(lensouls$id(packet))) ci.cancel();
    }

    @Inject(method = "updateStyle", at = @At("HEAD"), cancellable = true, require = 0)
    private void lensouls$guardStyle(ClientboundBossEventPacket packet, CallbackInfo ci) {
        if (!events.containsKey(lensouls$id(packet))) ci.cancel();
    }

    @Inject(method = "updateProperties", at = @At("HEAD"), cancellable = true, require = 0)
    private void lensouls$guardProperties(ClientboundBossEventPacket packet, CallbackInfo ci) {
        if (!events.containsKey(lensouls$id(packet))) ci.cancel();
    }

    @Inject(method = "updateFlags", at = @At("HEAD"), cancellable = true, require = 0)
    private void lensouls$guardFlags(ClientboundBossEventPacket packet, CallbackInfo ci) {
        if (!events.containsKey(lensouls$id(packet))) ci.cancel();
    }
}
