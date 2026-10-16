package com.plumejade.lensouls.photogallery;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 图鉴解锁数据的**唯一写入点**。
 *
 * <h2>1. 出片即解锁（主路径）</h2>
 * {@code ability/PhotoInjector} 在能力窃取成功、写 {@code lensouls:stolen_entity} 的那一处回调
 * {@link #onStolenPhoto}（与 {@code TimecoreCurse.onStolenPhoto} 并排）——两个消费者共用同一份出片事实，
 * 不要各写一套写入逻辑。
 *
 * <h2>2. 登录迁移（旧存档兼容，必须有）</h2>
 * 老玩家的能力窃取照片可能还躺在背包 / 饰品栏 / 相册里，而图鉴数据是本次新加的：
 * 登录时扫一遍存量照片补记解锁，**时间记 0**（最旧）⇒ 排序时自然落到已解锁条目的最后，
 * 不会让老存档的条目凭空顶到「最新首拍」的位置。
 */
public final class PhotoGalleryRecorder {

    private PhotoGalleryRecorder() {
    }

    /** 能力窃取出片成功：记录解锁（只记首次时间）+ 同步镜像 */
    public static void onStolenPhoto(ServerPlayer player, String entityId) {
        if (player == null || entityId == null || entityId.isEmpty()) return;
        try {
            PhotoGalleryData data = PhotoGalleryAttachments.get(player);
            data.record(entityId, player.level().getGameTime());
            data.bumpRevision();
            PhotoGallerySync.send(player);
        } catch (Throwable ignored) {
            // 图鉴记录失败绝不能影响出片本身
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            if (migrateFromExistingPhotos(player)) {
                PhotoGalleryAttachments.get(player).bumpRevision();
            }
        } catch (Throwable ignored) {
        }
        // 无论是否迁移过，登录都推一份全量（客户端镜像是内存态，重连后必然为空）
        PhotoGallerySync.send(player);
    }

    /** 扫存量照片补记解锁；@return true = 有新条目被补记 */
    private static boolean migrateFromExistingPhotos(ServerPlayer player) {
        PhotoGalleryData data = PhotoGalleryAttachments.get(player);
        boolean changed = false;

        // 物品栏 41 格（含快捷栏）+ 副手
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            changed |= migrateStack(data, player.getInventory().getItem(i));
        }
        for (ItemStack off : player.getInventory().offhand) {
            changed |= migrateStack(data, off);
        }

        // 饰品栏（照片槽 / 相册本体 / 相册内容物）
        for (PhotoGalleryRules.SlotRef ref : PhotoGalleryRules.photoCurioSlots(player)) {
            changed |= migrateStack(data, ref.stack());
        }

        return changed;
    }

    private static boolean migrateStack(PhotoGalleryData data, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        boolean changed = false;

        if (stack.getItem() instanceof com.plumejade.lensouls.item.PhotoAlbumItem) {
            for (String id : PhotoGalleryRules.albumPhotoIds(stack)) {
                changed |= data.recordMigration(id);
            }
            return changed;
        }

        String id = PhotoGalleryRules.photoEntityId(stack);
        if (id != null && !id.isEmpty()) {
            changed = data.recordMigration(id);
        }
        return changed;
    }
}
