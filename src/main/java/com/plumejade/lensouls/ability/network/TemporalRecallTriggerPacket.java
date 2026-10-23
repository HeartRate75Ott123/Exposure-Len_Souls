package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.handler.TemporalRecallHandler;
import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：手动触发时空回溯（手持回溯照片左键空气）。
 * <p>
 * 查找/消耗统一走 {@link TemporalRecallHandler#consumeTemporalPhoto}，
 * 传送与视觉走 {@link TemporalRecallHandler#beginRecall}（带 0.3s 前摇）。
 */
public class TemporalRecallTriggerPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TemporalRecallTriggerPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "temporal_recall_trigger"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TemporalRecallTriggerPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {},
                    buf -> new TemporalRecallTriggerPacket()
            );

    @Override
    @NotNull
    public CustomPacketPayload.Type<TemporalRecallTriggerPacket> type() {
        return TYPE;
    }

    public static void handle(TemporalRecallTriggerPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            TemporalSnapshot snapshot = TemporalRecallHandler.consumeTemporalPhoto(sp);
            if (snapshot != null) {
                TemporalRecallHandler.beginRecall(sp, snapshot);
            }
        });
    }
}
