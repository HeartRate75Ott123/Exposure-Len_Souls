package com.plumejade.lensouls.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
 * 照片套装定义加载器。
 * <p>
 * 路径: {@code data/lensouls/photo_set_defs/&lt;任意文件名&gt;.json}
 * <pre>
 * {
 *   "undead": {
 *     "name": "亡灵套",
 *     "desc": "最大生命+50%；1次不死图腾机会",
 *     "tiers": [ { "count": 3, "effects": ["maxhp:0.5", "death_revive:1:12000"] } ]
 *   }
 * }
 * </pre>
 */
public class PhotoSetDefs extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String FOLDER = "photo_set_defs";
    private static Map<String, SetDef> defs = Map.of();

    public PhotoSetDefs() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, SetDef> newMap = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            JsonElement json = entry.getValue();
            if (!json.isJsonObject()) continue;
            JsonObject root = json.getAsJsonObject();

            for (String setId : root.keySet()) {
                JsonObject o = root.getAsJsonObject(setId);
                String name = o.has("name") ? o.get("name").getAsString() : setId;
                String desc = o.has("desc") ? o.get("desc").getAsString() : "";
                List<Tier> tiers = new ArrayList<>();
                if (o.has("tiers") && o.get("tiers").isJsonArray()) {
                    for (JsonElement t : o.getAsJsonArray("tiers")) {
                        if (!t.isJsonObject()) continue;
                        JsonObject to = t.getAsJsonObject();
                        int count = to.has("count") ? to.get("count").getAsInt() : 1;
                        if (count <= 0) {
                            LenSouls.LOGGER.warn("[PhotoSetDefs] 套装 '{}' 的档位 count 必须 >0，已跳过: {}", setId, to);
                            continue;
                        }
                        List<String> effects = new ArrayList<>();
                        if (to.has("effects") && to.get("effects").isJsonArray()) {
                            for (JsonElement e : to.getAsJsonArray("effects")) {
                                if (e.isJsonPrimitive()) effects.add(e.getAsString());
                            }
                        }
                        String when = to.has("when") ? to.get("when").getAsString() : null;
                        tiers.add(new Tier(count, effects, when));
                    }
                }
                newMap.put(setId, new SetDef(setId, name, desc, tiers));
            }
        }

        defs = Map.copyOf(newMap);
        LenSouls.LOGGER.info("[PhotoSetDefs] 加载了 {} 套定义", defs.size());
    }

    public static SetDef get(String setId) {
        return defs.get(setId);
    }

    public static java.util.Collection<SetDef> all() {
        return defs.values();
    }

    /** 全部定义映射（服务端数据包同步用） */
    public static Map<String, SetDef> allMap() {
        return defs;
    }

    /**
     * 客户端接收数据包同步后的缓存填充（多人模式下服务端解析结果经 S2C 同步）。
     */
    public static void setClientCache(Map<String, SetDef> cache) {
        defs = Map.copyOf(cache);
    }

    public record SetDef(String id, String name, String desc, List<Tier> tiers) {}

    public record Tier(int count, List<String> effects, String when) {}
}
