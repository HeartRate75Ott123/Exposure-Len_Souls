package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.util.BlessingCrystalMarker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 末影龙照片的「赐福水晶」不可被伤害。
 * <p>
 * 原版 {@code EndCrystal.hurt} 在通过 {@code isInvulnerableTo} 之后会
 * {@code explode(..., 6.0F, false, ExplosionInteraction.BLOCK)}：既炸玩家又炸地形。
 * {@code setInvulnerable(true)} 挡不住创造模式玩家与 {@code BYPASSES_INVULNERABILITY}
 * （见 {@code Entity.isInvulnerableTo} 的放行条件），所以在 {@code hurt} 的 HEAD 上对本模组
 * 标记过的水晶直接返回 false —— 这是唯一的完全免疫点。
 * <p>
 * <b>作用域</b>：只对带 {@link BlessingCrystalMarker#KEY} 标记的水晶生效，
 * 末地原生水晶、其它模组的水晶、原版末影龙战流程一律不受影响。
 */
@Mixin(EndCrystal.class)
public abstract class BlessingCrystalMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void lensouls$blessingCrystalImmune(DamageSource source, float amount,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (BlessingCrystalMarker.isMarked((EndCrystal) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
