package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.config.BossEntityLoader;
import com.plumejade.lensouls.config.CopySoulFilter;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 实体死亡掉落复制之魂：5~20 个。
 * <p>
 * 基础判定：最大生命值 ≥ 200（替代原 BOSS 血条反射检测）。
 * 另受 {@link com.plumejade.lensouls.config.CopySoulFilter} 掉落黑白名单控制；
 * <b>新改案</b>：羽毛（① 荒厄遗咒 / ③ 元素觉醒者）不再影响掉魂。
 * {@link #hasBossBar} 反射检测仍保留，供扭曲羽毛生成判定复用。
 */
public class CopySoulDropHandler {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        // 基础判定：200 血以上（替代原 BOSS 血条检测）；
        // 首领清单（boss_entities/bosses.json）内的实体豁免血量门槛（如 the_gatekeeper 仅 175 血）
        if (entity.getMaxHealth() < 200f && !BossEntityLoader.isBoss(entity)) return;

        // 新改案：① 荒厄遗咒不再影响 BOSS 掉魂（e2 已移除复制之魂相关内容），
        // ③ 元素觉醒者的「无法掉落复制之魂」旧版就已移除 → 这里不再按羽毛拦掉落。

        // 数据驱动掉落黑白名单：综合白/黑名单与 "all" 通配、"#标签"（按实体类型标签展开）
        if (!CopySoulFilter.isDropAllowed(entity.getType())) return;

        spawnCopySoulDrop((ServerLevel) entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                copySoulMult(event.getSource().getEntity()));
    }

    /**
     * 生成 5~20 个复制之魂的掉落实体（供死亡事件与 Gatekeeper 挑战胜利 mixin 复用）。
     */
    public static void spawnCopySoulDrop(ServerLevel level, double x, double y, double z) {
        spawnCopySoulDrop(level, x, y, z, 1.0f);
    }

    /**
     * 同上，但带掉落倍率。
     * <p>
     * ⑤ 铁序·厄运 反转后的「复制之魂掉落 +50%」走这里 —— 措辞与实现同源，
     * 不再是只写在 tooltip 里的空头承诺。
     */
    public static void spawnCopySoulDrop(ServerLevel level, double x, double y, double z, float mult) {
        int count = 5 + level.random.nextInt(16); // 5..20
        if (mult != 1.0f) count = Math.max(1, Math.round(count * mult));
        level.addFreshEntity(new ItemEntity(level, x, y, z,
                new ItemStack(ModItems.COPY_SOUL.get(), count)));
    }

    /** 击杀者是否处于「⑤ 铁序·厄运 已反转」→ 复制之魂掉落 ×1.5 */
    private static float copySoulMult(net.minecraft.world.entity.Entity killer) {
        if (killer instanceof net.minecraft.server.level.ServerPlayer sp
                && com.plumejade.lensouls.feather.CurseManager.rev(sp,
                        com.plumejade.lensouls.feather.CurseDefs.TIMECORE, 14)) {
            return 1.5f;
        }
        return 1.0f;
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
