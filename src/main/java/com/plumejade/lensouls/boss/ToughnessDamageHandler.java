package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * BOSS 韧性伤害处理器。
 * <p>
 * 在受到伤害时自动注册 BOSS 韧性系统，并应用减伤。
 * BOSS 判定基于两类检测：
 * <ol>
 *   <li>配置白名单</li>
 *   <li>通用高血量阈值（maxHealth ≥ 200）</li>
 * </ol>
 */
public class ToughnessDamageHandler {

    /** BOSS 通用判定阈值：超过此血量的实体自动注册韧性 */
    private static final double GENERIC_BOSS_HP_THRESHOLD = 200.0;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (event.getOriginalDamage() <= 0f) return;

        BossToughnessManager manager = BossToughnessManager.getInstance();

        // ── 1. BOSS 自动注册 ──
        if (!manager.has(target) && isBoss(target)) {
            manager.register(target);
        }

        // ── 2. 韧性减伤 ──
        if (manager.has(target)) {
            float currentDamage = event.getNewDamage();
            float reduced = manager.applyDamageReduction(target, currentDamage);
            if (reduced != currentDamage) {
                event.setNewDamage(reduced);
            }
        }
    }

    /**
     * 判断实体是否为 BOSS。
     * <p>
     * 检测层级：
     * <ol>
     *   <li>配置黑名单（toughnessBlacklist）—— 永不触发韧性</li>
     *   <li>配置白名单（toughnessWhitelist）—— 始终触发韧性</li>
     *   <li>通用血量阈值（maxHealth ≥ {@link #GENERIC_BOSS_HP_THRESHOLD}）</li>
     * </ol>
     */
    public static boolean isBoss(LivingEntity entity) {
        // 0. 配置黑名单：永不触发韧性
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (Config.TOUGHNESS_BLACKLIST.get().contains(id.toString())) {
            return false;
        }

        // 1. 配置白名单：始终触发韧性（含通配 "all" → 除黑名单外全部触发）
        if (Config.TOUGHNESS_WHITELIST.get().contains("all")) return true;
        if (Config.TOUGHNESS_WHITELIST.get().contains(id.toString())) {
            return true;
        }

        // 2. 通用 BOSS 判定：高血量
        if (entity.getMaxHealth() >= GENERIC_BOSS_HP_THRESHOLD) {
            return true;
        }

        return false;
    }
}
