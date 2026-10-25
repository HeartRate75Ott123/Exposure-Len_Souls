package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：要害打击「白霸体挡下」——让客户端弹那支故障叉号。
 * <p>
 * 空包（无字段）：客户端只负责"播一次叠加层"，不需要知道是哪只 BOSS、也不需要原因
 * （定身中 / 倍率被扣成 0 服务端根本不发）。这样也避免了携带用不到的 entityId。
 * <p>
 * 触发口径由服务端聚合：视锥内<b>只要有一只</b>真的被削到韧性就不发；
 * 一只都没削到、且至少有一只被白霸体挡下，才发。
 */
public class VitalStrikeRejectPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VitalStrikeRejectPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "vital_strike_reject"));

    /** 项目约定：不要用 {@code StreamCodec.unit()}（编解码实例不一致），空包也走 ofMember */
    public static final StreamCodec<RegistryFriendlyByteBuf, VitalStrikeRejectPacket> STREAM_CODEC =
            StreamCodec.ofMember((packet, buf) -> {
            }, buf -> new VitalStrikeRejectPacket());

    public VitalStrikeRejectPacket() {
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 只发给出手者：旁观者不该跟着闪一支叉号 */
    public static void send(ServerPlayer player) {
        if (player == null) return;
        PacketDistributor.sendToPlayer(player, new VitalStrikeRejectPacket());
    }

    public static void handle(VitalStrikeRejectPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                com.plumejade.lensouls.client.ClientPacketHandlers.handleVitalStrikeReject(packet);
            } catch (Throwable ignored) {
            }
        });
    }
}
