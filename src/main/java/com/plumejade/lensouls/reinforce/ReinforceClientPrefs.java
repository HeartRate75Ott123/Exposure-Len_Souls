package com.plumejade.lensouls.reinforce;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 强化界面客户端偏好（收藏列表 / 两个开关 / 上次搜索内容）。
 * <p>
 * 单人存档存到存档目录，多人存到游戏目录（按服务器地址区分）——与参考项目同一套做法，
 * 登录进世界时重新加载，任一改动立即落盘。
 */
public final class ReinforceClientPrefs {

    private static final String FILE_NAME = "lensouls-reinforce-prefs.dat";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_SHOW_REINFORCED = "show_reinforced";
    private static final String KEY_FAVORITES_ONLY = "favorites_only";
    private static final String KEY_LAST_SEARCH = "last_search";

    /** 收藏的材料 ID（LinkedHashSet 保留收藏顺序） */
    private static final Set<String> FAVORITES = new LinkedHashSet<>();
    private static boolean showReinforced = true;
    private static boolean favoritesOnly = false;
    private static String lastSearch = "";
    private static boolean loaded;

    private ReinforceClientPrefs() {
    }

    // ========== 加载 / 保存 ==========

    /** 登录进世界时重置为默认并读取本存档/服务器的偏好文件。 */
    public static void loadForCurrentWorld() {
        FAVORITES.clear();
        showReinforced = true;
        favoritesOnly = false;
        lastSearch = "";
        loaded = false;

        Path file = prefsFile();
        if (file == null || !Files.isRegularFile(file)) {
            loaded = true;
            return;
        }
        try {
            CompoundTag tag = NbtIo.read(file);
            if (tag == null) {
                loaded = true;
                return;
            }
            FAVORITES.clear();
            ListTag list = tag.getList(KEY_FAVORITES, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) FAVORITES.add(list.getString(i));
            showReinforced = !tag.contains(KEY_SHOW_REINFORCED) || tag.getBoolean(KEY_SHOW_REINFORCED);
            favoritesOnly = tag.getBoolean(KEY_FAVORITES_ONLY);
            lastSearch = tag.getString(KEY_LAST_SEARCH);
        } catch (IOException | RuntimeException e) {
            LenSouls.LOGGER.warn("[Reinforce] 读取客户端偏好失败: {}", e.toString());
        }
        loaded = true;
    }

    private static void save() {
        Path file = prefsFile();
        if (file == null) return;
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (String id : FAVORITES) list.add(StringTag.valueOf(id));
            tag.put(KEY_FAVORITES, list);
            tag.putBoolean(KEY_SHOW_REINFORCED, showReinforced);
            tag.putBoolean(KEY_FAVORITES_ONLY, favoritesOnly);
            tag.putString(KEY_LAST_SEARCH, lastSearch);
            NbtIo.write(tag, file);
        } catch (IOException | RuntimeException e) {
            LenSouls.LOGGER.warn("[Reinforce] 保存客户端偏好失败: {}", e.toString());
        }
    }

    /** 单人 → 存档目录；多人 → 游戏目录下按服务器地址区分的文件。 */
    private static Path prefsFile() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return null;
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            return minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
        }
        String key = "unknown";
        if (minecraft.getCurrentServer() != null && minecraft.getCurrentServer().ip != null) {
            key = "server_" + minecraft.getCurrentServer().ip.replaceAll("[^a-zA-Z0-9_.-]", "_");
        }
        return FMLPaths.GAMEDIR.get().resolve(key + "-" + FILE_NAME);
    }

    // ========== 收藏 ==========

    public static boolean isFavorite(ResourceLocation materialId) {
        return FAVORITES.contains(materialId.toString());
    }

    /** 切换收藏状态（立即落盘），返回切换后是否为收藏。 */
    public static boolean toggleFavorite(ResourceLocation materialId) {
        String key = materialId.toString();
        boolean nowFavorite;
        if (FAVORITES.remove(key)) {
            nowFavorite = false;
        } else {
            FAVORITES.add(key);
            nowFavorite = true;
        }
        save();
        return nowFavorite;
    }

    public static Set<String> favorites() {
        return new HashSet<>(FAVORITES);
    }

    // ========== 开关 / 搜索内容 ==========

    /** 是否显示已强化过的材料格。 */
    public static boolean showReinforced() {
        return showReinforced;
    }

    public static void setShowReinforced(boolean value) {
        showReinforced = value;
        save();
    }

    /** 是否仅显示收藏。 */
    public static boolean favoritesOnly() {
        return favoritesOnly;
    }

    public static void setFavoritesOnly(boolean value) {
        favoritesOnly = value;
        save();
    }

    public static String lastSearch() {
        return lastSearch;
    }

    /** 关闭界面 / 搜索内容变化时记录（避免每次按键都落盘，仅退出或切换开关时保存）。 */
    public static void setLastSearch(String value) {
        lastSearch = value == null ? "" : value;
    }

    public static void persist() {
        save();
    }

    public static boolean isLoaded() {
        return loaded;
    }
}
