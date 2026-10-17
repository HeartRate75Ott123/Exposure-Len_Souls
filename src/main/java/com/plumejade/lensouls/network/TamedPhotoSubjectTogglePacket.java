package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.util.TamedPhotoSubject;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：请求切换「驯服过的生物是否纳入相机拍摄主体选取」（默认 {@code [} 键）。
 * <p>
 * 与 {@link BarrageTogglePacket} 同一套做法：不传目标值而是让服务端自己翻转——
 * 开关状态<b>只</b>由服务端持有并裁决（{@code PhotoTargets.subject} /
 * {@code WeaknessLensPhoto.subjectEntityId} 读的都是服务端那份），
 * 客户端那份只用于画状态提示，不参与任何判定。
 * <p>
 * 翻转后立刻回发 {@link TamedPhotoSubjectStatePacket} 让客户端把乐观值对齐到权威值。
 */
public class TamedPhotoSubjectTogglePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TamedPhotoSubjectTogglePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "tamed_photo_subject_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TamedPhotoSubjectTogglePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {},
                    buf -> new TamedPhotoSubjectTogglePacket());

    @Override
    @NotNull
    public CustomPacketPayload.Type<TamedPhotoSubjectTogglePacket> type() {
        return TYPE;
    }

    /** 服务端处理器：翻转持久化状态并回发权威值 */
    public static void handle(TamedPhotoSubjectTogglePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.level().isClientSide) return;
            TamedPhotoSubjectStatePacket.send(player, TamedPhotoSubject.flip(player));
        });
    }
}
