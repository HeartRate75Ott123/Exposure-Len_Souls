package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.boss.BossBarCache;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 幻灵借体 BOSS 的血条：拒绝重新加人。
 *
 * <p>光靠 {@code BossBarCache.clearBossBar} 里的 {@code removeAllPlayers()} 只能清掉<b>当时</b>的玩家。
 * 暮色的幻影骑士会在 {@code setNumber(0)} 里把 64 格内玩家重新加回血条
 * （{@code updateMyNumber()} 在幻灵期间每 tick 都会触发一次），于是血条被反复加回、一直显示。
 * 本 mixin 在 {@code addPlayer} 的 HEAD 拦住所有已登记为幻灵血条的加人请求；
 * 原版与各模组直接用 {@code ServerBossEvent} 的 BOSS 走这一条就够了。
 * <p>暮色覆写了 {@code addPlayer}（自己的加条包），需另一条
 * {@code compat/ServerTFBossBarMixin} 才能掐住，见那个类。
 */
@Mixin(ServerBossEvent.class)
public abstract class ServerBossEventMixin {

    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
    private void lensouls$skipPhantomBar(ServerPlayer player, CallbackInfo ci) {
        if (BossBarCache.isPhantomBar((ServerBossEvent) (Object) this)) {
            ci.cancel();
        }
    }
}
