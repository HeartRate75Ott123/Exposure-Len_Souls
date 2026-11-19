package com.plumejade.lensouls.ability.camera;

import com.plumejade.lensouls.LenSouls;
import io.github.mortuusars.exposure.network.packet.Packet;
import io.github.mortuusars.exposure.world.camera.CameraId;
import io.github.mortuusars.exposure.world.entity.CameraHolder;
import io.github.mortuusars.exposure.world.entity.CameraOperator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

/** S2C：告诉客户端「现在活跃的相机是某个饰品槽里的那台」。 */
public record CameraCurioSyncPacket(int operatorEntityId, CameraId cameraId, String slotId, int index)
        implements Packet {

    public static final CustomPacketPayload.Type<CameraCurioSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "camera_curio_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraCurioSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CameraCurioSyncPacket::operatorEntityId,
                    CameraId.STREAM_CODEC, CameraCurioSyncPacket::cameraId,
                    ByteBufCodecs.STRING_UTF8, CameraCurioSyncPacket::slotId,
                    ByteBufCodecs.VAR_INT, CameraCurioSyncPacket::index,
                    CameraCurioSyncPacket::new);

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public boolean handle(PacketFlow flow, Player player) {
        if (player.level().getEntity(operatorEntityId) instanceof LivingEntity entity
                && entity instanceof CameraOperator operator
                && entity instanceof CameraHolder holder) {
            operator.setActiveExposureCamera(new CameraInCurio(holder, cameraId, slotId, index));
        }
        return true;
    }
}
