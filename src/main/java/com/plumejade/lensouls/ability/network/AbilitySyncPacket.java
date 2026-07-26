package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.client.ClientAbilityCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：同步能力状态 + 空间扭曲球心坐标。
 */
public class AbilitySyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AbilitySyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "ability_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilitySyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(AbilitySyncPacket::encode, AbilitySyncPacket::new);

    private final int enabledOrdinal;
    private final boolean spatialWarpActive;
    private final double warpX, warpY, warpZ;
    private final String warpDimension;

    public AbilitySyncPacket(int enabledOrdinal, boolean spatialWarpActive,
                             double warpX, double warpY, double warpZ, String warpDimension) {
        this.enabledOrdinal = enabledOrdinal;
        this.spatialWarpActive = spatialWarpActive;
        this.warpX = warpX;
        this.warpY = warpY;
        this.warpZ = warpZ;
        this.warpDimension = warpDimension;
    }

    private AbilitySyncPacket(RegistryFriendlyByteBuf buf) {
        this.enabledOrdinal = buf.readInt();
        this.spatialWarpActive = buf.readBoolean();
        if (this.spatialWarpActive) {
            this.warpX = buf.readDouble();
            this.warpY = buf.readDouble();
            this.warpZ = buf.readDouble();
            this.warpDimension = buf.readUtf();
        } else {
            this.warpX = this.warpY = this.warpZ = 0;
            this.warpDimension = "";
        }
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(enabledOrdinal);
        buf.writeBoolean(spatialWarpActive);
        if (spatialWarpActive) {
            buf.writeDouble(warpX);
            buf.writeDouble(warpY);
            buf.writeDouble(warpZ);
            buf.writeUtf(warpDimension);
        }
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<AbilitySyncPacket> type() {
        return TYPE;
    }

    public static void handle(AbilitySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientAbilityCache.set(packet.enabledOrdinal, packet.spatialWarpActive,
                    packet.warpX, packet.warpY, packet.warpZ, packet.warpDimension);
        });
    }
}
