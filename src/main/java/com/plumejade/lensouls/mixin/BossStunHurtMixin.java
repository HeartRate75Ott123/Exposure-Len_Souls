package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 定身暂停（破刹 / 时间定格）期间清除受击无敌帧：
 * 暂停实体刻会让 {@code invulnerableTime} 冻结在 20（1.21.1 无敌帧是
 * "伤害下限抑制"：{@code invulnerableTime > 10} 且 {@code amount <= lastHurt}
 * 直接拒绝），导致只有第一下命中、后续连击全被拒——HEAD 清零使每击必中。
 * <p>
 * 同时 RETURN 清除 {@code hurtTime}：伤害结算会把 {@code hurtTime} 置 10
 * 触发后仰受击动画，tick 暂停下永不衰减，模型卡在受击姿态（抽搐）——清零禁用。
 * <p>
 * 时间定格（全局 freeze）期间同样清无敌帧：非玩家实体在全局冻结时受击
 * 每击必中（连击特效核心）。
 */
@Mixin(LivingEntity.class)
public class BossStunHurtMixin {

    @Inject(method = "hurt", at = @At("HEAD"))
    private void lensouls$clearInvulnOnStunPause(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (StunPauseHelper.isStunPaused(self) || isGlobalFreeze(self)) {
            ((EntityInvulnerableTimeAccessor) (Object) self).lensouls$setInvulnerableTime(0);
        }
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void lensouls$clearHurtTimeOnStunPause(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (StunPauseHelper.isStunPaused(self) || isGlobalFreeze(self)) {
            self.hurtTime = 0;
        }
    }

    /** 服务端全局冻结（时间定格）期间，非玩家实体清无敌帧——玩家照常受击判定。 */
    private static boolean isGlobalFreeze(LivingEntity entity) {
        if (entity.level().isClientSide) return false;
        if (entity instanceof Player) return false;
        return com.plumejade.lensouls.ability.util.TimeFreezeManager.getInstance().isEntityFrozen(entity);
    }
}
