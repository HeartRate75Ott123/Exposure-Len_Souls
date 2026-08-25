package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.client.ClientAbilityCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：手持相机选中能力变更回声（ordinal；-1 表示取消选中）。
 * <p>
 * 客户端据此即时更新「当前手持相机选中镜像」，使 HUD 高亮与滚动校正
 * 不依赖滞后的物品 NBT 同步，彻底避免滚动回拉/方向错乱。
 */
public class CameraAbilitySyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CameraAbilitySyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "camera_ability_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraAbilitySyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, packet -> packet.ordinal,
                    CameraAbilitySyncPacket::new);

    private final int ordinal;

    public CameraAbilitySyncPacket(int ordinal) {
        this.ordinal = ordinal;
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<CameraAbilitySyncPacket> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, int ordinal) {
        PacketDistributor.sendToPlayer(player, new CameraAbilitySyncPacket(ordinal));
    }

    public static void handle(CameraAbilitySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientAbilityCache.setHeldCameraSelected(packet.ordinal));
    }
}
