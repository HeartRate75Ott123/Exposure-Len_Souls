package com.plumejade.lensouls.photogallery;

import com.plumejade.lensouls.network.PhotoGallerySyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 图鉴数据 → 客户端的唯一出口。
 * <p>
 * 走的时机：登录、每次写入（出片记录 / 收藏 / 规则 / 快捷功能）、界面打开时客户端主动拉取。
 * 客户端缓存是**镜像**，任何时候都能被这一份全量包覆盖重建。
 */
public final class PhotoGallerySync {

    private PhotoGallerySync() {
    }

    public static void send(ServerPlayer player) {
        send(player, PhotoGallerySyncPacket.of(PhotoGalleryAttachments.get(player)));
    }

    public static void send(ServerPlayer player, PhotoGallerySyncPacket packet) {
        if (player == null || packet == null) return;
        try {
            PacketDistributor.sendToPlayer(player, packet);
        } catch (Throwable ignored) {
            // 玩家刚好断线之类：同步失败无所谓，下次登录会重发全量
        }
    }
}
