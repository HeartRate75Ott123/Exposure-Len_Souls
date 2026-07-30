package com.plumejade.lensouls.boss;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * BOSS 韧性属性配置（每实体）。
 * 实体 ID → { requiredHits, stunDurationTicks, invincibleTicks }
 */
public class BossToughnessAttributes {

    public record ToughnessConfig(int requiredHits, int stunDurationTicks, int invincibleTicks) {}

    private static final Map<String, ToughnessConfig> OVERRIDES = new HashMap<>();

    static {
        put("legendary_monsters:overgrown_colossus", new ToughnessConfig(8, 200, 60));
        put("legendary_monsters:dune_sentinel", new ToughnessConfig(8, 200, 60));
        put("legendary_monsters:skeletosaurus", new ToughnessConfig(8, 200, 60));
        put("legendary_monsters:lava_eater", new ToughnessConfig(8, 200, 60));
        put("legendary_monsters:frostbitten_golem", new ToughnessConfig(8, 200, 60));
        put("legendary_monsters:ancient_guardian", new ToughnessConfig(8, 200, 60));
        put("block_factorys_bosses:yeti", new ToughnessConfig(8, 200, 60));
        put("legendary_monsters:withered_abomination", new ToughnessConfig(8, 200, 60));
        put("cataclysm:maledictus", new ToughnessConfig(9, 200, 60));
        put("cataclysm:netherite_monstrosity", new ToughnessConfig(9, 200, 60));
        put("cataclysm:scylla", new ToughnessConfig(9, 200, 60));
        put("cataclysm:ancient_remnant", new ToughnessConfig(9, 200, 60));
        put("cataclysm:the_harbinger", new ToughnessConfig(9, 200, 60));
        put("legendary_monsters:shulker_mimic", new ToughnessConfig(10, 200, 60));
        put("legendary_monsters:endersent", new ToughnessConfig(10, 200, 60));
        put("legendary_monsters:annihilation_pursuer", new ToughnessConfig(10, 200, 60));
        put("legendary_monsters:posessed_paladin", new ToughnessConfig(10, 200, 60));
        put("legendary_monsters:cloud_golem", new ToughnessConfig(10, 200, 60));
        put("minecraft:wither", new ToughnessConfig(10, 200, 60));
        put("minecraft:warden", new ToughnessConfig(10, 200, 60));
        put("cataclysm:the_leviathan", new ToughnessConfig(12, 200, 60));
        put("cataclysm:ender_guardian", new ToughnessConfig(12, 200, 60));
        put("minecraft:ender_dragon", new ToughnessConfig(12, 200, 60));
        put("cataclysm:ignis", new ToughnessConfig(20, 200, 60));
        put("block_factorys_bosses:underworld_knight", new ToughnessConfig(20, 200, 60));
        put("legendary_monsters:the_obliterator", new ToughnessConfig(20, 200, 60));
    }

    public static void put(String entityId, ToughnessConfig config) {
        OVERRIDES.put(entityId, config);
    }

    /** 获取实体削韧次数（默认 5） */
    public static int getRequiredHits(Entity entity) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        ToughnessConfig c = OVERRIDES.get(id);
        return c != null ? c.requiredHits() : com.plumejade.lensouls.Config.TOUGHNESS_DEFAULT_HITS.get();
    }

    /** 获取实体定身 tick（默认 200 = 10 秒） */
    public static int getStunDurationTicks(Entity entity) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        ToughnessConfig c = OVERRIDES.get(id);
        return c != null ? c.stunDurationTicks() : com.plumejade.lensouls.Config.TOUGH_STUN_DURATION_TICKS.get();
    }

    /** 获取实体削韧间隔 tick（默认 60 = 3 秒） */
    public static int getInvincibleTicks(Entity entity) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        ToughnessConfig c = OVERRIDES.get(id);
        return c != null ? c.invincibleTicks() : 60;
    }
}
