package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.BossGuardHelper;
import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * block_factorys_bosses 定身兼容（破刹 / 时间定格）。
 * <p>
 * 这些 boss 的转阶段逻辑写在 {@code tick()} 内（依赖血量），故 {@link com.plumejade.lensouls.mixin.BossStunTickMixin}
 * 不再整段取消它们的 tick。本 mixin 在 tick 末尾选择性压制：定身时关 AI、关重力、清零速度
 * （原地静止、不追目标、不位移、不攻击），tick 仍照常跑 → 血量转阶段正常，不卡阶段。
 * 解冻后恢复 AI / 重力。
 * <p>
 * 视觉「乱动」与动画关键帧伤害由本 mixin 冻结 GeckoLib 动画时间（回写 tickCount）负责，不再依赖 GeckoLibAnimFreezeMixin。
 */
@Mixin(targets = {
        "net.unusual.block_factorys_bosses.entity.boss.yeti.YetiEntity",
        "net.unusual.block_factorys_bosses.entity.boss.knight.UnderworldKnightEntity",
        "net.unusual.block_factorys_bosses.entity.boss.dragon.boss.InfernalDragonEntity",
        "net.unusual.block_factorys_bosses.entity.boss.kraken.KrakenEntity",
        "net.unusual.block_factorys_bosses.entity.boss.sandworm.SandwormEntity"
}, remap = false)
public class BlockFactorysBossStunMixin {

    @Shadow public int tickCount;
    @Unique private int lensoulsPreTick;

    @Inject(method = "tick", at = @At("HEAD"), remap = false, require = 0)
    private void lensoulsCaptureTickCount(CallbackInfo ci) {
        this.lensoulsPreTick = this.tickCount;
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false, require = 0)
    private void lensouls$suppressWhenStunned(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (StunPauseHelper.isStunPaused(self)) {
            // 原地定身：关重力（不下坠）、清零速度（不漂移）、关 AI（不追目标/不执行攻击目标）
            self.setNoGravity(true);
            self.setDeltaMovement(Vec3.ZERO);
            if (self instanceof Mob mob) mob.setNoAi(true);
            self.tickCount = this.lensoulsPreTick;
        } else if (BossGuardHelper.isBlockFactorysBoss(self)) {
            // 解冻后恢复（仅对这些 boss 恢复，避免误改其它实体状态）
            self.setNoGravity(false);
            if (self instanceof Mob mob) mob.setNoAi(false);
        }
    }
}
