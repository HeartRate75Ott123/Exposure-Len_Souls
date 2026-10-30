package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.handler.CopySoulDropHandler;
import com.plumejade.lensouls.handler.FeatherHardmanHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * 永恒星光看门人（the_gatekeeper）**挑战胜利**时补做「死亡事件本该做的事」（永恒星光兼容）。
 * <p>
 * 标准挑战战中 {@code TheGatekeeper.die()} 不调 {@code super.die()}，而是走
 * {@code permitPlayer → trySpawnLoot() → abortFight()}：Boss 回血并传送离开，从未真正死亡，
 * 因此 {@code LivingDeathEvent} / {@code LivingDropsEvent} 都不触发。这里在
 * {@code abortFight()} 调用前（原奖励已生成、Boss 尚未回血离开时）补两件事：
 * <ol>
 *   <li>{@link CopySoulDropHandler}：就地生成 5~20 个复制之魂掉落实体 → 「挑战成功即掉落」；</li>
 *   <li>{@link FeatherHardmanHandler#creditBossKill}：把这次挑战胜利记成一次 BOSS 击杀
 *       （荒厄遗咒① 的「击杀 4 只不同 BOSS」反转条件，走首领清单判定）。</li>
 * </ol>
 * 真死路径（虚空/穿甲/非标准战）走 {@code super.die()}，不经过 {@code abortFight()}，
 * 由正常死亡事件处理，两条路径互斥、不会重复结算。
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
    private void lensouls$onChallengeWin(DamageSource source, CallbackInfo ci) {
        var self = (net.minecraft.world.entity.LivingEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)) return;
        // 挑战者先解析出来：下面两条都要用（掉落倍率 / 诅咒击杀进度）
        ServerPlayer winner = fightTarget(self);
        if (winner == null && source != null && source.getEntity() instanceof ServerPlayer sp) {
            winner = sp;
        }
        // ① 复制之魂：挑战成功即掉落；⑤ 铁序·厄运 反转后「掉落 +50%」在这里同样生效
        //   （这条路径不进 LivingDeathEvent，所以必须自己带上倍率，否则同一只 boss 换条死法就没加成了）
        if (source != null) {
            CopySoulDropHandler.spawnCopySoulDrop(serverLevel, self.getX(), self.getY(), self.getZ(),
                    CopySoulDropHandler.copySoulMult(winner));
        }
        // ② 诅咒进度：挑战成功算一次 BOSS 击杀（没有 LivingDeathEvent，只能在这里补）
        if (winner != null) {
            FeatherHardmanHandler.creditBossKill(winner, self);
        }
    }

    /**
     * 挑战者：守门人把「向它发起挑战的玩家」存在自己的 {@code getFightTarget()}（返回 {@code Optional}）。
     * 本模组对它没有编译依赖，只能反射调用。
     */
    private static ServerPlayer fightTarget(Object self) {
        try {
            Object r = self.getClass().getMethod("getFightTarget").invoke(self);
            if (r instanceof Optional<?> opt && opt.orElse(null) instanceof ServerPlayer sp) return sp;
        } catch (Throwable ignored) {
        }
        return null;
    }
}
