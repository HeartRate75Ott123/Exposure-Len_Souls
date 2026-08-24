package com.plumejade.lensouls.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 幻灵穿透伤害 — 幻灵每次命中覆盖为固定穿透伤害。
 * <p>
 * 按镜魂等级（SoulLevel 1-5）取值：10 / 18 / 21 / 35 / 37，
 * 无视护甲、无视伤害桶、无视单次伤害上限（setNewDamage 在事件阶段直接替换最终伤害）。
 * 攻击动画照常播放，命中即掉血，完全同步。
 * <p>
 * 防误伤：所有玩家（召唤者/队友/敌队）对幻灵伤害免疫——召唤者处于旁观者模式已天然免疫，
 * 此处对所有 Player 额外拦截以防队友被借体 BOSS 的近战/弹幕波及。
 * 借体 BOSS 发射的弹幕（getDirectEntity 为弹幕本身、无 phantom 标记）通过检查其 owner 判定。
 */
public class PhantomDamageHandler {

    private static final int[] PEN_DAMAGE = {10, 18, 21, 35, 37};

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getOriginalDamage() <= 0f) return;
        // 防误伤：所有玩家免疫幻灵伤害（召唤者旁观者已免疫，此处双保险并覆盖队友/敌队）
        if (event.getEntity() instanceof Player) return;

        Entity attacker = event.getSource().getDirectEntity();
        if (attacker == null) return;

        // 解析"幻灵来源实体"：借体 BOSS 本体，或弹幕的 owner
        Entity phantomEntity = attacker;
        boolean isPhantom = attacker.getPersistentData().getBoolean("lensouls:phantom");
        if (!isPhantom && attacker instanceof Projectile proj) {
            Entity owner = proj.getOwner();
            if (owner != null && owner.getPersistentData().getBoolean("lensouls:phantom")) {
                isPhantom = true;
                phantomEntity = owner;
            }
        }
        if (!isPhantom) return;

        // 命中 → 覆盖为固定穿透伤害（按镜魂等级 1-5；等级存于幻灵来源实体上）
        int level = phantomEntity.getPersistentData().getInt("lensouls:phantom_level");
        if (level < 1 || level > 5) level = 1;
        event.setNewDamage(PEN_DAMAGE[level - 1]);
    }
}
