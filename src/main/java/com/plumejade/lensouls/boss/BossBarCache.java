package com.plumejade.lensouls.boss;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerBossEvent;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BOSS 血条字段缓存。
 * <p>
 * 由 {@link BossDetectionMixin} 在 Mob 构造时写入（按 Class 粒度缓存），
 * 供 {@link com.plumejade.lensouls.boss.ToughnessDamageHandler} 读取。
 * <p>
 * 独立于 Mixin 类，避免 {@code @Unique} 字段在主类和目标类之间产生副本。
 */
public class BossBarCache {

    private BossBarCache() {}

    /** 已扫描过的实体类（含非 BOSS） */
    private static final Set<Class<?>> SCANNED = ConcurrentHashMap.newKeySet();

    /** 确认为 BOSS 的实体类（有 ServerBossEvent 字段） */
    private static final Set<Class<?>> BOSS_CLASSES = ConcurrentHashMap.newKeySet();

    /** 该类是否已扫描过（已缓存结果） */
    public static boolean isScanned(Class<?> entityClass) {
        return SCANNED.contains(entityClass);
    }

    /** 该类是否有 BOSS 血条字段 */
    public static boolean hasBossBar(Class<?> entityClass) {
        return BOSS_CLASSES.contains(entityClass);
    }

    /** 标记扫描结果 */
    public static void markScanned(Class<?> entityClass, boolean isBoss) {
        SCANNED.add(entityClass);
        if (isBoss) {
            BOSS_CLASSES.add(entityClass);
        }
    }

    /**
     * 清除实体的 BossBar（幻灵表演时隐藏借来实体的血条）。
     * 反射遍历实体及其父类查找 ServerBossEvent 字段，调用 removeAllPlayers()。
     */
    public static void clearBossBar(Entity entity) {
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (ServerBossEvent.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        ServerBossEvent bossEvent = (ServerBossEvent) field.get(entity);
                        if (bossEvent != null) {
                            bossEvent.removeAllPlayers();
                        }
                    } catch (IllegalAccessException e) {
                        // 跳过无法访问的字段
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }
}
