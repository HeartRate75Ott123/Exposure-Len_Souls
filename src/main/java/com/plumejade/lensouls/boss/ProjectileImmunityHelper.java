package com.plumejade.lensouls.boss;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * 传奇怪物 / 灾变「弹射物免疫」的统一解除出口。
 * <p>
 * 需求原文：「为传奇怪物、灾变去除弹射物免疫（定身的去被动机制的部分实现，直接下放到全局）」。
 * 这两批 boss 有<b>两套</b>写法，本类各提供一个出口：
 * <ol>
 *   <li>{@link #allowProjectile} —— 以 {@code source.is(DamageTypeTags.IS_PROJECTILE)} 作开关；</li>
 *   <li>{@link #hideProjectileDirectEntity} —— 以
 *       {@code source.getDirectEntity() instanceof AbstractArrow / ThrownPotion} 等类型判定作开关
 *       （传奇怪物 19 个类、灾变 5 个类；传奇怪物的判定还额外被其
 *       {@code ModConfig.MobConfig.*projectile} 配置项包着）。</li>
 * </ol>
 * 两种写法此前都只在定身（破刹 / 时间定格）期间由 {@code *StunPassiveMixin} 解除，现在恒定解除。
 */
public final class ProjectileImmunityHelper {

    private ProjectileImmunityHelper() {}

    /**
     * 需要解除弹射物免疫的判定点：恒返回 false（=「这不是投射物」），其余 TagKey 照常。
     *
     * @param source 原始伤害来源
     * @param tag    {@code DamageSource#is} 的入参
     * @return 若查询的是投射物标签则恒为 false，否则等于 {@code source.is(tag)}
     */
    public static boolean allowProjectile(DamageSource source, TagKey<DamageType> tag) {
        if (tag == DamageTypeTags.IS_PROJECTILE) {
            return false;
        }
        return source.is(tag);
    }

    /**
     * 供 {@code @Redirect(DamageSource#getDirectEntity)} 使用：把<b>外来投射物</b>的返回值置空，
     * 使 Boss 自己的 {@code instanceof <投射物类>} 免伤分支失效。
     * <p>
     * <b>保留「自己的弹射物不打自己」的自伤保护</b>：当投射物的 owner 就是该 Boss 时原样返回。
     * 这一点必须保留——灾变 {@code Kobolediator}/{@code Wadjet} 的毒镖免疫、
     * {@code Ender_Guardian} 自己的子弹免疫与弓箭免疫<b>共用同一个局部变量</b>，
     * 无差别置空会让它们被自己的弹射物打伤。
     * <p>
     * 非投射物（例如 {@code AreaEffectCloud} 滞留药水云）原样返回，不在此需求范围内。
     *
     * @param source 原始伤害来源
     * @param self   触发判定的 Boss 自身（{@code (Entity) (Object) this}）
     * @return 外来投射物 → {@code null}（判定必然为 false）；其余原样返回
     */
    public static Entity hideProjectileDirectEntity(DamageSource source, Entity self) {
        Entity direct = source.getDirectEntity();
        if (!(direct instanceof Projectile projectile)) {
            return direct;
        }
        if (self != null && projectile.getOwner() == self) {
            return direct;   // 自己的弹射物：保留自伤保护
        }
        return null;
    }
}
