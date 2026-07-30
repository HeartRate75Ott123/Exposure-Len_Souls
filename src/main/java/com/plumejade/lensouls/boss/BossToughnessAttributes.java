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
        // 可在此添加特定实体的覆盖配置
        // put("cataclysm:ignis", new ToughnessConfig(8, 300, 40));
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
