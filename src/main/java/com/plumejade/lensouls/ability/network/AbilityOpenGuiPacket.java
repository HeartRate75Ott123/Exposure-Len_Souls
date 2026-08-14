package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：请求打开能力选择 GUI（手持相机左键触发）。
 */
public class AbilityOpenGuiPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AbilityOpenGuiPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "ability_open_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityOpenGuiPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {},
                    buf -> new AbilityOpenGuiPacket()
            );

    @Override
    @NotNull
    public CustomPacketPayload.Type<AbilityOpenGuiPacket> type() {
        return TYPE;
    }

    public static void handle(AbilityOpenGuiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                PlayerUIMenuType.openUI(sp,
                        ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "ability_gui"));
            }
        });
    }
}