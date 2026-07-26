package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：手动触发时空回溯（手持回溯照片左键空气）。
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


            // 扫描背包找第一张回溯照片
            for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                ItemStack stack = sp.getInventory().getItem(i);
                TemporalSnapshot snapshot = TemporalSnapshot.fromPhoto(stack);
                if (snapshot != null) {
                    stack.shrink(1);
                    snapshot.apply(sp);
                    return;
                }
            }

        });
    }
}
