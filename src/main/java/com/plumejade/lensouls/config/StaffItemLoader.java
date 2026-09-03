package com.plumejade.lensouls.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 法杖类武器数据包加载器。
 * <p>
 * 路径: {@code data/lensouls/staff_item/&lt;任意文件名&gt;.json}
 * <p>
 * 格式：每个文件的根对象含 {@code "values"} 数组，列出视为法杖的物品 ID：
 * <pre>
 * {
 *   "values": [ "minecraft:stick", "minecraft:wooden_hoe", "minecraft:stone_hoe" ]
 * }
 * </pre>
 * 多文件求并集。内置默认文件 {@code staffs.json} 提供基线（木棍 + 全系列锄），
 * 数据包可追加自定义法杖。
 * <p>
 * 手持判定发生在服务端（照片弹幕触发），因此本 loader 不需要 S2C 同步。
 */
public class StaffItemLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static Set<ResourceLocation> staffItems = Set.of();

    public StaffItemLoader() {
        super(GSON, "staff_item");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Set<ResourceLocation> result = new HashSet<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            JsonElement json = entry.getValue();
            if (!json.isJsonObject()) continue;

            JsonObject root = json.getAsJsonObject();
            if (!root.has("values") || !root.get("values").isJsonArray()) {
                LenSouls.LOGGER.warn("[StaffItem] 缺少 values 数组 (文件: {})", entry.getKey());
                continue;
            }
            JsonArray values = root.getAsJsonArray("values");
            for (JsonElement v : values) {
                if (!v.isJsonPrimitive()) continue;
                try {
                    result.add(ResourceLocation.parse(v.getAsString()));
                } catch (Exception e) {
                    LenSouls.LOGGER.warn("[StaffItem] 无效物品 ID '{}' (文件: {}): {}", v.getAsString(), entry.getKey(), e.getMessage());
                }
            }
        }

        staffItems = Set.copyOf(result);
        LenSouls.LOGGER.info("[StaffItem] 加载了 {} 个法杖物品", staffItems.size());
    }

    /** 该物品 ID 是否属于法杖类武器 */
    public static boolean isStaff(ResourceLocation itemId) {
        return staffItems.contains(itemId);
    }

    /** 该手持物品是否属于法杖类武器 */
    public static boolean isStaff(ItemStack stack) {
        return !stack.isEmpty() && staffItems.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** 全量法杖清单（服务端数据包同步用；tooltip 显示需客户端缓存） */
    public static java.util.List<ResourceLocation> allStaffs() {
        return java.util.List.copyOf(staffItems);
    }

    /** 客户端接收数据包同步后的缓存填充（多人模式下服务端解析结果经 S2C 同步） */
    public static void setClientCache(java.util.List<ResourceLocation> cache) {
        staffItems = java.util.Set.copyOf(cache);
    }
}
