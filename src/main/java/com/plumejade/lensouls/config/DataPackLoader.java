package com.plumejade.lensouls.config;

import com.google.gson.*;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.*;

/**
 * 数据包弱点配置加载器。
 * <p>
 * 扫描 {@code data/lensouls/entity_weakness/*.json}，
 * 解析实体对各元素伤害的倍率缓存。
 * 通过 {@code /reload} 热重载。
 * <p>
 * JSON 格式示例：
 * <pre>
 * {
 *   "minecraft:zombie": {
 *     "fire": 1.5,
 *     "water": 0.5
 *   },
 *   "minecraft:creeper": {
 *     "projectile": 0.0,
 *     "earth": 2.0
 *   }
 * }
 * </pre>
 * <p>
 * 倍率含义：追加伤害 = 原伤害 × 倍率。
 * 倍率为 0 或未配置 = 无追加。
 */
public class DataPackLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LenSouls.LOGGER;
    private static final String FOLDER = "entity_weakness";
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    /** 未在数据包中显式配置的元素弱点默认值（PROJECTILE 除外 = 0） */
    private static final float DEFAULT_WEAKNESS = 0.1f;

    /** 实体 ID → (元素 → 倍率) */
    private static volatile Map<ResourceLocation, Map<ElementDamage, Float>> weaknessCache = Map.of();

    public DataPackLoader() {
        super(GSON, FOLDER);
    }

    // ========== 公开查询 API ==========

    /**
     * 获取某实体对指定元素的弱点倍率。
     * <p>
     * 若数据包已定义该实体的该元素值，则使用数据包值；
     * 否则 FIRE/WATER/EARTH/ENDER 默认 0.1（基础增伤），PROJECTILE 默认 0。
     * <p>
     * 粒子发射受 {@link #getAllWeaknesses} 显式配置控制：
     * 显式配置的弱点才发射 UP 螺旋粒子，默认 0.1 不发射粒子但产生伤害。
     *
     * @param entityId 实体注册名（如 {@code minecraft:zombie}）
     * @param element  元素类型
     * @return 倍率，永远 >= 0
     */
    public static float getWeakness(ResourceLocation entityId, ElementDamage element) {
        Map<ElementDamage, Float> weaknesses = weaknessCache.get(entityId);
        if (weaknesses != null && weaknesses.containsKey(element)) return weaknesses.get(element);
        // 未显式配置：PROJECTILE 默认 0，其余默认 0.1（基础增伤，不发射粒子）
        return element == ElementDamage.PROJECTILE ? 0f : DEFAULT_WEAKNESS;
    }

    /**
     * 获取某实体的全部弱点映射（不可修改）。
     */
    public static Map<ElementDamage, Float> getAllWeaknesses(ResourceLocation entityId) {
        return weaknessCache.getOrDefault(entityId, Map.of());
    }

    /**
     * 获取当前缓存实体数量。
     */
    public static int cachedEntityCount() {
        return weaknessCache.size();
    }

    // ========== JSON 解析 ==========

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> entries,
                         @NotNull ResourceManager manager,
                         @NotNull ProfilerFiller profiler) {

        Map<ResourceLocation, Map<ElementDamage, Float>> newCache = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation fileId = entry.getKey(); // e.g. "entity_weakness/defaults.json"
            JsonElement json = entry.getValue();

            if (!json.isJsonObject()) {
                LOGGER.warn("跳过非 JSON 对象文件: {}", fileId);
                continue;
            }

            JsonObject root = json.getAsJsonObject();
            for (String entityKey : root.keySet()) {
                ResourceLocation entityId;
                try {
                    entityId = ResourceLocation.parse(entityKey);
                } catch (Exception e) {
                    LOGGER.warn("无效实体 ID '{}' (文件: {}): {}", entityKey, fileId, e.getMessage());
                    continue;
                }

                JsonElement weaknessObj = root.get(entityKey);
                if (!weaknessObj.isJsonObject()) continue;

                Map<ElementDamage, Float> elementMap = parseElementMap(weaknessObj.getAsJsonObject(), fileId);

                // 合并：同一实体可在多个文件中出现，后加载的覆盖同名元素
                newCache.merge(entityId, elementMap, (oldMap, newMap) -> {
                    Map<ElementDamage, Float> merged = new HashMap<>(oldMap);
                    merged.putAll(newMap);
                    return merged;
                });
            }
        }

        weaknessCache = Map.copyOf(newCache);
    }

    private static Map<ElementDamage, Float> parseElementMap(JsonObject obj, ResourceLocation sourceFile) {
        Map<ElementDamage, Float> result = new HashMap<>();
        for (String key : obj.keySet()) {
            ElementDamage element = ElementDamage.byName(key);
            if (element == null) {
                LOGGER.warn("未知元素 '{}' (文件: {}), 跳过", key, sourceFile);
                continue;
            }
            try {
                float value = obj.get(key).getAsFloat();
                if (value < 0) {
                    LOGGER.warn("倍率不能为负数 '{}' (文件: {}), 取绝对值", key, sourceFile);
                    value = Math.abs(value);
                }
                result.put(element, value);
            } catch (NumberFormatException | UnsupportedOperationException e) {
                LOGGER.warn("无效倍率值 '{}' (文件: {}): {}", key, sourceFile, e.getMessage());
            }
        }
        return result;
    }
}
