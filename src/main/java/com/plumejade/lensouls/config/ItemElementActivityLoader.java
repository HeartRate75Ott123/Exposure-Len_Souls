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
 * 路径: {@code data/lensouls/item_element_activity/&lt;物品注册名&gt;.json}
 * <pre>
 * {
 *   "values": {
 *     "lensouls:fire": 2,
 *     "lensouls:water": 1
 *   }
 * }
 * </pre>
 * 等级 0=未定义(无加成)，1=1.2x, 2=1.5x, 3=2.0x, 4=2.5x, 5=3.0x
 */
public class ItemElementActivityLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    /** itemId → element → level */
    private static Map<ResourceLocation, Map<ElementDamage, Integer>> activityMap = new HashMap<>();

    public ItemElementActivityLoader() {
        super(GSON, "item_element_activity");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Map<ElementDamage, Integer>> newMap = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation fileId = entry.getKey(); // lensouls:items/diamond_sword → 实际是文件名
            JsonObject root = entry.getValue().getAsJsonObject();
            if (!root.has("values")) continue;

            // 文件名即为物品注册名（去掉目录前缀）
            // SimpleJsonResourceReloadListener 的 key 是 data/目录 到 .json 的相对路径
            // 比如 "items/diamond_sword" → minecraft:diamond_sword
            ResourceLocation itemId = fileId;

            JsonObject values = root.getAsJsonObject("values");
            Map<ElementDamage, Integer> elementLevels = new HashMap<>();

            for (String key : values.keySet()) {
                ElementDamage element = ElementDamage.byName(key.replace("lensouls:", ""));
                if (element != null) {
                    int level = values.get(key).getAsInt();
                    if (level >= 1 && level <= 5) {
                        elementLevels.put(element, level);
                    }
                }
            }

            if (!elementLevels.isEmpty()) {
                newMap.put(itemId, elementLevels);
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

    public static void clear() {
        activityMap.clear();
    }
}
