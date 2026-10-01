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
 *     "tiers": [ { "effects": ["maxhp:0.5", "death_revive:1:12000"] } ]
 *   }
 * }
 * </pre>
 * <b>{@code count} 可以省略</b>：省略时按<b>动态识别</b>处理 —— 要求张数 = 该套装在
 * {@code photo_set/} 成员表里登记的实体数（{@link PhotoSetLoader#memberCount(String)}），
 * 于是成员表的增删会自动反映到强度上。需要「允许同种照片重复凑数」时才显式写 {@code count}
 * （例如 {@code cinder_swarm} 只有 2 个成员却要求 3 张）；显式写非正数视为无效档位，跳过。
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
                        // 不写 count ⇒ 0 = 动态（= 成员表登记的实体数，见 effectiveCount）
                        int count = to.has("count") ? to.get("count").getAsInt() : 0;
                        if (to.has("count") && count <= 0) {
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

    /**
     * 单个档位。
     *
     * @param count 声明的照片张数：{@code <=0} 表示<b>动态</b>（= 成员表登记的实体数）；
     *              显式写正数时为覆盖值（允许同种照片重复凑数的套装才需要）
     */
    public record Tier(int count, List<String> effects, String when) {}

    /**
     * 档位实际要求的照片张数：显式声明了正数就用它，否则动态取
     * {@link PhotoSetLoader#memberCount(String)}（成员表为空时兜底 1，避免「0 张即触发」）。
     * <p>
     * <b>普通套装不要显式写 count</b>：计数口径是「已安装的<b>不同成员</b>数」（同种照片不会重复计数，
     * 见 {@code PhotoSetRegistry.collectInstalledEntities} 的去重），所以 count 一旦大于成员数，
     * 这个档位就<b>永远凑不齐</b>（曾经的真实 Bug：cinder_swarm / magma_kin / molten_oath）。
     * 只有首领套 {@code boss_barrage} 例外 —— 它按「件数」另行计数（同 Boss 也只算一次），
     * 成员表里没有它，故豁免。这里对「超过成员数」显式告警（每套装只报一次）。
     */
    public static int effectiveCount(String setId, Tier tier) {
        if (tier.count() > 0) {
            int members = PhotoSetLoader.memberCount(setId);
            if (members > 0 && tier.count() > members && !"boss_barrage".equals(setId)
                    && WARNED_UNREACHABLE.add(setId + "#" + tier.count())) {
                LenSouls.LOGGER.warn("[PhotoSetDefs] 套装 '{}' 的 count={} 超过成员数 {}，"
                        + "该档位永远无法激活（同种照片不重复计数）—— 普通套装请省略 count",
                        setId, tier.count(), members);
            }
            return tier.count();
        }
        return Math.max(1, PhotoSetLoader.memberCount(setId));
    }

    /** 已就「显式 count 超过成员数 ⇒ 永远凑不齐」告警过的 套装#count，避免每 tick 刷屏 */
    private static final java.util.Set<String> WARNED_UNREACHABLE = java.util.concurrent.ConcurrentHashMap.newKeySet();
}
