package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 时空回溯的视觉同步包 (S2C)。
 * <p>
 * {@code phase = 0}：前摇开始（客户端向内拉伸）；
 * {@code phase = 1}：传送已完成（客户端播放落点回弹 + 涟漪）。
 * 前摇时长由服务端 {@code temporalRecallDelayTicks} 控制，客户端只按阶段切片。
 */
public class TemporalRecallFxPacket implements CustomPacketPayload {

    public static final Type<TemporalRecallFxPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "temporal_recall_fx"));

    public static final StreamCodec<FriendlyByteBuf, TemporalRecallFxPacket> STREAM_CODEC =
            StreamCodec.ofMember(TemporalRecallFxPacket::encode, TemporalRecallFxPacket::new);

    public static final byte PHASE_CHARGE = 0;
    public static final byte PHASE_ARRIVE = 1;

    private final byte phase;

    public TemporalRecallFxPacket(byte phase) {
        this.phase = phase;
    }

    private TemporalRecallFxPacket(FriendlyByteBuf buf) {
        this.phase = buf.readByte();
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeByte(phase);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TemporalRecallFxPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Class<?> fx = Class.forName("com.plumejade.lensouls.client.fx.TemporalRecallScreenFx");
                if (packet.phase == PHASE_CHARGE) {
                    fx.getMethod("charge").invoke(null);
                } else {
                    fx.getMethod("arrive").invoke(null);
                }
            } catch (Throwable ignored) {
            }
        });
    }
}
