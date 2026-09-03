package com.plumejade.lensouls.handler;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 照片弹幕「最大生命百分比伤害」10tick 内置间隔。
 * <p>
 * 带 {@code lensouls:photo_percent} 标记的弹幕（湮灭激光 5%/tick、云筑激光 1%/tick、
 * 利维坦射线、焰魔火球）对同一目标在 10tick 内的后续命中直接把伤害置 0——主动拦截，
 * 不依赖"不清无敌帧"的被动机制（否则其它弹幕帮忙清帧就会绕过）。
 * 键按 (弹幕实体, 目标) 区分：不同弹幕对同一目标、或同一弹幕对不同目标互不影响。
 */
public class PhotoPercentDamageThrottleHandler {

    /** 内置间隔（tick） */
    private static final int INTERVAL = 10;

    /** key=(弹幕实体Id<<32)|目标Id → 最近一次允许结算的 gameTime（仅服务端） */
    private static final Map<Long, Long> LAST_ALLOWED = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;

        Entity direct = event.getSource().getDirectEntity();
        if (direct == null) return;
        if (!direct.getPersistentData().getBoolean("lensouls:photo_proj")) return;
        if (!direct.getPersistentData().getBoolean("lensouls:photo_percent")) return;

        LivingEntity target = event.getEntity();
        long now = target.level().getGameTime();
        long key = ((long) direct.getId() << 32) | (target.getId() & 0xFFFFFFFFL);

        Long last = LAST_ALLOWED.get(key);
        if (last != null && now - last < INTERVAL) {
            // 间隔内：该百分比弹幕本次不造成伤害（不取消、不影响其它弹幕）
            event.setNewDamage(0f);
            return;
        }

        LAST_ALLOWED.put(key, now);
        if (LAST_ALLOWED.size() > 256) {
            prune(now - 40);
        }
    }

    private static void prune(long olderThan) {
        Iterator<Map.Entry<Long, Long>> it = LAST_ALLOWED.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < olderThan) it.remove();
        }
    }

    private PhotoPercentDamageThrottleHandler() {}
}
