package com.plumejade.lensouls.client.photogallery;

import com.plumejade.lensouls.client.feather.FeatherToast;
import com.plumejade.lensouls.photogallery.PhotoGalleryData;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 照片图鉴数据的**客户端镜像**（内存态，切世界/断线即清）。
 *
 * <h2>为什么是镜像而不是本地文件</h2>
 * 解锁/收藏/规则是服务端权威数据，且必须「不同存档不污染」。客户端只保留一份由
 * {@code PhotoGallerySyncPacket} 覆盖重建的镜像；退出世界时整体清空，
 * 否则上一存档的图鉴会留到下一存档（这就是跨存档污染）。
 */
public final class PhotoGalleryClientCache {

    private static volatile Map<String, Long> stolen = Map.of();
    private static volatile Set<String> favorites = Set.of();
    private static volatile PhotoGalleryData.ClickRule rule = PhotoGalleryData.ClickRule.GRANT;
    private static volatile int revision = -1;
    private static volatile boolean loaded;

    private PhotoGalleryClientCache() {
    }

    /** 收到服务端全量同步（可在任意时刻被覆盖重建） */
    public static void set(int newRevision, String ruleName,
                           Map<String, Long> newStolen, Set<String> newFavorites,
                           String toastKey, int toastArg) {
        revision = newRevision;
        rule = PhotoGalleryData.ClickRule.byName(ruleName);
        stolen = Map.copyOf(new LinkedHashMap<>(newStolen));
        favorites = Set.copyOf(new LinkedHashSet<>(newFavorites));
        loaded = true;

        if (toastKey != null && !toastKey.isEmpty()) {
            FeatherToast.show(needsArg(toastKey)
                    ? Component.translatable(toastKey, toastArg)
                    : Component.translatable(toastKey));
        }
    }

    /**
     * 只有这两条提示带「件数」。
     * <p>
     * 刻意不做「有 arg 就一律传参」：{@code Component.translatable(key, arg)} 对不含 {@code %s}
     * 的键虽然不会崩，但一旦哪天文案里手滑加了 {@code %s}，就会显示成数字，不如在这里显式区分。
     */
    private static boolean needsArg(String toastKey) {
        return toastKey.endsWith(".cleared") || toastKey.endsWith(".deduped");
    }

    /** 断线 / 切世界：清空镜像（注册在客户端事件总线上） */
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    /** 进入世界时也清一次：避免「退出到主菜单再进另一个存档」这种没有 LoggingOut 的路径 */
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        clear();
        // 提前预热：拼音/双语名索引 + 生物模型实例（后台线程）。
        // 放在这里而不是「开屏时」，是因为 262 个实体要逐个 create()，开屏那一刻往往还没建完 ——
        // 实测症状就是「刚打开图鉴一个模型都没有」，等几秒再开才有。
        try {
            PhotoGalleryCatalog.warmUp();
        } catch (Throwable ignored) {
        }
    }

    public static void clear() {
        stolen = Map.of();
        favorites = Set.of();
        rule = PhotoGalleryData.ClickRule.GRANT;
        revision = -1;
        loaded = false;
    }

    // ========== 查询 ==========

    public static boolean isLoaded() {
        return loaded;
    }

    public static int revision() {
        return revision;
    }

    public static boolean isUnlocked(String entityId) {
        return entityId != null && stolen.containsKey(entityId);
    }

    public static long firstTime(String entityId) {
        Long t = stolen.get(entityId);
        return t == null ? 0L : t;
    }

    public static int unlockedCount() {
        return stolen.size();
    }

    public static Map<String, Long> stolen() {
        return stolen;
    }

    public static boolean isFavoriteEntity(String entityId) {
        return entityId != null && favorites.contains(PhotoGalleryData.FAV_ENTITY + entityId);
    }

    public static boolean isFavoriteSet(String setId) {
        return setId != null && favorites.contains(PhotoGalleryData.FAV_SET + setId);
    }

    public static PhotoGalleryData.ClickRule rule() {
        return rule;
    }
}
