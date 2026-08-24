package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.integration.BossPhotoProjHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捕获玩家攻击动作（原版命中 / BetterCombat 命中的原版路径），触发 Boss 照片弹幕判定。
 */
@Mixin(Player.class)
public abstract class PlayerSwingMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void lensouls$onAttack(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer sp) {
            com.plumejade.lensouls.LenSouls.LOGGER.info("[PhotoBoss] Player#attack target={}", target);
            BossPhotoProjHelper.onSwing(sp);
        }
    }
}
