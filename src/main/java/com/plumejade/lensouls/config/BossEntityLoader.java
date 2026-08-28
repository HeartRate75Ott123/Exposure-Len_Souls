package com.plumejade.lensouls.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 首领实体清单加载器（仿 BossChecklist 的 server_bosses_ids 数据驱动方式）。
 * <p>
 * 路径：{@code data/lensouls/boss_entities/&lt;任意文件名&gt;.json}
 * 格式：数组（元素可为字符串 id 或 {@code {"id":"..."}} 对象）。
 * 用于照片饰品「首领套」判定——拍照时按实体类型是否命中本清单识别 Boss。
 */
public class BossEntityLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final String FOLDER = "boss_entities";
    private static Set<ResourceLocation> BOSSES = Set.of();

    public BossEntityLoader() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Set<ResourceLocation> set = new HashSet<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            JsonElement json = entry.getValue();
            if (json.isJsonArray()) {
                for (JsonElement el : json.getAsJsonArray()) {
                    String id = null;
                    if (el.isJsonPrimitive()) {
                        id = el.getAsString();
                    } else if (el.isJsonObject() && el.getAsJsonObject().has("id")) {
                        id = el.getAsJsonObject().get("id").getAsString();
                    }
                    if (id == null || id.isEmpty()) continue;
                    try {
                        set.add(ResourceLocation.parse(id.trim()));
                    } catch (Exception e) {
                        LenSouls.LOGGER.warn("[BossEntity] 无效 boss id '{}' (文件: {})", id, entry.getKey());
                    }
                }
            } else if (json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();
                if (obj.has("id")) {
                    try { set.add(ResourceLocation.parse(obj.get("id").getAsString().trim())); }
                    catch (Exception ignored) {}
                } else {
                    for (String key : obj.keySet()) {
                        try { set.add(ResourceLocation.parse(key)); } catch (Exception ignored) {}
                    }
                }
            }
        }
        BOSSES = Set.copyOf(set);
        LenSouls.LOGGER.info("[BossEntity] 加载了 {} 个首领实体", BOSSES.size());
    }

    /** 实体是否在首领清单中 */
    public static boolean isBoss(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id != null && BOSSES.contains(id);
    }

    /** 实体 id 字符串是否在首领清单中 */
    public static boolean isBoss(String entityId) {
        try {
            return BOSSES.contains(ResourceLocation.parse(entityId));
        } catch (Exception e) {
            return false;
        }
    }
}
