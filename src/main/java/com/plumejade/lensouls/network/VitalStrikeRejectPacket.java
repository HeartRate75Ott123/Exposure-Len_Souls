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
 * S2C：要害打击「白霸体挡下」——让客户端弹一次那一下反馈。
 * <p>
 * 触发口径（服务端聚合，两条出口同一套）：视锥/照片帧内<b>只要有一只</b>真的被削到韧性就不发；
 * 一只都没削到、且至少有一只被白霸体挡下，才发。定身中 / 倍率被扣成 0 服务端根本不发。
 * <p>
 * <b>唯一字段 {@code cross}</b> 决定弹哪一种：
 * <ul>
 *   <li>{@code true} —— 赛博朋克故障叉号（{@code client/hud/VitalDenyOverlay}）；
 *       只在「相机闪光灯槽里装着机械眼」<b>且</b>当前能力不属于那四个静默能力时成立。</li>
 *   <li>{@code false} —— 原版虚弱小图标（{@code client/hud/HyperArmorDenyIcon}），默认形态。</li>
 * </ul>
 * 判断放服务端而不是客户端，是因为只有服务端知道"这一下是不是白霸体挡的、有没有别的 BOSS 真吃到",
 * 客户端只看得到自己手上的相机。
 */
public class VitalStrikeRejectPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VitalStrikeRejectPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "vital_strike_reject"));

    /** 项目约定：不要用 {@code StreamCodec.unit()}（编解码实例不一致），空包也走 ofMember */
    public static final StreamCodec<RegistryFriendlyByteBuf, VitalStrikeRejectPacket> STREAM_CODEC =
            StreamCodec.ofMember((packet, buf) -> buf.writeBoolean(packet.cross),
                    buf -> new VitalStrikeRejectPacket(buf.readBoolean()));

    /** true = 大故障叉号；false = 原版虚弱小图标 */
    private final boolean cross;

    private VitalStrikeRejectPacket(boolean cross) {
        this.cross = cross;
    }

    public boolean cross() {
        return cross;
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 只发给出手者：旁观者不该跟着看到提示。
     *
     * @param cross true = 大故障叉号（装了机械眼），false = 原版虚弱小图标
     */
    public static void send(ServerPlayer player, boolean cross) {
        if (player == null) return;
        PacketDistributor.sendToPlayer(player, new VitalStrikeRejectPacket(cross));
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
