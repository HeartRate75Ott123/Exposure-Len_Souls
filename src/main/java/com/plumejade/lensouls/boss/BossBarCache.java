package com.plumejade.lensouls.boss;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;

/**
 * BOSS 血条缓存工具。
 * <p>
 * 提供反射清除实体 BossBar 的能力（幻灵表演时隐藏借来实体的血条）。
 */
public class BossBarCache {

    private BossBarCache() {}

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
