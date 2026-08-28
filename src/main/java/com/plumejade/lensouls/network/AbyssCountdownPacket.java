package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.client.AbyssCountdownClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：通知客户端显示「祸之可能性」3 秒倒计时（红字，物品栏上方）。
 * <p>
 * 仅作一次性触发标记，不含数据。
 */
public class AbyssCountdownPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AbyssCountdownPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "abyss_countdown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbyssCountdownPacket> STREAM_CODEC =
            StreamCodec.unit(new AbyssCountdownPacket());

    public AbyssCountdownPacket() {
    }

    public static void send(net.minecraft.server.level.ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new AbyssCountdownPacket());
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<AbyssCountdownPacket> type() {
        return TYPE;
    }

    public static void handle(AbyssCountdownPacket packet, IPayloadContext context) {
        context.enqueueWork(AbyssCountdownClient::start);
    }
}
