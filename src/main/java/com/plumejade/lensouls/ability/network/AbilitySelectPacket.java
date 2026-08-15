package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：HUD 滚动列表请求切换能力（携带目标能力 ordinal）。
 * 服务端经 {@link AbilityManager#setEnabled} 校验解锁后切换并回同步。
 */
public class AbilitySelectPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AbilitySelectPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "ability_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilitySelectPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, packet -> packet.ordinal,
                    AbilitySelectPacket::new
            );

    private final int ordinal;

    public AbilitySelectPacket(int ordinal) {
        this.ordinal = ordinal;
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<AbilitySelectPacket> type() {
        return TYPE;
    }

    public static void handle(AbilitySelectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                AbilityType[] values = AbilityType.values();
                if (packet.ordinal < 0 || packet.ordinal >= values.length) return;
                AbilityManager.getInstance().setEnabled(sp, values[packet.ordinal]);
            }
        });
    }
}