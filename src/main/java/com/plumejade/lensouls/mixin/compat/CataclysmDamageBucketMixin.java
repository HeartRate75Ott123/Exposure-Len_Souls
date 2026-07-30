package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.BossToughnessManager;
import com.plumejade.lensouls.damage.ElementBypassHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 灾变 BOSS 伤害桶 + 单次伤害上限绕过 Mixin。
 * <p>
 * 三层绕过逻辑：
 * <ol>
 *   <li>破定中 → 清空桶（已有逻辑）</li>
 *   <li>武器有元素活性 + 目标有弱点 → 清空桶（不限等级）</li>
 *   <li>武器活性等级 5 + 目标有弱点 → 绕过 Math.min(DamageCap(), amount)（单次上限）</li>
 * </ol>
 */
@Mixin(value = {
        com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.LLibrary_Boss_Monster.class,
        com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.IABoss_monster.class
}, remap = false)
public abstract class CataclysmDamageBucketMixin {

    @Shadow(remap = false)
    private float damageBucket;

    @Inject(method = "hurt", at = @At("HEAD"), remap = false, require = 0)
    private void lensouls$resetBucket(DamageSource source, float amount,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        LivingEntity self = (LivingEntity) (Object) this;

        // ① 破定中 → 清空桶
        var manager = BossToughnessManager.getInstance();
        if (manager.has(self)) {
            var data = manager.get(self);
            if (data != null && data.isBroken()) {
                this.damageBucket = 0;
                return; // 破定优先，不继续检查元素绕过
            }
        }

        // ② 元素活性 + 弱点 → 清空桶（同时可能设置 cap 绕过标志）
        if (ElementBypassHelper.evaluateAndShouldBypassBucket(source, self)) {
            this.damageBucket = 0;
        }
    }

    @ModifyArg(
            method = "hurt",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F", ordinal = 0),
            index = 0,
            remap = false,
            require = 0
    )
    private float lensouls$bypassDamageCap(float capValue) {
        if (ElementBypassHelper.shouldBypassCap()) {
            return Float.MAX_VALUE;
        }
        return capValue;
    }
}
