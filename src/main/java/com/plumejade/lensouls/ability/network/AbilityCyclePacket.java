package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：请求循环切换到下一个已解锁能力。
 */
public class AbilityCyclePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AbilityCyclePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "ability_cycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityCyclePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {},
                    buf -> new AbilityCyclePacket()
            );

    @Override
    @NotNull
    public CustomPacketPayload.Type<AbilityCyclePacket> type() {
        return TYPE;
    }

    public static void handle(AbilityCyclePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                AbilityManager.getInstance().cycleToNextEnabled(sp);
            }
        });
    }
}
