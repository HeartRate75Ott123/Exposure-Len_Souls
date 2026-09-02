package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * block_factorys_bosses 小怪/召唤物定身兼容（破刹 / 时间定格）。
 * <p>
 * 这些类自己重写了 {@code LivingEntity.tick()}，通用 {@link com.plumejade.lensouls.mixin.BossStunTickMixin}
 * （注入在 {@code LivingEntity.tick} 基类）的虚分发碰不到它们，因此逐个 target 并在定身时整段取消：
 * 移动/攻击/AI/动画推进全部暂停（与 5 个 boss 的处理一致）。
 * <p>
 * 不重写 tick 的 block_factorys 小怪（crossbow_pirate/pirate_captain/ghost_tentacle/kraken_tentacle 等）
 * 不在本列表 —— 它们已由 {@code BossGuardHelper} 收窄后重新落入通用 {@code BossStunTickMixin} 冻结。
 * <p>
 * 死亡放行：{@code isDeadOrDying()} 时不取消，保证 tickDeath 正常执行。
 */
@Mixin(targets = {
        "net.unusual.block_factorys_bosses.entity.monster.SoulSkeletonEntity",
        "net.unusual.block_factorys_bosses.entity.monster.SoulKnightWitherSkeletonEntity",
        "net.unusual.block_factorys_bosses.entity.boss.dragon.guardians.FlamingSkeletonGuardSwordEntity",
        "net.unusual.block_factorys_bosses.entity.boss.dragon.guardians.FlamingSkeletonGuardFireballEntity",
        "net.unusual.block_factorys_bosses.entity.boss.dragon.guardians.DragonGuardSwordEntity"
}, remap = false)
public class BlockFactorysMobStunMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lensouls$cancelTickWhenStunned(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isDeadOrDying()) return;
        if (StunPauseHelper.isStunPaused(self)) {
            ci.cancel();
        }
    }
}
