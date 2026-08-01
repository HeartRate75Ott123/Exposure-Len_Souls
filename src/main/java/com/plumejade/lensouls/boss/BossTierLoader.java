package com.plumejade.lensouls.boss;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * BOSS 阶层映射：实体 ID → 阶层（1~4），0 表示无阶层。
 * 安装对应或更高阶层镜头配件的相机才能对这些 BOSS 造成韧性伤害。
 */
public class BossTierLoader {

    private static final Map<String, Integer> TIER_MAP = new HashMap<>();

    static {
        // Tier 1 — 橄榄绿
        put("legendary_monsters:overgrown_colossus", 1);
        put("legendary_monsters:dune_sentinel", 1);
        put("legendary_monsters:skeletosaurus", 1);
        put("legendary_monsters:lava_eater", 1);
        put("legendary_monsters:frostbitten_golem", 1);
        put("legendary_monsters:ancient_guardian", 1);
        put("block_factorys_bosses:yeti", 1);
        put("legendary_monsters:withered_abomination", 1);

        // Tier 2 — 蓝色
        put("cataclysm:maledictus", 2);
        put("cataclysm:netherite_monstrosity", 2);
        put("cataclysm:scylla", 2);
        put("cataclysm:ancient_remnant", 2);
        put("cataclysm:the_harbinger", 2);

        // Tier 3 — 紫色
        put("legendary_monsters:posessed_paladin", 3);
        put("legendary_monsters:cloud_golem", 3);
        put("minecraft:wither", 3);
        put("minecraft:warden", 3);
        put("cataclysm:the_leviathan", 3);
        put("cataclysm:ignis", 3);
        put("block_factorys_bosses:underworld_knight", 3);

        // Tier 4 — 红色
        put("legendary_monsters:shulker_mimic", 4);
        put("legendary_monsters:endersent", 4);
        put("legendary_monsters:annihilation_pursuer", 4);
        put("cataclysm:ender_guardian", 4);
        put("minecraft:ender_dragon", 4);
        put("legendary_monsters:the_obliterator", 4);
    }

    private static void put(String id, int tier) {
        TIER_MAP.put(id, tier);
    }

    /** 获取实体的阶层（0 = 无阶层/未知） */
    public static int getTier(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return TIER_MAP.getOrDefault(id.toString(), 0);
    }
}
