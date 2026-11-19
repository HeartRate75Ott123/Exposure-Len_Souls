package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** C2S：把饰品栏里指定槽位/下标的相机取到主手并打开取景框。 */
public class CameraDrawPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CameraDrawPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "camera_draw"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraDrawPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, CameraDrawPacket::slotId,
                    ByteBufCodecs.VAR_INT, CameraDrawPacket::index,
                    CameraDrawPacket::new
            );

    private final String slotId;
    private final int index;

    public CameraDrawPacket(String slotId, int index) {
        this.slotId = slotId;
        this.index = index;
    }

    public String slotId() {
        return slotId;
    }

    public int index() {
        return index;
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<CameraDrawPacket> type() {
        return TYPE;
    }

    public static void handle(CameraDrawPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                com.plumejade.lensouls.ability.CameraEquipHandler.toggleFromCurio(sp, packet.slotId, packet.index);
            }
        });
    }
}
