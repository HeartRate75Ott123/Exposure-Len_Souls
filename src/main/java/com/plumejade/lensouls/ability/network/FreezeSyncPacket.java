package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 时间定格状态同步包 (S2C)。
 * <p>
 * 服务端触发/解除时间定格时广播，告知客户端进入/退出冻结状态，
 * 并携带实际定身实体 id 集（韧性目标 30% 判定后的结果）。
 */
public class FreezeSyncPacket implements CustomPacketPayload {

    public static final Type<FreezeSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "freeze_sync"));

    public static final StreamCodec<FriendlyByteBuf, FreezeSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(FreezeSyncPacket::encode, FreezeSyncPacket::new);

    private final boolean frozen;
    private final int[] frozenIds;

    /** 编码端构造器 */
    public FreezeSyncPacket(boolean frozen, int[] frozenIds) {
        this.frozen = frozen;
        this.frozenIds = frozenIds;
    }

    /** 解码端构造器 */
    private FreezeSyncPacket(FriendlyByteBuf buf) {
        this.frozen = buf.readBoolean();
        this.frozenIds = new int[buf.readVarInt()];
        for (int i = 0; i < this.frozenIds.length; i++) {
            this.frozenIds[i] = buf.readVarInt();
        }
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(frozen);
        buf.writeVarInt(frozenIds.length);
        for (int id : frozenIds) {
            buf.writeVarInt(id);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FreezeSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientFreezeCache.updateFreeze(pkt.frozen, pkt.frozenIds));
    }
}
