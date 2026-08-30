package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.handler.CopySoulDropHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 永恒星光看门人（the_gatekeeper）挑战胜利掉落复制之魂（永恒星光兼容）。
 * <p>
 * 标准挑战战中 {@code TheGatekeeper.die()} 不调 {@code super.die()}，而是走
 * {@code permitPlayer → trySpawnLoot() → abortFight()}：Boss 回血并传送离开，从未真正死亡，
 * 因此 {@code LivingDeathEvent} / {@code LivingDropsEvent} 都不触发，
 * {@link CopySoulDropHandler} 的正常死亡掉落对挑战胜利无效。
 * <p>
 * 在 {@code die()} 的 {@code abortFight()} 调用前（即原奖励已生成、Boss 尚未回血离开时）
 * 就地生成 5~20 个复制之魂掉落实体，实现「挑战成功即掉落」。
 * 真死路径（虚空/穿甲/非标准战）走 {@code super.die()}，不经过 {@code abortFight()}，
 * 由正常死亡事件处理，不会重复掉落。
 */
@Mixin(targets = "cn.leolezury.eternalstarlight.common.entity.living.boss.gatekeeper.TheGatekeeper", remap = false)
public abstract class GatekeeperSoulDropMixin {

    @Inject(
            method = "die",
            at = @At(value = "INVOKE",
                    target = "Lcn/leolezury/eternalstarlight/common/entity/living/boss/gatekeeper/TheGatekeeper;abortFight()V",
                    remap = false),
            require = 0
    )
    private void lensouls$spawnSoulOnDefeat(DamageSource source, CallbackInfo ci) {
        if (source == null) return;
        var self = (net.minecraft.world.entity.LivingEntity) (Object) this;
        if (self.level() instanceof ServerLevel serverLevel) {
            CopySoulDropHandler.spawnCopySoulDrop(serverLevel,
                    self.getX(), self.getY(), self.getZ());
        }
    }
}
