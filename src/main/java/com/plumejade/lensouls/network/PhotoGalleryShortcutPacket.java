package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.photogallery.PhotoGalleryAttachments;
import com.plumejade.lensouls.photogallery.PhotoGalleryData;
import com.plumejade.lensouls.photogallery.PhotoGallerySettlement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：快捷功能两项（清空 / 剔除重复）。
 * <p>
 * 与操作包分开，是因为这两条都是「一次重扫 + 批量搬运」，语义上不属于「点某个格子」；
 * 结算同样只在服务端做（客户端只用回执里的件数弹提示）。
 */
public record PhotoGalleryShortcutPacket(int action) implements CustomPacketPayload {

    /** 清空当前装备的全部照片饰品 */
    public static final int ACTION_CLEAR_EQUIPPED = 0;
    /** 剔除重复的照片饰品 */
    public static final int ACTION_DEDUPE = 1;

    public static final CustomPacketPayload.Type<PhotoGalleryShortcutPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "photo_gallery_shortcut"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoGalleryShortcutPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeVarInt(packet.action()),
                    buf -> new PhotoGalleryShortcutPacket(buf.readVarInt()));

    @Override
    @NotNull
    public CustomPacketPayload.Type<PhotoGalleryShortcutPacket> type() {
        return TYPE;
    }

    public static void handle(PhotoGalleryShortcutPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                if (!(context.player() instanceof ServerPlayer player)) return;
                PhotoGalleryData data = PhotoGalleryAttachments.get(player);

                int moved;
                String toastKey;
                if (packet.action() == ACTION_CLEAR_EQUIPPED) {
                    moved = PhotoGallerySettlement.clearEquipped(player);
                    toastKey = "gui.lensouls.photos.toast.cleared";
                } else if (packet.action() == ACTION_DEDUPE) {
                    moved = PhotoGallerySettlement.dedupe(player);
                    toastKey = "gui.lensouls.photos.toast.deduped";
                } else {
                    return;
                }

                PacketDistributor.sendToPlayer(player,
                        PhotoGallerySyncPacket.withToast(data, toastKey, moved));
            } catch (Throwable t) {
                LenSouls.LOGGER.error("[PhotoGallery] 快捷功能处理失败 action={}", packet.action(), t);
            }
        });
    }
}
