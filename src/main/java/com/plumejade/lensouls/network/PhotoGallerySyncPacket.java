package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.client.photogallery.PhotoGalleryClientCache;
import com.plumejade.lensouls.photogallery.PhotoGalleryData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * S2C：照片图鉴全量同步（解锁表 + 收藏 + 点击规则 + 版本号），并可顺带捎一条界面内提示。
 *
 * <h2>为什么不用 {@code AttachmentType.sync}</h2>
 * 附件 sync 每次写入都整包下发，且没法附带「清空了几件 / 已装备」这类结算回执；
 * 这里把「数据 + 回执」放进同一个包，界面只需一个入口就能既刷新列表又弹提示。
 *
 * <h2>toast 为什么放在这里</h2>
 * 提示必须画在 GUI 里（动作栏消息会被界面盖住，羽毛页已证），而结算结果只有服务端知道
 * （物品栏是否满、照片槽是否还有空位）。所以让服务端回传 lang 键 + 一个数字参数，
 * 客户端用 {@code FeatherToast} 显示 —— 比再开一个专用包少一条通道。
 */
public class PhotoGallerySyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PhotoGallerySyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "photo_gallery_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoGallerySyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(PhotoGallerySyncPacket::encode, PhotoGallerySyncPacket::new);

    private final int revision;
    private final String rule;
    private final Map<String, Long> stolen;
    private final Set<String> favorites;
    private final String toastKey;
    private final int toastArg;

    public PhotoGallerySyncPacket(int revision, String rule,
                                  Map<String, Long> stolen, Set<String> favorites,
                                  String toastKey, int toastArg) {
        this.revision = revision;
        this.rule = rule;
        this.stolen = stolen;
        this.favorites = favorites;
        this.toastKey = toastKey == null ? "" : toastKey;
        this.toastArg = toastArg;
    }

    /** 无提示的全量包 */
    public static PhotoGallerySyncPacket of(PhotoGalleryData data) {
        return new PhotoGallerySyncPacket(data.revision(), data.clickRule().name(),
                data.stolenSnapshot(), data.favoritesSnapshot(), "", 0);
    }

    /** 带界面提示的全量包 */
    public static PhotoGallerySyncPacket withToast(PhotoGalleryData data, String toastKey, int toastArg) {
        return new PhotoGallerySyncPacket(data.revision(), data.clickRule().name(),
                data.stolenSnapshot(), data.favoritesSnapshot(), toastKey, toastArg);
    }

    private PhotoGallerySyncPacket(RegistryFriendlyByteBuf buf) {
        this.revision = buf.readVarInt();
        this.rule = buf.readUtf();

        int stolenSize = buf.readVarInt();
        Map<String, Long> stolenMap = new LinkedHashMap<>();
        for (int i = 0; i < stolenSize; i++) {
            String id = buf.readUtf();
            long time = buf.readLong();
            stolenMap.put(id, time);
        }
        this.stolen = stolenMap;

        int favSize = buf.readVarInt();
        Set<String> favSet = new LinkedHashSet<>();
        for (int i = 0; i < favSize; i++) favSet.add(buf.readUtf());
        this.favorites = favSet;

        this.toastKey = buf.readUtf();
        this.toastArg = buf.readVarInt();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(revision);
        buf.writeUtf(rule);

        buf.writeVarInt(stolen.size());
        for (Map.Entry<String, Long> e : stolen.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeLong(e.getValue());
        }

        buf.writeVarInt(favorites.size());
        for (String key : favorites) buf.writeUtf(key);

        buf.writeUtf(toastKey);
        buf.writeVarInt(toastArg);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<PhotoGallerySyncPacket> type() {
        return TYPE;
    }

    public static void handle(PhotoGallerySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                PhotoGalleryClientCache.set(packet.revision, packet.rule,
                        packet.stolen, packet.favorites, packet.toastKey, packet.toastArg);
            } catch (Throwable t) {
                LenSouls.LOGGER.error("[PhotoGallery] 同步包处理失败", t);
            }
        });
    }
}
