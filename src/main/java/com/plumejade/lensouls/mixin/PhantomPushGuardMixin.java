package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.entity.PhantomDamageHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 幻灵来源不得「推动 / 牵引」玩家。
 * <p>
 * 需求：虚影幻灵的召唤物（利维坦的牵引、其它 BOSS 随从的位移类特殊效果）对玩家赦免——
 * 不只是伤害与药水效果，物理推挤/吸引也要一并免除。
 * <p>
 * 借体 BOSS 及其召唤物在 {@link PhantomDamageHandler#isPhantomEntity} 层面带
 * {@code lensouls:phantom} / {@code lensouls:phantom_minion} 标记；此处拦截带来源的
 * {@code Entity#push(Entity)}（NeoForge 提供的重载，能追溯到推动者），
 * 当被推动者是玩家、且推动者属于幻灵来源时直接取消。
 * <p>
 * 注：直接 {@code setDeltaMovement} 的位移实现没有来源信息，无法在此拦截；
 * 若仍有某个召唤物能拉动玩家，需要针对该模组的具体实现补一个兼容处理。
 */
@Mixin(Entity.class)
public abstract class PhantomPushGuardMixin {

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void lensouls$noPushFromPhantom(Entity pusher, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player)) return;
        if (PhantomDamageHandler.isPhantomSource(pusher)) {
            ci.cancel();
        }
    }
}
