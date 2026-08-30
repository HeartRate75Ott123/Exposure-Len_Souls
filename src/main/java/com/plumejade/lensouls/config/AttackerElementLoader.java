package com.plumejade.lensouls.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.damage.ElementDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 攻击者实体类型 → 元素活性等级映射加载器。
 * <p>
 * 路径: {@code data/lensouls/attacker_element/&lt;任意文件名&gt;.json}
 * <p>
 * 格式：一组数据 = 实体 ID + 元素类型 + 对应等级（0~9 整数；0 = 无活性）。
 * 单元素直接写对象，多元素用数组罗列：
 * <pre>
 * {
 *   "minecraft:blaze": { "element": "fire", "level": 3 },
 *   "cataclysm:ignis": [
 *     { "element": "fire", "level": 5 },
 *     { "element": "earth", "level": 2 }
 *   ]
 * }
 * </pre>
 * 等级 → 活性倍率：1→1.0x, 2→1.5x, 3→2.0x, 4→2.5x, 5→3.0x … 9→5.0x（每级 +0.5）
 */
public class AttackerElementLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String FOLDER = "attacker_element";
    /** 等级上限（数据包允许 0~9） */
    private static final int MAX_LEVEL = 9;
    private static Map<ResourceLocation, Map<ElementDamage, Integer>> mappings = Map.of();

    public AttackerElementLoader() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Map<ElementDamage, Integer>> newMap = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            JsonElement json = entry.getValue();
            if (!json.isJsonObject()) continue;

            JsonObject root = json.getAsJsonObject();
            for (String entityKey : root.keySet()) {
                ResourceLocation entityId;
                try {
                    entityId = ResourceLocation.parse(entityKey);
                } catch (Exception e) {
                    LenSouls.LOGGER.warn("[AttackerElement] 无效实体 ID '{}' (文件: {}): {}", entityKey, entry.getKey(), e.getMessage());
                    continue;
                }

                Map<ElementDamage, Integer> parsed = parseEntries(entityKey, root.get(entityKey), entry.getKey());
                if (!parsed.isEmpty()) {
                    newMap.merge(entityId, parsed, (a, b) -> {
                        Map<ElementDamage, Integer> m = new HashMap<>(a);
                        m.putAll(b);
                        return m;
                    });
                }
            }
        }

        mappings = Map.copyOf(newMap);
        LenSouls.LOGGER.info("[AttackerElement] 加载了 {} 条映射", mappings.size());
    }

    /** 解析单元素对象或多元素数组 */
    private static Map<ElementDamage, Integer> parseEntries(String entityKey, JsonElement value, ResourceLocation sourceFile) {
        List<JsonObject> entries = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement e : value.getAsJsonArray()) {
                if (e.isJsonObject()) entries.add(e.getAsJsonObject());
            }
        } else if (value.isJsonObject()) {
            entries.add(value.getAsJsonObject());
        }

        Map<ElementDamage, Integer> result = new HashMap<>();
        for (JsonObject obj : entries) {
            String elementName = obj.has("element") ? obj.get("element").getAsString() : null;
            if (elementName == null) {
                LenSouls.LOGGER.warn("[AttackerElement] 缺少 element 字段 (实体: {}, 文件: {})", entityKey, sourceFile);
                continue;
            }
            ElementDamage element = ElementDamage.byName(elementName);
            if (element == null) {
                LenSouls.LOGGER.warn("[AttackerElement] 未知元素: {} (实体: {}, 文件: {})", elementName, entityKey, sourceFile);
                continue;
            }
            int level = 0;
            if (obj.has("level")) {
                level = obj.get("level").getAsInt();
            } else if (obj.has("activity")) {
                // 兼容旧字段名（旧值 1.0 按等级 1 处理）
                level = obj.get("activity").getAsInt();
            }
            if (level < 0) {
                LenSouls.LOGGER.warn("[AttackerElement] 等级不能为负 (实体: {}, 文件: {})", entityKey, sourceFile);
                level = 0;
            }
            if (level > MAX_LEVEL) {
                LenSouls.LOGGER.warn("[AttackerElement] 等级 {} 超过上限 9 (实体: {}, 文件: {}), 钳位为 9", level, entityKey, sourceFile);
                level = MAX_LEVEL;
            }
            result.put(element, level);
        }
        return result;
    }

    /** 查询实体类型的元素活性等级（0=无映射/无活性） */
    public static int getLevel(ResourceLocation entityId, ElementDamage element) {
        Map<ElementDamage, Integer> m = mappings.get(entityId);
        if (m == null) return 0;
        return m.getOrDefault(element, 0);
    }

    /** 查询实体类型的元素活性倍率（0=无映射） */
    public static float getActivity(ResourceLocation entityId, ElementDamage element) {
        int level = getLevel(entityId, element);
        return level > 0 ? ElementDamage.getActivityByLevel(level) : 0;
    }

    /** 判断该实体类型是否有任何元素映射 */
    public static boolean hasMapping(ResourceLocation entityId) {
        return mappings.containsKey(entityId);
    }

    /** 获取实体映射的全部元素等级（可能为空 Map） */
    public static Map<ElementDamage, Integer> getLevels(ResourceLocation entityId) {
        return mappings.getOrDefault(entityId, Map.of());
    }

    /** 获取实体映射中等级最高的元素（无映射返回 null） */
    public static ElementDamage getElement(ResourceLocation entityId) {
        Map<ElementDamage, Integer> m = mappings.get(entityId);
        if (m == null || m.isEmpty()) return null;
        ElementDamage best = null;
        int bestLevel = -1;
        for (Map.Entry<ElementDamage, Integer> e : m.entrySet()) {
            if (e.getValue() > bestLevel) {
                bestLevel = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /**
     * 客户端接收数据包同步后的缓存填充（多人模式下服务端解析结果经 S2C 同步）。
     */
    public static void setClientCache(Map<ResourceLocation, Map<ElementDamage, Integer>> cache) {
        mappings = Map.copyOf(cache);
    }

    /** 全量映射（服务端数据包同步用） */
    public static Map<ResourceLocation, Map<ElementDamage, Integer>> allMappings() {
        return mappings;
    }
}