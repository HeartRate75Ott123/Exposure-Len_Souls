package com.plumejade.lensouls.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 幻灵穿透伤害 — 幻灵每次命中覆盖为固定穿透伤害。
 * <p>
 * 按镜魂等级（SoulLevel 1-5）取值：10 / 18 / 21 / 35 / 37，
 * 无视护甲、无视伤害桶、无视单次伤害上限（setNewDamage 在事件阶段直接替换最终伤害）。
 * 攻击动画照常播放，命中即掉血，完全同步。
 */
public class PhantomDamageHandler {

    private static final int[] PEN_DAMAGE = {10, 18, 21, 35, 37};

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getOriginalDamage() <= 0f) return;

        Entity attacker = event.getSource().getDirectEntity();
        if (attacker == null) return;
        if (!attacker.getPersistentData().getBoolean("lensouls:phantom")) return;

        // 命中 → 覆盖为固定穿透伤害（按镜魂等级 1-5）
        int level = attacker.getPersistentData().getInt("lensouls:phantom_level");
        if (level < 1 || level > 5) level = 1;
        event.setNewDamage(PEN_DAMAGE[level - 1]);
    }
}
