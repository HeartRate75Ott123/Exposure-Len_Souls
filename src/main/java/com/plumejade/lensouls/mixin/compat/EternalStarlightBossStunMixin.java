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
 * 永恒星光双 BOSS 定身被动取消（lunar_monstrosity / starlight_golem）。
 * <p>
 * 两者 {@code hurt()} 都在「{@code !source.is(BYPASSES_INVULNERABILITY)}」时进入防御性被动：
 * <ul>
 *   <li>{@code LunarMonstrosity}：Sneak/Soul 阶段完全免疫 + 单次伤害上限 3 点</li>
 *   <li>{@code StarlightGolem}：{@code hasProtection()} 时完全免疫</li>
 * </ul>
 * 定身（韧性破定 / 时间定格，见 {@link StunPauseHelper#isStunPaused}）暂停其 AI 后这些被动冻结常驻，
 * 伤害无法落入 {@code super.hurt()}。定身期间把 {@code BYPASSES_INVULNERABILITY} 判定强制改为 true，
 * 使被动分支短路，伤害走原版 {@code super.hurt()} 正常结算（元素加伤 / 韧性免伤事件照常触发）。
 */
@Mixin(targets = {
        "cn.leolezury.eternalstarlight.common.entity.living.boss.monstrosity.LunarMonstrosity",
        "cn.leolezury.eternalstarlight.common.entity.living.boss.golem.StarlightGolem"
}, remap = false)
public abstract class EternalStarlightBossStunMixin {

    @Redirect(
            method = "hurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z",
                    remap = false),
            require = 0
    )
    private boolean lensouls$bypassStunPassive(DamageSource source, TagKey<DamageType> tag) {
        if (tag == DamageTypeTags.BYPASSES_INVULNERABILITY
                && StunPauseHelper.isStunPaused((net.minecraft.world.entity.Entity) (Object) this)) {
            return true;
        }
        return source.is(tag);
    }
}
