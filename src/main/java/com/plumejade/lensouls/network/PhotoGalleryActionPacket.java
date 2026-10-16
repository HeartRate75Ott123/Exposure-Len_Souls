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
 * C2S：照片图鉴的界面操作（请求同步 / 切收藏 / 切规则 / 点格子）。
 * <p>
 * 客户端只报「做了什么」，校验与结算全在服务端（{@link PhotoGallerySettlement}）——
 * 与 {@code FeatherSlotPacket} 同款写法：包可以被伪造，所以服务端不信任包里的任何状态。
 */
public record PhotoGalleryActionPacket(int action, String key) implements CustomPacketPayload {

    /** 请求全量同步（界面打开时发一次，兜底登录时序 / 断线重连） */
    public static final int ACTION_REQUEST_SYNC = 0;
    /** 切换收藏（key = 收藏键，形如 {@code entity:minecraft:zombie}） */
    public static final int ACTION_TOGGLE_FAVORITE = 1;
    /** 切换点击规则（key = {@code GRANT} / {@code EQUIP}） */
    public static final int ACTION_SET_RULE = 2;
    /** 点击网格槽位（key = 实体 id） */
    public static final int ACTION_CLICK_CELL = 3;

    public static final CustomPacketPayload.Type<PhotoGalleryActionPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "photo_gallery_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoGalleryActionPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.action());
                        buf.writeUtf(packet.key() == null ? "" : packet.key());
                    },
                    buf -> new PhotoGalleryActionPacket(buf.readVarInt(), buf.readUtf()));

    public static PhotoGalleryActionPacket requestSync() {
        return new PhotoGalleryActionPacket(ACTION_REQUEST_SYNC, "");
    }

    public static PhotoGalleryActionPacket toggleFavorite(String favoriteKey) {
        return new PhotoGalleryActionPacket(ACTION_TOGGLE_FAVORITE, favoriteKey);
    }

    public static PhotoGalleryActionPacket setRule(PhotoGalleryData.ClickRule rule) {
        return new PhotoGalleryActionPacket(ACTION_SET_RULE, rule.name());
    }

    public static PhotoGalleryActionPacket clickCell(String entityId) {
        return new PhotoGalleryActionPacket(ACTION_CLICK_CELL, entityId);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<PhotoGalleryActionPacket> type() {
        return TYPE;
    }

    public static void handle(PhotoGalleryActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                if (!(context.player() instanceof ServerPlayer player)) return;
                PhotoGalleryData data = PhotoGalleryAttachments.get(player);
                String toastKey = "";
                int toastArg = 0;

                switch (packet.action()) {
                    case ACTION_REQUEST_SYNC -> {
                        // 无条件全量回发：客户端镜像过期时这是唯一的自愈路径
                    }
                    case ACTION_TOGGLE_FAVORITE -> {
                        if (packet.key().isEmpty()) return;
                        data.toggleFavorite(packet.key());
                        data.bumpRevision();
                    }
                    case ACTION_SET_RULE -> {
                        PhotoGalleryData.ClickRule rule = PhotoGalleryData.ClickRule.byName(packet.key());
                        data.setClickRule(rule);
                        data.bumpRevision();
                        // 规则行本身在客户端即时改写，这里不再弹提示（避免重复打扰）
                    }
                    case ACTION_CLICK_CELL -> {
                        PhotoGallerySettlement.Settlement result =
                                PhotoGallerySettlement.clickCell(player, data, packet.key());
                        toastKey = result.toastKey();
                        toastArg = result.count();
                    }
                    default -> {
                        return;
                    }
                }

                PacketDistributor.sendToPlayer(player, PhotoGallerySyncPacket.withToast(data, toastKey, toastArg));
            } catch (Throwable t) {
                // 任何异常都不能冒泡到服务端主循环（那会直接踢掉玩家）
                LenSouls.LOGGER.error("[PhotoGallery] 操作包处理失败 action={} key={}",
                        packet.action(), packet.key(), t);
            }
        });
    }
}
