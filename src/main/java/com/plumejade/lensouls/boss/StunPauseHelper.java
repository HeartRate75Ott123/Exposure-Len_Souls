package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.ability.util.FreezeTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 定身暂停判定（common：服务端/客户端共用）。
 * <p>
 * 破刹（韧性清空）与时间定格共用「暂停实体刻」逻辑：
 * 判定在 {@code level().isClientSide} 分支内引用客户端类（惰性类加载，
 * 服务端不执行该分支不会加载客户端类）。
 */
public final class StunPauseHelper {

    private StunPauseHelper() {}

    /**
     * 实体是否处于定身暂停（破刹或时间定格）——暂停实体刻用。
     * <p>
     * 客户端只认破刹：时间定格（全局 freeze）期间客户端实体照常 tick
     * （动画继续，原版 freeze 语义），渲染层由 timer 冻结 partialTicks。
     */
    public static boolean isStunPaused(Entity entity) {
        if (entity.level().isClientSide) {
            return BossToughnessClientCache.isStunned(entity.getId());
        }
        if (!(entity instanceof LivingEntity living)) return false;
        BossToughnessData data = BossToughnessManager.getInstance().get(living);
        if (data != null && data.isBroken()) return true;
        return FreezeTracker.getInstance().isFrozen();
    }

    /**
     * 实体是否处于破刹（韧性清空）——清除无敌帧专用（时间定格不清）。
     */
    public static boolean isToughnessBroken(Entity entity) {
        if (entity.level().isClientSide) {
            return BossToughnessClientCache.isStunned(entity.getId());
        }
        if (!(entity instanceof LivingEntity living)) return false;
        BossToughnessData data = BossToughnessManager.getInstance().get(living);
        return data != null && data.isBroken();
    }
}