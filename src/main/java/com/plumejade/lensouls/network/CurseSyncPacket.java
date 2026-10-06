package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.client.CurseClientCache;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import com.plumejade.lensouls.feather.CurseStateData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：把「反转位掩码 + 逐条进度」同步给客户端。
 * <p>
 * 客户端不持有权威数据（{@link CurseStateData} 是纯服务端附件），但物品 tooltip
 * 必须显示「已反转 N / M」与逐条进度，所以这里推一份只读镜像到
 * {@link CurseClientCache}。
 * <p>
 * 编码只发<b>非 0 的进度项</b>（绝大多数条目长期为 0），一次同步通常几十字节。
 */
public class CurseSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CurseSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "curse_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CurseSyncPacket> STREAM_CODEC =
            StreamCodec.of(CurseSyncPacket::encode, CurseSyncPacket::decode);

    private final long mask;
    private final long[] progress;

    public CurseSyncPacket(long mask, long[] progress) {
        this.mask = mask;
        this.progress = progress;
    }

    public long mask() {
        return mask;
    }

    public long[] progress() {
        return progress;
    }

    private static void encode(RegistryFriendlyByteBuf buf, CurseSyncPacket packet) {
        buf.writeLong(packet.mask);
        int nonZero = 0;
        for (long value : packet.progress) {
            if (value != 0L) nonZero++;
        }
        buf.writeVarInt(nonZero);
        for (int i = 0; i < packet.progress.length; i++) {
            if (packet.progress[i] == 0L) continue;
            buf.writeVarInt(i);
            buf.writeLong(packet.progress[i]);
        }
    }

    private static CurseSyncPacket decode(RegistryFriendlyByteBuf buf) {
        long mask = buf.readLong();
        int nonZero = buf.readVarInt();
        long[] progress = new long[CurseDefs.MASK_BITS];
        for (int k = 0; k < nonZero; k++) {
            int index = buf.readVarInt();
            long value = buf.readLong();
            if (index >= 0 && index < progress.length) progress[index] = value;
        }
        return new CurseSyncPacket(mask, progress);
    }

    /** 服务端把该玩家的当前状态推给本人 */
    public static void send(ServerPlayer player) {
        CurseStateData data = CurseManager.state(player);
        PacketDistributor.sendToPlayer(player, new CurseSyncPacket(data.mask(), data.progressCopy()));
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<CurseSyncPacket> type() {
        return TYPE;
    }

    public static void handle(CurseSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> CurseClientCache.set(packet.mask, packet.progress));
    }
}
