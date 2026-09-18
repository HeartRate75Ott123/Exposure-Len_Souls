package com.plumejade.lensouls.network;

import com.plumejade.lensouls.reinforce.ReinforceClientData;
import com.plumejade.lensouls.reinforce.ReinforceDataLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C：强化材料持有数量同步包。
 * <p>
 * 数量在服务端权威统计（玩家物品栏 + 饰品栏内精妙背包等内容物 + 超越维度网络存储），
 * 客户端只负责显示（0 红 / &gt;0 绿）与点击上报。
 * <p>
 * 数组下标 = {@link ReinforceDataLoader#getMaterials()} 的顺序（与客户端同一份数据包同步结果）。
 */
public record ReinforceCountsPacket(int[] counts) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ReinforceCountsPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(com.plumejade.lensouls.LenSouls.MODID, "reinforce_counts"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReinforceCountsPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        int[] values = packet.counts();
                        buf.writeVarInt(values.length);
                        for (int value : values) buf.writeVarInt(value);
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        int[] values = new int[size];
                        for (int i = 0; i < size; i++) values[i] = buf.readVarInt();
                        return new ReinforceCountsPacket(values);
                    });

    @Override
    public CustomPacketPayload.Type<ReinforceCountsPacket> type() {
        return TYPE;
    }

    /** 客户端处理器：写入客户端缓存 */
    public static void handle(ReinforceCountsPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ReinforceClientData.setCounts(packet.counts());
            } catch (Throwable t) {
                com.plumejade.lensouls.LenSouls.LOGGER.error("[Reinforce] 数量同步失败", t);
            }
        });
    }
}
