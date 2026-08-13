package com.plumejade.lensouls.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.damage.ElementDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 物品元素活性数据包加载器。
 * <p>
 * 路径: {@code data/lensouls/item_element_activity/&lt;任意文件名&gt;.json}
 * <p>
 * 格式（值直接填活性等级，0~9 整数；0 = 无活性）：
 * <pre>
 * {
 *   "minecraft:diamond_sword": {
 *     "values": { "lensouls:fire": 3, "lensouls:water": 2 }
 *   },
 *   "twilightforest:fiery_sword": {
 *     "values": { "lensouls:fire": 5 }
 *   }
 * }
 * </pre>
 * 等级 → 活性倍率：1→1.0x, 2→1.5x, 3→2.0x, 4→2.5x, 5→3.0x … 9→5.0x（每级 +0.5）
 */
public class ItemElementActivityLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    /** 等级上限（数据包允许 0~9） */
    private static final int MAX_LEVEL = 9;
    /** itemId → element → level */
    private static Map<ResourceLocation, Map<ElementDamage, Integer>> activityMap = new HashMap<>();

    public ItemElementActivityLoader() {
        super(GSON, "item_element_activity");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Map<ElementDamage, Integer>> newMap = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            JsonElement json = entry.getValue();
            if (!json.isJsonObject()) continue;

            JsonObject root = json.getAsJsonObject();
            for (String itemKey : root.keySet()) {
                ResourceLocation itemId;
                try {
                    itemId = ResourceLocation.parse(itemKey);
                } catch (Exception e) {
                    LenSouls.LOGGER.warn("[ItemElementActivity] 无效物品 ID '{}' (文件: {}): {}", itemKey, entry.getKey(), e.getMessage());
                    continue;
                }

                JsonElement valElem = root.get(itemKey);
                if (!(valElem instanceof JsonObject valObj) || !valObj.has("values")) continue;

                JsonObject values = valObj.getAsJsonObject("values");
                Map<ElementDamage, Integer> elementLevels = new HashMap<>();

                for (String key : values.keySet()) {
                    ElementDamage element = ElementDamage.byName(key.replace("lensouls:", ""));
                    if (element == null) continue;
                    int level = values.get(key).getAsInt();
                    if (level > MAX_LEVEL) {
                        LenSouls.LOGGER.warn("[ItemElementActivity] 等级 {} 超过上限 9 (物品: {}, 元素: {}), 钳位为 9", level, itemKey, element.getSerializedName());
                        level = MAX_LEVEL;
                    }
                    if (level >= 0) {
                        elementLevels.put(element, level);
                    }
                }

                if (!elementLevels.isEmpty()) {
                    newMap.merge(itemId, elementLevels, (a, b) -> {
                        Map<ElementDamage, Integer> m = new HashMap<>(a);
                        m.putAll(b);
                        return m;
                    });
                }
            }
        }

        activityMap = newMap;
        LenSouls.LOGGER.info("[ItemElementActivity] Loaded {} item entries", activityMap.size());
    }

    /** 获取某物品对某元素的活性等级（0=无配置） */
    public static int getLevel(ResourceLocation itemId, ElementDamage element) {
        Map<ElementDamage, Integer> levels = activityMap.get(itemId);
        if (levels == null) return 0;
        return levels.getOrDefault(element, 0);
    }

    /** 获取某物品对某元素的活性倍率（0=无加成返回 0） */
    public static float getActivity(ResourceLocation itemId, ElementDamage element) {
        int level = getLevel(itemId, element);
        return level > 0 ? ElementDamage.getActivityByLevel(level) : 0;
    }

    /** 获取物品位阶对应的活性倍率 */
    public static float getActivity(ItemStack stack, ElementDamage element) {
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return getActivity(itemId, element);
    }

    /** 获取物品的所有元素等级映射（可能为 null） */
    public static Map<ElementDamage, Integer> getLevels(ResourceLocation itemId) {
        return activityMap.get(itemId);
    }

    public static void clear() {
        activityMap.clear();
    }
}
