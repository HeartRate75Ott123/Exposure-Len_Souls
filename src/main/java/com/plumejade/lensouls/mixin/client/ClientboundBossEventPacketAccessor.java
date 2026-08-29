package com.plumejade.lensouls.mixin.client;

import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

/** 暴露 {@code ClientboundBossEventPacket.id}（当前映射下为 private 无 getter），供防御性拦截使用。 */
@Mixin(ClientboundBossEventPacket.class)
public interface ClientboundBossEventPacketAccessor {

    @Accessor("id")
    UUID lensouls$getId();
}
