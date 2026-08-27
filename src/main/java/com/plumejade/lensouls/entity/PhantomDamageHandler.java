package com.plumejade.lensouls.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 幻灵防误伤 + 穿透伤害。
 * <p>
 * 防误伤：幻灵来源（借体 BOSS 本体、其召唤物、或其弹幕 owner）对玩家不造成任何伤害——
 * 在 LivingIncomingDamageEvent 阶段直接取消（含近战/接触/弹幕/召唤物），真正免伤且含击退。
 * 召唤者处于旁观者模式已天然免疫，此处额外拦截以防队友/敌队被借体 BOSS 及其召唤物波及。
 * <p>
 * 穿透伤害：仅借体 BOSS 本体每次命中非玩家时覆盖为固定穿透伤害
 * （按镜魂等级 1-5：10 / 18 / 21 / 35 / 37，无视护甲/伤害桶/单次上限）。
 */
public class PhantomDamageHandler {

    private static final int[] PEN_DAMAGE = {20, 36, 42, 70, 74};

    /** 实体本身是否是幻灵（借体 boss 本体 / 召唤物），用于幻灵之间不互殴的隔离判断 */
    public static boolean isPhantomEntity(Entity e) {
        if (e == null) return false;
        return e.getPersistentData().getBoolean("lensouls:phantom")
                || e.getPersistentData().getBoolean("lensouls:phantom_minion");
    }

    /** 递归判定实体是否属幻灵来源：本体 / 召唤物 / 弹幕 owner */
    private static boolean isPhantomSource(Entity e) {
        if (e == null) return false;
        if (isPhantomEntity(e)) return true;
        if (e instanceof Projectile proj) {
            return isPhantomSource(proj.getOwner());
        }
        return false;
    }

    /** 防误伤：幻灵来源（直接来源或真实攻击者，含弹幕 owner）对玩家全免；幻灵之间也不互殴（幻灵来源同样不可伤其他幻灵）。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getAmount() <= 0f) return;
        boolean receiverProtected = event.getEntity() instanceof Player || isPhantomEntity(event.getEntity());
        if (!receiverProtected) return;
        if (isPhantomDamageSource(event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** 递归判定伤害来源是否属幻灵：同时查直接来源与真实攻击者（getEntity），覆盖水花/弹幕 attrib 到 boss 的情形 */
    private static boolean isPhantomDamageSource(DamageSource src) {
        return isPhantomSource(src.getDirectEntity()) || isPhantomSource(src.getEntity());
    }

    /** Goety 式：幻灵/召唤物永不把玩家或另一只幻灵设为目标（硬拦截 AI 锁目标，根治“虚灵打我”与虚灵互殴） */
    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!(entity instanceof Mob mob)) return;
        if (!isPhantomEntity(mob)) return;
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget == null) return;
        if (newTarget instanceof Player || isPhantomEntity(newTarget)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getOriginalDamage() <= 0f) return;

        Entity attacker = event.getSource().getDirectEntity();
        if (attacker == null) return;

        // 仅借体 BOSS 本体穿透；召唤物按正常伤害结算
        if (!attacker.getPersistentData().getBoolean("lensouls:phantom")) return;

        // 玩家已在 LivingIncomingDamageEvent 拦截，此处仅处理非玩家
        if (event.getEntity() instanceof Player) return;

        int level = attacker.getPersistentData().getInt("lensouls:phantom_level");
        if (level < 1 || level > 5) level = 1;
        event.setNewDamage(PEN_DAMAGE[level - 1]);
    }
}
