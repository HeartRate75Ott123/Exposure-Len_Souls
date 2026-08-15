package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 全局时间定格状态同步包 (S2C)。
 * <p>
 * 服务端在触发/解除时间定格时广播，告知客户端进入/退出冻结状态。
 */
public class FreezeSyncPacket implements CustomPacketPayload {

    public static final Type<FreezeSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "freeze_sync"));

    public static final StreamCodec<ByteBuf, FreezeSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(FreezeSyncPacket::encode, FreezeSyncPacket::new);

    private final boolean frozen;

    /** 编码端构造器 */
    public FreezeSyncPacket(boolean frozen) {
        this.frozen = frozen;
    }

    /** 解码端构造器 */
    private FreezeSyncPacket(ByteBuf buf) {
        this.frozen = buf.readBoolean();
    }

    private void encode(ByteBuf buf) {
        buf.writeBoolean(frozen);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FreezeSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientFreezeCache.setTimeFrozen(pkt.frozen));
    }
}