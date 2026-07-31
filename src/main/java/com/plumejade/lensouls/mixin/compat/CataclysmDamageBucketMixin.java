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
 * 灾变 BOSS 伤害限制绕过 Mixin。
 * <p>
 * 两层绕过逻辑：
 * <ol>
 *   <li><b>桶限制全移除</b>：无条件清空 damageBucket，每次攻击全额生效（不再 tps 过高变 0.1）</li>
 *   <li><b>破定绕上限</b>：破定期间 + 元素弱点武器 → 绕过 Math.min(DamageCap(), amount)（单次上限）</li>
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

        // ① 桶限制全移除：无条件清空，每次攻击全额生效
        this.damageBucket = 0;

        // ② 破定 + 元素弱点 → 设置单次上限绕过标志
        LivingEntity self = (LivingEntity) (Object) this;
        ElementBypassHelper.evaluateAndShouldBypassBucket(source, self);
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
