package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.item.ModItems;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * BOSS 死亡掉落复制之魂：5~20 个。
 * <p>
 * BOSS 判定：沿实体类层次反射查找 {@link BossEvent} 类型字段（原版 EnderDragon/Wither/ElderGuardian
 * 与多数模组 BOSS 均持有 ServerBossEvent 字段），且为可见状态。
 */
public class CopySoulDropHandler {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!hasBossBar(entity)) return;

        // 佩戴羽·元素觉醒者/羽·荒厄遗咒的玩家击杀 → BOSS 不掉落复制之魂
        LivingEntity killer = entity.getKillCredit();
        if (killer instanceof Player player
                && (FeatherElementRiseHandler.hasFeather(player) || FeatherHardmanHandler.hasHardman(player))) {
            return;
        }

        int count = 5 + entity.level().random.nextInt(16); // 5..20
        event.getDrops().add(new ItemEntity(entity.level(),
                entity.getX(), entity.getY(), entity.getZ(),
                new ItemStack(ModItems.COPY_SOUL.get(), count)));
    }

    /** 反射检测实体是否持有可见的 BOSS 血条（沿类层次遍历字段，供扭曲羽毛生成判定复用） */
    public static boolean hasBossBar(LivingEntity entity) {
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (!BossEvent.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    if (value instanceof net.minecraft.server.level.ServerBossEvent serverBossEvent) {
                        if (serverBossEvent.isVisible()) return true;
                    } else if (value instanceof BossEvent) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }
}
