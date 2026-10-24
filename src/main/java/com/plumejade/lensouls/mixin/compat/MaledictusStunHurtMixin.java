package com.plumejade.lensouls.mixin.compat;

import com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.Maledictus.Maledictus_Entity;
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
 * 咒翼灵骸（灾变 {@code cataclysm:maledictus}）定身期间被动免疫解除。
 * <p>
 * <b>症状（实测）</b>：破刹/时间定格把它定在特定动画帧之后，玩家怎么打都「空挥」——
 * 没有伤害、没有受击红闪、伤害数字也不出。
 * <p>
 * <b>根因（反编译 3.32 确认）</b>：{@code Maledictus_Entity.hurt} 的第一段就是伤害闸门
 * <pre>
 *   if ((getAttackState() == 31 || == 32 || == 33) &amp;&amp; !source.is(BYPASSES_INVULNERABILITY)) return false;
 * </pre>
 * 31/32/33 是抓取/处决一类的动画相位，由动画状态机推进。定身暂停 {@code LivingEntity.tick}
 * （{@code aiStep} 在内）后，动画队列/目标条件卡住，相位停在 31~33 不回落到 0
 * ⇒ 之后每一次攻击都在第一行被 {@code return false} 掉，连受击动画都不触发。
 * <p>
 * <b>解法</b>：与 {@code IgnisStunHurtMixin} 同款——定身期间把
 * {@code BYPASSES_INVULNERABILITY} 的读数强制为 {@code true}，让闸门短路，
 * 伤害落回原版结算（{@code IABoss_monster.hurt} 的 DamageCap / 距离限制 / 伤害桶照常生效，
 * 因为那些判定在**基类**自己的 {@code hurt} 里，不受本重定向影响）。
 * 不直接改 attackState：相位还等着定身结束后继续推进，强改会让它的动作序列错位。
 */
@Mixin(value = Maledictus_Entity.class, remap = false)
public abstract class MaledictusStunHurtMixin {

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
