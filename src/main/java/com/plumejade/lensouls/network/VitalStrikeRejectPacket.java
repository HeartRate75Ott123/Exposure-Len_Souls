package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：要害打击**没削到韧性**时的负面反馈（miss 粒子 + 一句解释）。
 * <p>
 * 为什么要单独一条：白霸体 / 定身中按快门时，原来除了"没反应"什么都没有，
 * 玩家无法区分"按错了"和"打了但被挡下"。带上 {@code reason} 才能说出具体原因，
 * 而不是一律丢一句"没命中"。
 */
public class VitalStrikeRejectPacket implements CustomPacketPayload {

    /** 0 = 白霸体（无敌窗口内） 1 = 已破韧/定身中 2 = 其它（韧性伤害被扣成 0 等） */
    public static final byte REASON_INVINCIBLE = 0;
    public static final byte REASON_BROKEN = 1;
    public static final byte REASON_BLOCKED = 2;

    public static final CustomPacketPayload.Type<VitalStrikeRejectPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "vital_strike_reject"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VitalStrikeRejectPacket> STREAM_CODEC =
            StreamCodec.ofMember(VitalStrikeRejectPacket::encode, VitalStrikeRejectPacket::new);

    private final int entityId;
    private final byte reason;

    public VitalStrikeRejectPacket(int entityId, byte reason) {
        this.entityId = entityId;
        this.reason = reason;
    }

    private VitalStrikeRejectPacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.reason = buf.readByte();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeByte(reason);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getEntityId() {
        return entityId;
    }

    public byte getReason() {
        return reason;
    }

    /** 只发给出手的玩家：这是"你这一下"的反馈，旁观者看到 miss 粒子就够了（不打扰他们的 HUD） */
    public static void send(ServerPlayer player, LivingEntity target, byte reason) {
        if (player == null || target == null) return;
        PacketDistributor.sendToPlayer(player, new VitalStrikeRejectPacket(target.getId(), reason));
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
