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

import java.util.HashMap;
import java.util.Map;

/**
 * 攻击者实体类型 → 元素活性映射加载器。
 * <p>
 * 路径: {@code data/lensouls/attacker_element/&lt;任意文件名&gt;.json}
 * <p>
 * 格式（仿 entity_weakness，从文件内容中读取实体 ID）：
 * <pre>
 * {
 *   "minecraft:blaze": { "element": "fire", "activity": 1.2 },
 *   "irons_spellbooks:fire_elemental": { "element": "fire", "activity": 1.5 },
 *   "cataclysm:ignis": { "element": "fire", "activity": 2.0 }
 * }
 * </pre>
 */
public class AttackerElementLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String FOLDER = "attacker_element";
    private static Map<ResourceLocation, AttackerEntry> mappings = Map.of();

    public AttackerElementLoader() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, AttackerEntry> newMap = new HashMap<>();

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

                try {
                    JsonObject obj = root.getAsJsonObject(entityKey);
                    String elementName = obj.get("element").getAsString();
                    float activity = obj.get("activity").getAsFloat();

                    ElementDamage element = ElementDamage.byName(elementName);
                    if (element == null) {
                        LenSouls.LOGGER.warn("[AttackerElement] 未知元素: {} (文件: {})", elementName, entry.getKey());
                        continue;
                    }
                    if (activity <= 0f) {
                        LenSouls.LOGGER.warn("[AttackerElement] activity <= 0, 跳过: {} (文件: {})", entityKey, entry.getKey());
                        continue;
                    }

                    newMap.put(entityId, new AttackerEntry(element, activity));
                } catch (Exception e) {
                    LenSouls.LOGGER.error("[AttackerElement] 解析失败: {} (文件: {})", entityKey, entry.getKey(), e);
                }
            }
        }

        mappings = Map.copyOf(newMap);
        LenSouls.LOGGER.info("[AttackerElement] 加载了 {} 条映射", mappings.size());
    }

    /** 查询实体类型的元素活性（返回 0 表示无映射） */
    public static float getActivity(ResourceLocation entityId, ElementDamage element) {
        AttackerEntry ae = mappings.get(entityId);
        if (ae != null && ae.element == element) return ae.activity;
        return 0f;
    }

    /** 判断该实体类型是否有任何元素映射 */
    public static boolean hasMapping(ResourceLocation entityId) {
        return mappings.containsKey(entityId);
    }

    /** 获取映射的元素 */
    public static ElementDamage getElement(ResourceLocation entityId) {
        AttackerEntry ae = mappings.get(entityId);
        return ae != null ? ae.element : null;
    }

    private record AttackerEntry(ElementDamage element, float activity) {}
}
