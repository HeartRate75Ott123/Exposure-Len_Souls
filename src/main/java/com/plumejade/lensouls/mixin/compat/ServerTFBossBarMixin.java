package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.BossBarCache;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 暮色森林 {@code ServerTFBossBar#addPlayer}：与 {@code ServerBossEventMixin} 同一道闸。
 *
 * <p>暮色把这个方法<b>整个覆写</b>了（发自己的 {@code TFBossBarPacket.AddTFBossBarPacket}），
 * 不经过 {@code ServerBossEvent.addPlayer}，所以父类上的 mixin 拦不到它。
 * 而幻影骑士正是靠 {@code setNumber(0) → getBossBar().addPlayer(...)} 每 tick 把血条加回来的。
 * <p>暮色缺席时整条被跳过（compat 配置 {@code required:false} + 字符串 target + {@code require = 0}）。
 */
@Mixin(targets = "twilightforest.entity.boss.bar.ServerTFBossBar", remap = false)
public abstract class ServerTFBossBarMixin {

    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lensouls$skipPhantomBar(ServerPlayer player, CallbackInfo ci) {
        if (BossBarCache.isPhantomBar((ServerBossEvent) (Object) this)) {
            ci.cancel();
        }
    }
}
