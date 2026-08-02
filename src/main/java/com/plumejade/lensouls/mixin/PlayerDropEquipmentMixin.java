package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.handler.FeatherTwitcherHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 扭曲值满 100 死亡强制掉落：绕过 {@link Player#dropEquipment()} 中的 keepInventory 检查，
 * 使死亡掉落物正常产生（墓碑由其他模组接管）。
 * <p>
 * 仅佩戴扭曲羽毛且本次死亡被打上 {@link FeatherTwitcherHandler#KEY_FORCE_DROP} 标记时生效，
 * 标记在消费后清除，不影响其他死亡。
 */
@Mixin(Player.class)
public class PlayerDropEquipmentMixin {

    @Redirect(method = "dropEquipment",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean lensouls$forceDropOnTwistDeath(GameRules rules,
                                                   GameRules.Key<GameRules.BooleanValue> key) {
        if (key == GameRules.RULE_KEEPINVENTORY
                && (Object) this instanceof ServerPlayer sp
                && FeatherTwitcherHandler.hasTwitcher(sp)
                && sp.getPersistentData().getBoolean(FeatherTwitcherHandler.KEY_FORCE_DROP)) {
            sp.getPersistentData().putBoolean(FeatherTwitcherHandler.KEY_FORCE_DROP, false);
            return false;
        }
        return rules.getBoolean(key);
    }
}
