package com.plumejade.lensouls.boss;

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
     * 客户端：破刹（韧性清空）或时停定身集（{@code ClientFreezeCache}）；
     * 时间定格定身期间客户端实体照常 tick 会造成拉扯，同样跳过。
     */
    public static boolean isStunPaused(Entity entity) {
        if (entity.level().isClientSide) {
            return BossToughnessClientCache.isStunned(entity.getId())
                    || com.plumejade.lensouls.ability.client.ClientFreezeCache.isFrozen(entity.getId());
        }
        if (!(entity instanceof LivingEntity living)) return false;
        BossToughnessData data = BossToughnessManager.getInstance().get(living);
        if (data != null && data.isBroken()) return true;
        return com.plumejade.lensouls.ability.util.TimeFreezeManager.getInstance().isEntityFrozen(entity);
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