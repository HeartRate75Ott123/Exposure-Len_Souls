package com.plumejade.lensouls.mixin.compat;

import com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ender_Guardian_Entity;
import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 末影守卫（灾变 {@code cataclysm:ender_guardian}）定身期间被动免疫解除。
 * <p>
 * <b>症状（实测）</b>：定身在特定动画帧后，打上去「空挥」——无伤害、无受击红闪。
 * <p>
 * <b>根因（反编译 3.32 确认）</b>：{@code Ender_Guardian_Entity.hurt} 第一段：
 * <pre>
 *   if (getAnimation() == GUARDIAN_MASS_DESTRUCTION &amp;&amp; !source.is(BYPASSES_INVULNERABILITY)) return false;
 * </pre>
 * 即「质量毁灭」动画期间完全免疫。破刹/时间定格暂停 {@code LivingEntity.tick} 后，
 * 该动画的收尾/阶段推进被卡在这段窗口里（相位与目标条件都冻住），伤害闸门就一直关着。
 * <p>
 * <b>解法</b>：与 {@code IgnisStunHurtMixin} / {@link MaledictusStunHurtMixin} 同款，
 * 定身期间把 {@code BYPASSES_INVULNERABILITY} 读数强制为 {@code true}，闸门短路。
 * 注意重定向只作用于本类的 {@code hurt}，基类 {@code LLibrary_Boss_Monster.hurt} 里的
 * DamageCap / 距离限制 / 伤害桶仍按真实伤害来源判定。
 * <p>
 * 头盔阶段的弓箭免疫（{@code !getIsHelmetless() && directEntity instanceof AbstractArrow}）
 * 与本闸门无关，已由 {@code ProjectileImmunityMixins$CataEnderGuardian} 单独处理。
 */
@Mixin(value = Ender_Guardian_Entity.class, remap = false)
public abstract class EnderGuardianStunHurtMixin {

    @Redirect(
            method = "hurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z",
                    remap = false),
            require = 0
    )
    private boolean lensouls$bypassStunPassive(DamageSource source, TagKey<DamageType> tag) {
        if (tag == DamageTypeTags.BYPASSES_INVULNERABILITY
                && StunPauseHelper.isStunPaused((Entity) (Object) this)) {
            return true;
        }
        return source.is(tag);
    }
}
