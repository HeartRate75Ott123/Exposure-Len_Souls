package com.plumejade.lensouls.reinforce;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 强化系统数据包加载器。
 * <p>
 * 路径：{@code data/lensouls/reinforcement/&lt;任意文件名&gt;.json}（可放多个文件，按文件路径字典序合并）
 * <pre>
 * {
 *   // 可被强化的物品黑名单（命中者不能作为被强化目标）
 *   "blacklist": [ "minecraft:bedrock", "lensouls:dimensional_hammer" ],
 *
 *   // 强化材料：物品 ID → 数值简写（主手 ADD_VALUE 攻击伤害）或完整对象
 *   "materials": {
 *     "legendary_monsters:nature_crystal": 3,
 *     "fdbosses:malkuth_trophy": 10,
 *     "some:item": {
 *       "attribute": "minecraft:generic.attack_speed",   // 默认 minecraft:generic.attack_damage
 *       "amount": 0.1,                                   // 必填
 *       "operation": "add_multiplied_base",              // 默认 add_value
 *       "slot": "mainhand",                              // 默认 mainhand
 *       "desc": [ "额外说明第一行", "第二行" ]              // 可选，原样显示在 tooltip
 *     },
 *     "multi:attr": {
 *       "modifiers": [ { "attribute": "...", "amount": 3 }, { "attribute": "...", "amount": 2 } ]
 *     }
 *   }
 * }
 * </pre>
 * 键名以 {@code _} 开头的项被忽略（便于写注释）。
 * <p>
 * 材料在列表中的顺序 = JSON 中的书写顺序（GUI 的默认排列；收藏项由客户端提到最前）。
 */
public class ReinforceDataLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    /** 材料 ID → 定义（LinkedHashMap 保留 JSON 书写顺序，供 GUI 默认排列） */
    private static Map<ResourceLocation, ReinforceMaterial> materials = new LinkedHashMap<>();
    /** 黑名单（不可作为被强化目标） */
    private static Set<ResourceLocation> blacklist = new HashSet<>();
    /** 数据版本号（加载/同步后自增，供客户端搜索索引判断是否重建） */
    private static final java.util.concurrent.atomic.AtomicInteger VERSION =
            new java.util.concurrent.atomic.AtomicInteger();

    public ReinforceDataLoader() {
        super(GSON, "reinforcement");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, ReinforceMaterial> newMaterials = new LinkedHashMap<>();
        Set<ResourceLocation> newBlacklist = new LinkedHashSet<>();

        List<ResourceLocation> files = new ArrayList<>(entries.keySet());
        files.sort(Comparator.comparing(ResourceLocation::toString));
        int badModifiers = 0;

        for (ResourceLocation file : files) {
            JsonElement json = entries.get(file);
            if (!json.isJsonObject()) continue;
            JsonObject root = json.getAsJsonObject();

            // ---- 黑名单 ----
            JsonElement blacklistElem = root.get("blacklist");
            if (blacklistElem != null && blacklistElem.isJsonArray()) {
                for (JsonElement elem : blacklistElem.getAsJsonArray()) {
                    ResourceLocation id = parseId(elem, file, "blacklist");
                    if (id != null) newBlacklist.add(id);
                }
            }

            // ---- 材料 ----
            JsonElement materialsElem = root.get("materials");
            if (materialsElem == null || !materialsElem.isJsonObject()) continue;
            JsonObject materialObject = materialsElem.getAsJsonObject();

            for (String key : materialObject.keySet()) {
                if (key.startsWith("_")) continue;
                ResourceLocation materialId = ResourceLocation.tryParse(key);
                if (materialId == null) {
                    LenSouls.LOGGER.warn("[Reinforce] 无效材料物品 ID '{}' (文件: {})", key, file);
                    continue;
                }
                JsonElement value = materialObject.get(key);
                List<ReinforceModifier> modifiers = new ArrayList<>();
                List<String> desc = new ArrayList<>();

                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                    // 简写：数字 = 主手 ADD_VALUE 攻击伤害
                    modifiers.add(new ReinforceModifier(Attributes.ATTACK_DAMAGE,
                            value.getAsDouble(), ReinforceModifier.DEFAULT_OPERATION, ReinforceModifier.DEFAULT_SLOT));
                } else if (value.isJsonObject()) {
                    JsonObject obj = value.getAsJsonObject();
                    JsonElement multi = obj.get("modifiers");
                    if (multi != null && multi.isJsonArray()) {
                        for (JsonElement elem : multi.getAsJsonArray()) {
                            if (!elem.isJsonObject()) continue;
                            ReinforceModifier modifier = parseModifier(elem.getAsJsonObject(), materialId, file);
                            if (modifier == null) badModifiers++;
                            else modifiers.add(modifier);
                        }
                    } else {
                        ReinforceModifier modifier = parseModifier(obj, materialId, file);
                        if (modifier == null) badModifiers++;
                        else modifiers.add(modifier);
                    }
                    JsonElement descElem = obj.get("desc");
                    if (descElem != null && descElem.isJsonArray()) {
                        for (JsonElement elem : descElem.getAsJsonArray()) {
                            if (elem.isJsonPrimitive()) desc.add(elem.getAsString());
                        }
                    }
                } else {
                    LenSouls.LOGGER.warn("[Reinforce] 材料 '{}' 的值既不是数字也不是对象 (文件: {})", key, file);
                    continue;
                }

                if (modifiers.isEmpty() && desc.isEmpty()) {
                    LenSouls.LOGGER.warn("[Reinforce] 材料 '{}' 没有任何有效修饰符 (文件: {})", key, file);
                    continue;
                }
                newMaterials.put(materialId, new ReinforceMaterial(materialId, List.copyOf(modifiers), List.copyOf(desc)));
            }
        }

        materials = newMaterials;
        blacklist = newBlacklist;
        VERSION.incrementAndGet();
        LenSouls.LOGGER.info("[Reinforce] Loaded {} materials, {} blacklisted items{}",
                materials.size(), blacklist.size(), badModifiers > 0 ? " (" + badModifiers + " invalid modifiers skipped)" : "");
    }

    /** 数据版本号（客户端搜索索引据此判断是否需要重建）。 */
    public static int version() {
        return VERSION.get();
    }

    /** 解析单条修饰符；缺 amount 或属性/运算/槽组非法 → null。 */
    private static ReinforceModifier parseModifier(JsonObject obj, ResourceLocation materialId, ResourceLocation file) {
        if (!obj.has("amount") || !obj.get("amount").isJsonPrimitive()) {
            LenSouls.LOGGER.warn("[Reinforce] 材料 '{}' 的修饰符缺少 amount (文件: {})", materialId, file);
            return null;
        }
        double amount = obj.get("amount").getAsDouble();

        net.minecraft.core.Holder<Attribute> attribute;
        String attributeName = obj.has("attribute") ? obj.get("attribute").getAsString() : null;
        if (attributeName == null) {
            attribute = Attributes.ATTACK_DAMAGE;
        } else {
            ResourceLocation attributeId = ResourceLocation.tryParse(attributeName);
            attribute = attributeId == null ? null
                    : BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).orElse(null);
            if (attribute == null) {
                LenSouls.LOGGER.warn("[Reinforce] 材料 '{}' 的属性 '{}' 不存在 (文件: {})", materialId, attributeName, file);
                return null;
            }
        }

        AttributeModifier.Operation operation = ReinforceModifier.DEFAULT_OPERATION;
        if (obj.has("operation")) {
            operation = ReinforceModifier.parseOperation(obj.get("operation").getAsString());
            if (operation == null) {
                LenSouls.LOGGER.warn("[Reinforce] 材料 '{}' 的运算 '{}' 非法 (文件: {})",
                        materialId, obj.get("operation").getAsString(), file);
                return null;
            }
        }

        EquipmentSlotGroup slot = ReinforceModifier.DEFAULT_SLOT;
        if (obj.has("slot")) {
            slot = ReinforceModifier.parseSlot(obj.get("slot").getAsString());
            if (slot == null) {
                LenSouls.LOGGER.warn("[Reinforce] 材料 '{}' 的槽组 '{}' 非法 (文件: {})",
                        materialId, obj.get("slot").getAsString(), file);
                return null;
            }
        }

        return new ReinforceModifier(attribute, amount, operation, slot);
    }

    private static ResourceLocation parseId(JsonElement elem, ResourceLocation file, String section) {
        if (!elem.isJsonPrimitive()) return null;
        ResourceLocation id = ResourceLocation.tryParse(elem.getAsString());
        if (id == null) LenSouls.LOGGER.warn("[Reinforce] {} 中的无效物品 ID '{}' (文件: {})", section, elem.getAsString(), file);
        return id;
    }

    // ========== 查询 ==========

    /** 材料定义（不存在 → null）。 */
    public static ReinforceMaterial getMaterial(ResourceLocation materialId) {
        return materials.get(materialId);
    }

    /** 是否为已配置的强化材料。 */
    public static boolean isMaterial(ResourceLocation itemId) {
        return materials.containsKey(itemId);
    }

    /** 材料定义（按 JSON 书写顺序，供 GUI 默认排列）。 */
    public static List<ReinforceMaterial> getMaterials() {
        return List.copyOf(materials.values());
    }

    /** 该物品是否被禁止强化。 */
    public static boolean isBlacklisted(ResourceLocation itemId) {
        return blacklist.contains(itemId);
    }

    public static boolean isBlacklisted(ItemStack stack) {
        return !stack.isEmpty() && isBlacklisted(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static Set<ResourceLocation> getBlacklist() {
        return Set.copyOf(blacklist);
    }

    /** 全量快照（服务端数据包同步用） */
    public static List<ReinforceMaterial> allMaterials() {
        return List.copyOf(materials.values());
    }

    public static Set<ResourceLocation> allBlacklist() {
        return new LinkedHashSet<>(blacklist);
    }

    public static void clear() {
        materials = new LinkedHashMap<>();
        blacklist = new HashSet<>();
    }

    /**
     * 客户端接收数据包同步后的缓存填充（多人模式下服务端解析结果经 S2C 同步）。
     */
    public static void setClientCache(List<ReinforceMaterial> syncedMaterials, Set<ResourceLocation> syncedBlacklist) {
        Map<ResourceLocation, ReinforceMaterial> map = new LinkedHashMap<>();
        for (ReinforceMaterial material : syncedMaterials) map.put(material.id(), material);
        materials = map;
        blacklist = new HashSet<>(syncedBlacklist);
        VERSION.incrementAndGet();
    }
}
