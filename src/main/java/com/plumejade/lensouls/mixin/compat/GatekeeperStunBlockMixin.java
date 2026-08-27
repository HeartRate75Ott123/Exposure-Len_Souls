package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 看门人自动格挡取消（永恒星光兼容）。
 * <p>
 * {@code TheGatekeeper.hurt()} 用两个「提前 return false」实现自动格挡，判定条件是
 * {@code !source.is(BYPASSES_INVULNERABILITY)}。定身（韧性破定 / 时间定格）会暂停其 AI，
 * 使 {@code getBehaviorState()} 冻结在 0（空闲）→ 格挡常驻、完全免疫伤害。
 * 定身期间把这两处 {@code BYPASSES_INVULNERABILITY} 判定强制改为 true，使格挡分支短路，
 * 伤害落入 {@code super.hurt()} 正常结算。
 */
@Mixin(targets = "cn.leolezury.eternalstarlight.common.entity.living.boss.gatekeeper.TheGatekeeper", remap = false)
public abstract class GatekeeperStunBlockMixin {

    @Redirect(
            method = "hurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z",
                    remap = false),
            require = 0
    )
    private boolean lensouls$bypassBlockWhenFrozen(DamageSource source, TagKey<DamageType> tag) {
        if (tag == DamageTypeTags.BYPASSES_INVULNERABILITY
                && StunPauseHelper.isStunPaused((net.minecraft.world.entity.Entity) (Object) this)) {
            return true;
        }
        return source.is(tag);
    }
}
