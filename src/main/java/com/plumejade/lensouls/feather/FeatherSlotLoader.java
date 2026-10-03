package com.plumejade.lensouls.feather;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「可放入羽毛槽的物品」列表（数据驱动，方便后续追加）。
 * <p>
 * 扫描 {@code data/lensouls/feather_slot/*.json}，格式：
 * <pre>
 * { "items": [ "lensouls:feather_hardman", "lensouls:feather_abyss" ] }
 * </pre>
 * <b>为什么不做成 Java 常量表</b>：需求明确「做一个支持放入的物品的列表，方便后续追加」。
 * 数据驱动下追加只需加一行 id；整合包也能自己写数据包覆盖/追加（同 id 的多个文件会被
 * {@code SimpleJsonResourceReloadListener} 全部读取，本类按文件顺序合并去重）。
 * 支持 {@code /reload} 热重载，并随 {@code DatapackSyncPacket} 同步到客户端——
 * 选择界面要靠它给「不可放入」的物品盖红色蒙版。
 */
public class FeatherSlotLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LenSouls.LOGGER;
    private static final String FOLDER = "feather_slot";
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String KEY_ITEMS = "items";

    /** 支持放入的物品（服务端 = 数据包解析结果；客户端 = 同步包填充） */
    private static volatile Set<Item> supported = Set.of();
    /** 对应的物品 id 列表（同步用，保持数据包书写顺序） */
    private static volatile List<ResourceLocation> supportedIds = List.of();

    public FeatherSlotLoader() {
        super(GSON, FOLDER);
    }

    // ========== 公开查询 API ==========

    /** 该物品能否放入羽毛槽（界面红蒙版与菜单服务端校验共用同一判定） */
    public static boolean isSupported(ItemStack stack) {
        return stack != null && !stack.isEmpty() && supported.contains(stack.getItem());
    }

    /** 支持放入的物品集合（只读） */
    public static Set<Item> allItems() {
        return supported;
    }

    /** 支持放入的物品 id 列表（服务端数据包同步用） */
    public static List<ResourceLocation> supportedIds() {
        return supportedIds;
    }

    /** 客户端接收同步后的缓存填充（多人模式下服务端解析结果经 S2C 同步） */
    public static void setClientCache(Collection<ResourceLocation> ids) {
        LinkedHashSet<ResourceLocation> collected = new LinkedHashSet<>();
        LinkedHashSet<Item> items = new LinkedHashSet<>();
        for (ResourceLocation id : ids) {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item == null) {
                LOGGER.warn("[FeatherSlot] 客户端同步：未知物品 {}", id);
                continue;
            }
            collected.add(id);
            items.add(item);
        }
        supportedIds = List.copyOf(collected);
        supported = Set.copyOf(items);
    }

    // ========== 解析 ==========

    @Override
    protected void apply(@NotNull Map<ResourceLocation, JsonElement> entries,
                         @NotNull ResourceManager manager,
                         @NotNull ProfilerFiller profiler) {

        LinkedHashSet<ResourceLocation> collected = new LinkedHashSet<>();
        LinkedHashSet<Item> items = new LinkedHashSet<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonElement json = entry.getValue();
            if (!json.isJsonObject()) {
                LOGGER.warn("[FeatherSlot] 跳过非 JSON 对象文件: {}", fileId);
                continue;
            }
            JsonObject root = json.getAsJsonObject();
            JsonArray array = root.getAsJsonArray(KEY_ITEMS);
            if (array == null) {
                LOGGER.warn("[FeatherSlot] 文件缺少 '{}' 数组: {}", KEY_ITEMS, fileId);
                continue;
            }
            for (JsonElement element : array) {
                ResourceLocation id;
                try {
                    id = ResourceLocation.parse(element.getAsString());
                } catch (Exception e) {
                    LOGGER.warn("[FeatherSlot] 无效物品 ID '{}'（文件 {}）", element, fileId);
                    continue;
                }
                Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null) {
                    LOGGER.warn("[FeatherSlot] 未知物品 '{}'（文件 {}），跳过", id, fileId);
                    continue;
                }
                collected.add(id);
                items.add(item);
            }
        }

        supportedIds = List.copyOf(collected);
        supported = Set.copyOf(items);
        LOGGER.info("[FeatherSlot] 可放入羽毛槽的物品 {} 种：{}", supportedIds.size(), supportedIds);
    }
}
