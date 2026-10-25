package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：要害打击命中 → 受击者身上「血线贯穿」。
 * <p>
 * 只带 {@code entityId}，位置由客户端每帧现取（平滑跟随、体型缩放都在客户端算），
 * 与滤镜施加的 {@code FilterAppliedFxPacket} 同一套思路。
 * <p>
 * <b>与削韧反馈共存</b>：本包只是"再补一层红色表现"，不替代
 * {@code ToughnessParticlePacket}（通用削韧粒子）或 {@code ToughnessFeedbackPacket}（屏幕抖动），
 * 三个包互不影响。
 */
public class VitalStrikeFxPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VitalStrikeFxPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "vital_strike_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VitalStrikeFxPacket> STREAM_CODEC =
            StreamCodec.ofMember(VitalStrikeFxPacket::encode, VitalStrikeFxPacket::new);

    private final int entityId;

    public VitalStrikeFxPacket(int entityId) {
        this.entityId = entityId;
    }

    private VitalStrikeFxPacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getEntityId() {
        return entityId;
    }

    /** 广播给追踪该实体的人（出手玩家一定在其列），世界事件 */
    public static void send(LivingEntity target) {
        if (target == null) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new VitalStrikeFxPacket(target.getId()));
    }

    public static void handle(VitalStrikeFxPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                com.plumejade.lensouls.client.ClientPacketHandlers.handleVitalStrikeFx(packet);
            } catch (Throwable ignored) {
            }
        });
    }
}
