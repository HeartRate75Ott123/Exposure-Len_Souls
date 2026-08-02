package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.client.TwistClientCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：同步佩戴者的扭曲值给客户端（供左侧 bar 渲染）。
 * <p>
 * 扭曲值仅在合成/死亡/清零等低频时机变化，变化时发送一次即可。
 */
public class TwistSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TwistSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "twist_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TwistSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TwistSyncPacket::value,
                    TwistSyncPacket::new);

    private final int value;

    public TwistSyncPacket(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /** 服务端发送给指定玩家 */
    public static void send(ServerPlayer player, int value) {
        PacketDistributor.sendToPlayer(player, new TwistSyncPacket(value));
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<TwistSyncPacket> type() {
        return TYPE;
    }

    public static void handle(TwistSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            org.slf4j.LoggerFactory.getLogger("lensouls.twistsync")
                    .info("[TwistSync] received twist={}", packet.value());
            TwistClientCache.set(packet.value());
        });
    }
}
