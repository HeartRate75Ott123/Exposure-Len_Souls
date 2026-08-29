package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * block_factorys_bosses 定身兼容（破刹 / 时间定格）。
 * <p>
 * 这些 boss 都重写了 {@code LivingEntity.tick()}，其攻击/移动/转阶段/GeckoLib 动画推进
 * 全部写在自己的 {@code tick()}（及 {@code customServerAiStep}）里。
 * 注意：{@link com.plumejade.lensouls.mixin.BossStunTickMixin} 只作用在 {@code LivingEntity.tick} 基类上，
 * 虚分发碰不到这些被重写的 tick，因此对它们无效。本 mixin 直接 {@code targets} 这 5 个 boss 类，
 * 命中其重写的 {@code tick} 并在定身时整段取消 —— 攻击/位移/AI/动画/转阶段全部暂停。
 * <p>
 * 转阶段不会永久卡死：阶段逻辑靠掉血触发、写在 tick 内；定身期间只是暂停，解冻后 tick 恢复即继续推进。
 * 死亡放行：{@code isDeadOrDying()} 时不取消，保证 tickDeath 执行（尸体正常消失）。
 */
@Mixin(targets = {
        "net.unusual.block_factorys_bosses.entity.boss.yeti.YetiEntity",
        "net.unusual.block_factorys_bosses.entity.boss.knight.UnderworldKnightEntity",
        "net.unusual.block_factorys_bosses.entity.boss.dragon.boss.InfernalDragonEntity",
        "net.unusual.block_factorys_bosses.entity.boss.kraken.KrakenEntity",
        "net.unusual.block_factorys_bosses.entity.boss.sandworm.SandwormEntity"
}, remap = false)
public class BlockFactorysBossStunMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lensoulsCancelTickWhenStunned(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isDeadOrDying()) return;
        if (StunPauseHelper.isStunPaused(self)) {
            ci.cancel();
        }
    }
}
