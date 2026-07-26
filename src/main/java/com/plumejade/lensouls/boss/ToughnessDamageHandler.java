package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.boss.BossBarCache;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Turtle;

import java.util.Set;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * BOSS 韧性伤害处理器。
 * <p>
 * 在受到伤害时自动注册 BOSS 韧性系统，并应用减伤。
 * BOSS 判定基于三类检测：
 * <ol>
 *   <li>{@link BossDetectionMixin} — 扫描所在类有无 {@code ServerBossEvent} 字段</li>
 *   <li>通用高血量阈值（maxHealth ≥ 100）</li>
 * </ol>
 */
public class ToughnessDamageHandler {

    /** BOSS 通用判定阈值：超过此血量的实体自动注册韧性 */
    private static final double GENERIC_BOSS_HP_THRESHOLD = 100.0;

    /** 排除的非 BOSS 实体类（高血量但不该算 BOSS） */
    private static final Set<Class<?>> BOSS_EXCLUDED_CLASSES = Set.of(
            IronGolem.class,
            SnowGolem.class,
            AbstractGolem.class,
            Turtle.class
    );

    @SubscribeEvent(priority = EventPriority.LOW)
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
     *   <li>ServerBossEvent 字段检测（Mixin，实体类含有 boss bar 字段）</li>
     *   <li>通用血量阈值（maxHealth ≥ {@link #GENERIC_BOSS_HP_THRESHOLD}），排除 {@link #BOSS_EXCLUDED_CLASSES}</li>
     * </ol>
     */
    public static boolean isBoss(LivingEntity entity) {
        // 0. 白名单排除：高血量的非 BOSS（铁傀儡、雪傀儡等）
        if (BOSS_EXCLUDED_CLASSES.stream().anyMatch(clazz -> clazz.isInstance(entity))) {
            return false;
        }

        // 1. ServerBossEvent 字段检测（覆盖绝大多数 modded BOSS）
        if (entity instanceof Mob && BossBarCache.hasBossBar(entity.getClass())) {
            return true;
        }

        // 2. 通用 BOSS 判定：高血量
        if (entity.getMaxHealth() >= GENERIC_BOSS_HP_THRESHOLD) {
            return true;
        }

        return false;
    }
}
