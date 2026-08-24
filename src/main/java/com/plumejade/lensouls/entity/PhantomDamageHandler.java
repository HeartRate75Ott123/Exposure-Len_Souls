package com.plumejade.lensouls.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 幻灵穿透伤害 — 幻灵每次命中覆盖为固定穿透伤害。
 * <p>
 * 按镜魂等级（SoulLevel 1-5）取值：10 / 18 / 21 / 35 / 37，
 * 无视护甲、无视伤害桶、无视单次伤害上限（setNewDamage 在事件阶段直接替换最终伤害）。
 * <p>
 * 防误伤：幻灵来源（借体 BOSS 本体或其弹幕 owner）对玩家不造成任何伤害——
 * 召唤者处于旁观者模式已天然免疫，此处额外拦截以防队友/敌队被借体 BOSS 的近战/弹幕波及。
 */
public class PhantomDamageHandler {

    private static final int[] PEN_DAMAGE = {10, 18, 21, 35, 37};

    /** 防误伤：幻灵来源对玩家不造成任何伤害（真正免伤，含击退）。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getAmount() <= 0f) return;
        if (!(event.getEntity() instanceof Player)) return;

        Entity attacker = event.getSource().getDirectEntity();
        if (attacker == null) return;

        // 解析"幻灵来源实体"：借体 BOSS 本体，或弹幕的 owner
        boolean isPhantom = attacker.getPersistentData().getBoolean("lensouls:phantom");
        if (!isPhantom && attacker instanceof Projectile proj) {
            Entity owner = proj.getOwner();
            if (owner != null && owner.getPersistentData().getBoolean("lensouls:phantom")) {
                isPhantom = true;
            }
        }
        if (isPhantom) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getOriginalDamage() <= 0f) return;

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

        // 玩家已在 LivingIncomingDamageEvent 拦截，此处仅处理非玩家
        if (event.getEntity() instanceof Player) return;

        // 命中非玩家 → 覆盖为固定穿透伤害（按镜魂等级 1-5；等级存于幻灵来源实体上）
        int level = phantomEntity.getPersistentData().getInt("lensouls:phantom_level");
        if (level < 1 || level > 5) level = 1;
        event.setNewDamage(PEN_DAMAGE[level - 1]);
    }
}
