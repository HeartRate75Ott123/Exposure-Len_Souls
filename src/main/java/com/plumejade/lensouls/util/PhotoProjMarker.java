package com.plumejade.lensouls.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * 照片弹幕标记（单一事实来源）。
 * <p>
 * 我们生成的每一个「照片弹幕」实体都打上 {@link #PHOTO_PROJ}；会造成目标最大生命
 * 百分比伤害的那批额外打 {@link #PHOTO_PERCENT}。标记承担三件事：
 * <ol>
 *   <li>{@code BossProjHurtMixin}：命中后清目标无敌帧，使多段判伤弹幕能连续结算；</li>
 *   <li>{@code PhotoPercentDamageThrottleHandler}：百分比弹幕对同一目标的 10tick 内置间隔；</li>
 *   <li><b>伤害归属判定</b>：弹幕自身造成的伤害不得回头再掷一次「弹幕触发」，否则
 *       「触发 → 弹幕 → 命中（远程伤害）→ 再触发」会指数增殖（左脚踩右脚升天）；
 *       同时弹幕被反弹打回发射者时完全免伤（自伤保护）。</li>
 * </ol>
 * 判定必须走<b>全链路</b>：直接实体（弹幕本体）与间接实体（造成者）任一带标记都算。
 * 只查直接实体会漏掉「弹幕实体不是直接来源」的写法（爆炸、射线、召唤物代打）。
 */
public final class PhotoProjMarker {

    /** 本模组照片弹幕标记（任何照片弹幕实体都有） */
    public static final String PHOTO_PROJ = "lensouls:photo_proj";
    /** 造成目标最大生命百分比伤害的照片弹幕标记 */
    public static final String PHOTO_PERCENT = "lensouls:photo_percent";

    private PhotoProjMarker() {
    }

    /** 打标记（不改动世界；是否已加入世界由调用方负责） */
    public static void mark(Entity entity, boolean percent) {
        if (entity == null) return;
        CompoundTag tag = entity.getPersistentData();
        tag.putBoolean(PHOTO_PROJ, true);
        if (percent) tag.putBoolean(PHOTO_PERCENT, true);
        // 客户端可见通道：persistentData 不随实体同步，客户端渲染逻辑（弹幕「立体光束」修正）
        // 必须靠这个同步附件（见 ModAttachments.PHOTO_PROJ）。重复标记不再发一遍同步包。
        if (!entity.getData(com.plumejade.lensouls.boss.ModAttachments.PHOTO_PROJ)) {
            entity.setData(com.plumejade.lensouls.boss.ModAttachments.PHOTO_PROJ, true);
            entity.syncData(com.plumejade.lensouls.boss.ModAttachments.PHOTO_PROJ);
        }
    }

    public static void mark(Entity entity) {
        mark(entity, false);
    }

    /**
     * 实体本身是不是本模组的照片弹幕。
     * <p>
     * 双端可用：先看<b>同步附件</b>（客户端唯一能看到的通道），再兜底服务端 {@code persistentData}
     * （存档往返后附件会丢，标记还在）。
     */
    public static boolean isBarrage(Entity entity) {
        if (entity == null) return false;
        if (entity.getData(com.plumejade.lensouls.boss.ModAttachments.PHOTO_PROJ)) return true;
        // 客户端实体只可能拿到同步附件（persistentData 不下发），提前返回还能省掉每帧一次 NBT 复制
        if (entity.level().isClientSide()) return false;
        CompoundTag tag = entity.getPersistentData();
        return tag.getBoolean(PHOTO_PROJ) || tag.getBoolean(PHOTO_PERCENT);
    }

    /**
     * 该伤害是不是「本模组照片弹幕」造成的——弹幕伤害不得回头触发新一轮弹幕。
     * 玩家本身（间接实体是玩家）不算：玩家手持弓箭/次元枪的普通远程命中必须照常触发。
     */
    public static boolean isBarrageDamage(DamageSource source) {
        if (source == null) return false;
        if (isBarrage(source.getDirectEntity())) return true;
        Entity causer = source.getEntity();
        return causer != null && !(causer instanceof Player) && isBarrage(causer);
    }

    /**
     * 该伤害的"发起者实体"：优先弹幕自己的 {@code Projectile#getOwner()}
     * （被反弹/护盾弹回后 {@code getEntity()} 可能已经是别人），其次伤害的 causer。
     * <p>
     * 自伤判定（{@link #isSelfHit}）与友方判定（{@code AllyFilter#isFriendly} 的视角）
     * <b>共用这一份解析</b>，避免两处各写一遍。
     */
    public static Entity ownerOf(DamageSource source) {
        if (source == null) return null;
        Entity direct = source.getDirectEntity();
        Entity owner = null;
        if (direct instanceof Projectile projectile) owner = projectile.getOwner();
        return owner != null ? owner : source.getEntity();
    }

    /**
     * 弹幕是否打回了自己的发射者（被灾变等模组的反弹/护盾弹回后命中玩家自身）。
     * 直接实体必须是我们标记过的弹幕，才认为是「我们的弹幕自伤」。
     */
    public static boolean isSelfHit(DamageSource source, Entity victim) {
        if (source == null || victim == null) return false;
        if (!isBarrage(source.getDirectEntity())) return false;
        Entity owner = ownerOf(source);
        return owner != null && owner == victim;
    }
}
