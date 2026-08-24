package com.plumejade.lensouls.config;

import com.google.gson.*;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.*;

/**
 * 复制之魂数据驱动过滤（两套黑白名单，均支持 {@code "all"} 通配）。
 * <p>
 * 扫描 {@code data/lensouls/copysoul_filter/} 下四个文件，支持 {@code /reload} 热重载：
 * <ul>
 *   <li>{@code drop_whitelist.json} / {@code drop_blacklist.json} —— 控制哪些实体死亡掉落复制之魂</li>
 *   <li>{@code copy_whitelist.json} / {@code copy_blacklist.json} —— 控制哪些物品可被复制之魂复制</li>
 * </ul>
 * 每个文件为实体/物品 ID 的 JSON 数组（或对象，键为 ID）；字符串 {@code "all"} 表示全部。
 * <p>
 * 通配语义（以任一过滤组为例）：
 * <ul>
 *   <li>黑名单含 {@code "all"}：默认全禁，白名单中列出的 ID 回加（即“只有这些可以”）；</li>
 *   <li>白名单含 {@code "all"}：默认全许，黑名单中列出的 ID 排除（即“只有这些不行”）；</li>
 *   <li>两者均不含 {@code "all"}：白名单非空则仅白名单可，否则仅排除黑名单。</li>
 * </ul>
 * 默认（四文件为空或白名单为 {@code ["all"]}、黑名单为空）：掉落仅限 200 血以上实体，复制之魂本身不可复制，其余全部允许。
 */
public class CopySoulFilter extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LenSouls.LOGGER;
    private static final String FOLDER = "copysoul_filter";
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String ALL = "all";

    // ===== 掉落过滤 =====
    private static volatile Set<ResourceLocation> dropWhitelist = Set.of();
    private static volatile Set<ResourceLocation> dropBlacklist = Set.of();
    private static volatile boolean dropWhitelistAll = false;
    private static volatile boolean dropBlacklistAll = false;

    // ===== 复制过滤 =====
    private static volatile Set<ResourceLocation> copyWhitelist = Set.of();
    private static volatile Set<ResourceLocation> copyBlacklist = Set.of();
    private static volatile boolean copyWhitelistAll = false;
    private static volatile boolean copyBlacklistAll = false;

    public CopySoulFilter() {
        super(GSON, FOLDER);
    }

    /** 该实体是否被允许掉落复制之魂（已综合掉落黑白名单，调用方仍需满足 200 血以上基础判定） */
    public static boolean isDropAllowed(ResourceLocation entityId) {
        return evaluate(dropWhitelistAll, dropWhitelist, dropBlacklistAll, dropBlacklist, entityId);
    }

    /** 该物品是否可被复制之魂复制（已综合复制黑白名单；复制之魂本身由调用方额外拒绝） */
    public static boolean isCopyAllowed(ResourceLocation itemId) {
        return evaluate(copyWhitelistAll, copyWhitelist, copyBlacklistAll, copyBlacklist, itemId);
    }

    public static int dropWhitelistSize() { return dropWhitelist.size(); }
    public static int dropBlacklistSize() { return dropBlacklist.size(); }
    public static int copyWhitelistSize() { return copyWhitelist.size(); }
    public static int copyBlacklistSize() { return copyBlacklist.size(); }

    private static boolean evaluate(boolean wlAll, Set<ResourceLocation> wl, boolean blAll, Set<ResourceLocation> bl,
                                    ResourceLocation id) {
        if (blAll) {
            // 黑名单全禁，白名单回加
            return wlAll || wl.contains(id);
        }
        if (wlAll) {
            // 白名单全许，黑名单排除
            return !bl.contains(id);
        }
        // 无 all：白名单非空→仅白名单；否则→非黑名单
        if (!wl.isEmpty()) return wl.contains(id);
        return !bl.contains(id);
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> entries,
                         @NotNull ResourceManager manager,
                         @NotNull ProfilerFiller profiler) {

        Set<ResourceLocation> dWl = new HashSet<>(), dBl = new HashSet<>();
        Set<ResourceLocation> cWl = new HashSet<>(), cBl = new HashSet<>();
        boolean[] dWlAll = {false}, dBlAll = {false}, cWlAll = {false}, cBlAll = {false};

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            String path = entry.getKey().getPath(); // e.g. copysoul_filter/drop_whitelist.json
            Set<ResourceLocation> set;
            boolean[] allFlag;
            if (path.endsWith("drop_whitelist.json")) { set = dWl; allFlag = dWlAll; }
            else if (path.endsWith("drop_blacklist.json")) { set = dBl; allFlag = dBlAll; }
            else if (path.endsWith("copy_whitelist.json")) { set = cWl; allFlag = cWlAll; }
            else if (path.endsWith("copy_blacklist.json")) { set = cBl; allFlag = cBlAll; }
            else {
                LOGGER.warn("复制之魂过滤忽略未知文件（仅支持 drop_/copy_ 前缀的 whitelist/blacklist.json）: {}", entry.getKey());
                continue;
            }
            parseList(entry.getValue(), set, allFlag, entry.getKey());
        }

        dropWhitelist = Set.copyOf(dWl);
        dropBlacklist = Set.copyOf(dBl);
        copyWhitelist = Set.copyOf(cWl);
        copyBlacklist = Set.copyOf(cBl);
        dropWhitelistAll = dWlAll[0];
        dropBlacklistAll = dBlAll[0];
        copyWhitelistAll = cWlAll[0];
        copyBlacklistAll = cBlAll[0];
        LOGGER.info("复制之魂过滤加载完成：掉落 白{}黑{}，复制 白{}黑{}",
                dropWhitelistAll ? "all" : dropWhitelist.size(),
                dropBlacklistAll ? "all" : dropBlacklist.size(),
                copyWhitelistAll ? "all" : copyWhitelist.size(),
                copyBlacklistAll ? "all" : copyBlacklist.size());
    }

    private static void parseList(JsonElement json, Set<ResourceLocation> out, boolean[] allFlag, ResourceLocation source) {
        if (json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) parseToken(e.getAsString(), out, allFlag, source);
        } else if (json.isJsonObject()) {
            for (String key : json.getAsJsonObject().keySet()) parseToken(key, out, allFlag, source);
        } else {
            LOGGER.warn("复制之魂过滤文件格式无效（需为数组或对象）: {}", source);
        }
    }

    private static void parseToken(String token, Set<ResourceLocation> out, boolean[] allFlag, ResourceLocation source) {
        if (ALL.equalsIgnoreCase(token)) {
            allFlag[0] = true;
            return;
        }
        try {
            out.add(ResourceLocation.parse(token));
        } catch (Exception e) {
            LOGGER.warn("复制之魂过滤无效 ID '{}' (文件: {}): {}", token, source, e.getMessage());
        }
    }
}
