package com.plumejade.lensouls.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 照片套装成员归属加载器。
 * <p>
 * 路径: {@code data/lensouls/photo_set/&lt;任意文件名&gt;.json}
 * <pre>
 * { "minecraft:zombie": ["undead"], "cataclysm:draugr": ["undead"] }
 * </pre>
 * 一个实体可属于多套；多文件合并。
 */
public class PhotoSetLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String FOLDER = "photo_set";
    private static Map<ResourceLocation, List<String>> membership = Map.of();
    /** setId → 成员表里登记的实体数；供 {@link PhotoSetDefs} 的「动态 count」使用（随成员表一同重建） */
    private static Map<String, Integer> memberCounts = Map.of();

    public PhotoSetLoader() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, List<String>> newMap = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            JsonElement json = entry.getValue();
            if (!json.isJsonObject()) continue;
            JsonObject root = json.getAsJsonObject();

            for (String entityKey : root.keySet()) {
                ResourceLocation entityId;
                try {
                    entityId = ResourceLocation.parse(entityKey);
                } catch (Exception e) {
                    LenSouls.LOGGER.warn("[PhotoSet] 无效实体 ID '{}' (文件: {}): {}", entityKey, entry.getKey(), e.getMessage());
                    continue;
                }

                JsonElement val = root.get(entityKey);
                List<String> sets = new ArrayList<>();
                if (val.isJsonArray()) {
                    for (JsonElement e : val.getAsJsonArray()) {
                        if (e.isJsonPrimitive()) sets.add(e.getAsString());
                    }
                } else if (val.isJsonPrimitive()) {
                    sets.add(val.getAsString());
                }

                if (!sets.isEmpty()) {
                    newMap.merge(entityId, sets, (a, b) -> {
                        List<String> m = new ArrayList<>(a);
                        for (String s : b) if (!m.contains(s)) m.add(s);
                        return m;
                    });
                }
            }
        }

        membership = Map.copyOf(newMap);
        rebuildMemberCounts();
        LenSouls.LOGGER.info("[PhotoSet] 加载了 {} 条套装成员映射", membership.size());
    }

    /** 查询实体所属套装 id 列表（可能为空） */
    public static List<String> getSets(ResourceLocation entityId) {
        return membership.getOrDefault(entityId, List.of());
    }

    /**
     * 某套装登记了多少个实体 —— <b>「动态 count」的口径</b>：
     * 套装定义里不写 {@code count} 时，要求张数就取这个值，于是成员表的增删会自动反映到强度上。
     */
    public static int memberCount(String setId) {
        return memberCounts.getOrDefault(setId, 0);
    }

    /** 重建 setId → 成员数（成员表每次加载/客户端同步后调用一次） */
    private static void rebuildMemberCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (List<String> sets : membership.values()) {
            for (String setId : sets) {
                counts.merge(setId, 1, Integer::sum);
            }
        }
        memberCounts = Map.copyOf(counts);
    }

    /** 全部成员映射（供 tooltip 反查某套装包含哪些实体） */
    public static Map<ResourceLocation, List<String>> getAll() {
        return membership;
    }

    /**
     * 客户端接收数据包同步后的缓存填充（多人模式下服务端解析结果经 S2C 同步）。
     */
    public static void setClientCache(Map<ResourceLocation, List<String>> cache) {
        membership = Map.copyOf(cache);
        rebuildMemberCounts();
    }
}
