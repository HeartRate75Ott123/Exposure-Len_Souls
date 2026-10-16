package com.plumejade.lensouls.photogallery;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 照片图鉴的玩家附件注册。
 * <p>
 * {@code serializable} —— 随玩家 NBT 持久化（存档/重进都在，且**按存档隔离**）；
 * {@code copyOnDeath} —— 死亡重生时整体复制（否则解锁/收藏/规则全丢）。
 * <p>
 * 与 {@link com.plumejade.lensouls.feather.FeatherAttachments} 同款写法。
 * <b>刻意不做 {@code sync}</b>：客户端镜像走 {@code PhotoGallerySyncPacket}，
 * 这样「什么时候下发、下发什么」都在我们手里（附件 sync 会在每次写入时整包下发，且不好带 toast 信息）。
 */
public final class PhotoGalleryAttachments {

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LenSouls.MODID);

    /** 照片图鉴数据（解锁 / 收藏 / 点击规则） */
    public static final Supplier<AttachmentType<PhotoGalleryData>> GALLERY =
            TYPES.register("photo_gallery",
                    () -> AttachmentType.serializable(PhotoGalleryData::new).copyOnDeath().build());

    private PhotoGalleryAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    /** 取玩家的图鉴数据（缺失时返回默认实例，永不 null）。两端都可调用：服务端是权威，客户端只在被同步后才有内容。 */
    public static PhotoGalleryData get(Player player) {
        return player.getData(GALLERY);
    }
}
