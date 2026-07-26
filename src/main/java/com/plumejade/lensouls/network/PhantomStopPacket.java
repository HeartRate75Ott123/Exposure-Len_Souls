package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * S2C 虚影停止包——通知客户端结束幻灵表演，复位视角。
 */
public class PhantomStopPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PhantomStopPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "phantom_stop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhantomStopPacket> STREAM_CODEC =
            StreamCodec.ofMember(PhantomStopPacket::encode, PhantomStopPacket::new);

    private final UUID playerId;

    public PhantomStopPacket(UUID playerId) {
        this.playerId = playerId;
    }

    private PhantomStopPacket(RegistryFriendlyByteBuf buf) {
        this.playerId = buf.readUUID();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(playerId);
    }

    // ========== Payload 接口 ==========

    @Override
    @NotNull
    public CustomPacketPayload.Type<PhantomStopPacket> type() { return TYPE; }

    public UUID getPlayerId() { return playerId; }
}
