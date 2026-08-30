package com.plumejade.lensouls.mixin.compat;

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
 * 焰魔（灾变 cataclysm:ignis）定身期间被动防御解除（走原版伤害流程）。
 * <p>
 * {@code Ignis_Entity.hurt()} 有多处被动防御分支，全部以
 * {@code !source.is(BYPASSES_INVULNERABILITY)} 为进入条件：
 * <ul>
 *   <li>反击动画窗口（COUNTER/STRIKE/SHIELD_BREAK）内完全免疫 → return false</li>
 *   <li>ULTIMATE_ATTACK / 阶段切换时伤害 ×0.5 减半</li>
 *   <li>PHASE_2 / PHASE_3 / STRIKE 动画期间完全免疫</li>
 *   <li>{@code canBlockDamageSource}（护盾格挡）→ 格挡后 return false</li>
 * </ul>
 * 定身（韧性破定 / 时间定格）会暂停 AI，但这些被动仍持续生效导致玩家打不动。
 * 定身期间把 {@code BYPASSES_INVULNERABILITY} 判定强制改为 true、并让护盾格挡判定返回 false，
 * 使各防御分支短路，伤害落入原版结算（元素加伤 / 韧性免伤事件照常触发）。
 * 伤害桶 / DamageCap 已由 {@link CataclysmDamageBucketMixin} 处理，无需重复。
 */
@Mixin(value = com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity.class, remap = false)
public abstract class IgnisStunHurtMixin {

    /**
     * 定身期间强制 {@code BYPASSES_INVULNERABILITY} 返回 true，短路反击窗口免疫 / 阶段减半 / 阶段动画免疫。
     * 其余 TagKey（如 IS_PROJECTILE）不受影响。
     */
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

    /**
     * 定身期间让护盾格挡判定返回 false，解除 {@code canBlockDamageSource} 格挡分支。
     */
    @Redirect(
            method = "hurt",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/L_Ender/cataclysm/entity/AnimationMonster/BossMonsters/Ignis_Entity;canBlockDamageSource(Lnet/minecraft/world/damagesource/DamageSource;)Z",
                    remap = false),
            require = 0
    )
    private boolean lensouls$disableBlockWhenStunned(DamageSource source) {
        return !StunPauseHelper.isStunPaused((Entity) (Object) this);
    }
}
