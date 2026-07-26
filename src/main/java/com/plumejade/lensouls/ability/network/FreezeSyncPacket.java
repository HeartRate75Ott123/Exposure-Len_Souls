package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 冻结状态同步包 (S2C)。
 * <p>
 * 服务端在触发/解除时间定格时发送，告知客户端哪些实体被冻结。
 */
public class FreezeSyncPacket implements CustomPacketPayload {

    public static final Type<FreezeSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "freeze_sync"));

    public static final StreamCodec<ByteBuf, FreezeSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(FreezeSyncPacket::encode, FreezeSyncPacket::new);

    private final boolean frozen;
    private final List<Integer> entityIds;

    /** 编码端构造器 */
    public FreezeSyncPacket(boolean frozen, List<Integer> entityIds) {
        this.frozen = frozen;
        this.entityIds = entityIds;
    }

    /** 解码端构造器 */
    private FreezeSyncPacket(ByteBuf buf) {
        this.frozen = buf.readBoolean();
        int size = buf.readInt();
        this.entityIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.entityIds.add(buf.readInt());
        }
    }

    private void encode(ByteBuf buf) {
        buf.writeBoolean(frozen);
        buf.writeInt(entityIds.size());
        for (int id : entityIds) {
            buf.writeInt(id);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FreezeSyncPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (pkt.frozen) {
                ClientFreezeCache.freezeAll(pkt.entityIds);
            } else {
                ClientFreezeCache.unfreezeAll(pkt.entityIds);
            }
        });
    }
}
