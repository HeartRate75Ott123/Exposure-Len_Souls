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
 * 伤害类型 → 元素活性映射加载器。
 * <p>
 * 路径: {@code data/lensouls/damage_type_element/&lt;任意文件名&gt;.json}
 * <p>
 * 格式（仿 entity_weakness，从文件内容中读取伤害类型 ID）：
 * <pre>
 * {
 *   "minecraft:player_attack": { "element": "fire", "activity": 2.0 },
 *   "irons_spellbooks:fire_spell": { "element": "fire", "activity": 2.5 }
 * }
 * </pre>
 * activity 在公式中与武器活性同位加算，取值任意。
 * element 可用值：fire、water、earth、ender
 */
public class DamageTypeElementLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String FOLDER = "damage_type_element";
    private static Map<ResourceLocation, DamageTypeEntry> mappings = Map.of();

    public DamageTypeElementLoader() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, DamageTypeEntry> newMap = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            JsonElement json = entry.getValue();
            if (!json.isJsonObject()) continue;

            JsonObject root = json.getAsJsonObject();
            for (String dtKey : root.keySet()) {
                ResourceLocation dtId;
                try {
                    dtId = ResourceLocation.parse(dtKey);
                } catch (Exception e) {
                    LenSouls.LOGGER.warn("[DamageTypeElement] 无效 DamageType ID '{}' (文件: {}): {}", dtKey, entry.getKey(), e.getMessage());
                    continue;
                }

                try {
                    JsonObject obj = root.getAsJsonObject(dtKey);
                    String elementName = obj.get("element").getAsString();
                    float activity = obj.get("activity").getAsFloat();

                    ElementDamage element = ElementDamage.byName(elementName);
                    if (element == null) {
                        LenSouls.LOGGER.warn("[DamageTypeElement] 未知元素: {} (文件: {})", elementName, entry.getKey());
                        continue;
                    }
                    if (activity <= 0f) {
                        LenSouls.LOGGER.warn("[DamageTypeElement] activity <= 0, 跳过: {} (文件: {})", dtKey, entry.getKey());
                        continue;
                    }

                    newMap.put(dtId, new DamageTypeEntry(element, activity));
                } catch (Exception e) {
                    LenSouls.LOGGER.error("[DamageTypeElement] 解析失败: {} (文件: {})", dtKey, entry.getKey(), e);
                }
            }
        }

        mappings = Map.copyOf(newMap);
        LenSouls.LOGGER.info("[DamageTypeElement] 加载了 {} 条映射", mappings.size());
    }

    /** 查询伤害类型的元素活性（返回 0 表示无映射） */
    public static float getActivity(ResourceLocation damageTypeId, ElementDamage element) {
        DamageTypeEntry dte = mappings.get(damageTypeId);
        if (dte != null && dte.element == element) {
            return dte.activity;
        }
        return 0f;
    }

    /** 判断该伤害类型是否有任何元素映射 */
    public static boolean hasMapping(ResourceLocation damageTypeId) {
        return mappings.containsKey(damageTypeId);
    }

    /** 获取映射的元素 */
    public static ElementDamage getElement(ResourceLocation damageTypeId) {
        DamageTypeEntry dte = mappings.get(damageTypeId);
        return dte != null ? dte.element : null;
    }

    /** 该伤害类型在公式中应使用 activity 而非被动 0.1 默认值 */
    public static boolean isActiveMapping(ResourceLocation damageTypeId) {
        return mappings.containsKey(damageTypeId);
    }

    private record DamageTypeEntry(ElementDamage element, float activity) {}
}
