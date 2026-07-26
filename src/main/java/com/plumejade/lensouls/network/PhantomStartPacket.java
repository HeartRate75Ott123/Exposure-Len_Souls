package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.entity.BossPhantomType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * S2C 虚影启动包——通知客户端开始幻灵表演。
 */
public class PhantomStartPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PhantomStartPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "phantom_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhantomStartPacket> STREAM_CODEC =
            StreamCodec.ofMember(PhantomStartPacket::encode, PhantomStartPacket::new);

    private final UUID playerId;
    private final int bossTypeOrdinal;
    private final int lifetimeTicks;
    private final int phantomEntityId;
    private final double phantomX, phantomY, phantomZ;
    private final float phantomYaw;

    public PhantomStartPacket(UUID playerId, BossPhantomType bossType, int lifetimeTicks, int phantomEntityId,
                              double phantomX, double phantomY, double phantomZ, float phantomYaw) {
        this.playerId = playerId;
        this.bossTypeOrdinal = bossType.ordinal();
        this.lifetimeTicks = lifetimeTicks;
        this.phantomEntityId = phantomEntityId;
        this.phantomX = phantomX;
        this.phantomY = phantomY;
        this.phantomZ = phantomZ;
        this.phantomYaw = phantomYaw;
    }

    private PhantomStartPacket(RegistryFriendlyByteBuf buf) {
        this.playerId = buf.readUUID();
        this.bossTypeOrdinal = buf.readInt();
        this.lifetimeTicks = buf.readInt();
        this.phantomEntityId = buf.readInt();
        this.phantomX = buf.readDouble();
        this.phantomY = buf.readDouble();
        this.phantomZ = buf.readDouble();
        this.phantomYaw = buf.readFloat();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(playerId);
        buf.writeInt(bossTypeOrdinal);
        buf.writeInt(lifetimeTicks);
        buf.writeInt(phantomEntityId);
        buf.writeDouble(phantomX);
        buf.writeDouble(phantomY);
        buf.writeDouble(phantomZ);
        buf.writeFloat(phantomYaw);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<PhantomStartPacket> type() { return TYPE; }

    public UUID getPlayerId()               { return playerId; }
    public int getBossTypeOrdinal()         { return bossTypeOrdinal; }
    public int getLifetimeTicks()           { return lifetimeTicks; }
    public int getPhantomEntityId()         { return phantomEntityId; }
    public double getPhantomX()             { return phantomX; }
    public double getPhantomY()             { return phantomY; }
    public double getPhantomZ()             { return phantomZ; }
    public float getPhantomYaw()            { return phantomYaw; }
}
