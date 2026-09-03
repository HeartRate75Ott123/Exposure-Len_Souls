package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：数据包解析结果拉取请求（无负载）。
 * <p>
 * 客户端登录进世界后主动发送一次，服务端收到即回发全量
 * {@link DatapackSyncPacket}。作为 OnDatapackSyncEvent / 登录推送之外的兜底，
 * 规避局域网等环境下登录时点事件投递时序不可靠导致客机缓存为空的问题。
 */
public class DatapackSyncRequestPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DatapackSyncRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "datapack_sync_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DatapackSyncRequestPacket> STREAM_CODEC =
            StreamCodec.ofMember(DatapackSyncRequestPacket::encode, DatapackSyncRequestPacket::new);

    private static final DatapackSyncRequestPacket INSTANCE = new DatapackSyncRequestPacket();

    private DatapackSyncRequestPacket() {}

    private DatapackSyncRequestPacket(RegistryFriendlyByteBuf buf) {}

    public static DatapackSyncRequestPacket instance() {
        return INSTANCE;
    }

    private void encode(RegistryFriendlyByteBuf buf) {}

    @Override
    @NotNull
    public CustomPacketPayload.Type<DatapackSyncRequestPacket> type() {
        return TYPE;
    }

    public static void handle(DatapackSyncRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, DatapackSyncPacket.build());
            }
        });
    }
}
