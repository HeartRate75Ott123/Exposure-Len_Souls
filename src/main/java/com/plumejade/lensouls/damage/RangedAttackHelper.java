package com.plumejade.lensouls.damage;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 远程伤害判定（口径与「远程伤害加成词条」一致）。
 * <p>
 * 远程 = 伤害来源方为存活 {@link LivingEntity}，且满足以下任一：
 * <ul>
 *   <li>非直接命中（{@code !isDirect()}，如箭/次元枪子弹等以投射物为直接实体、活体为造成者）</li>
 *   <li>伤害类型带 {@link DamageTypeTags#IS_PROJECTILE} 标签</li>
 * </ul>
 * 供元素「弹射物弱点」触发与照片攻击类型增伤共用，避免两处口径漂移。
 */
public final class RangedAttackHelper {

    private RangedAttackHelper() {}

    /** 该伤害来源是否算作"远程"（口径：远程伤害加成词条）。 */
    public static boolean isRanged(DamageSource src) {
        return src.getEntity() instanceof LivingEntity
                && (!src.isDirect() || src.is(DamageTypeTags.IS_PROJECTILE));
    }
}
