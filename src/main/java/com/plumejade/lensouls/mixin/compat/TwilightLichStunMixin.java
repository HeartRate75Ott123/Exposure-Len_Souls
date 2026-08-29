package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 巫妖（暮色森林）定身走原版受伤流程。
 * <p>
 * {@code Lich.hurt} 内置多处自定义免伤/传送：隐身无敌（getTeleportInvisibility）、护盾免伤、
 * 影分身免伤、以及受击累计若干次后 {@code teleportToNewTarget}「消失」。破刹（韧性破定）或时间定格
 * 期间令其完全可打、不逃走——与看门人自动格挡取消、圣骑强行走原版同一门控原语
 * {@link StunPauseHelper#isStunPaused}。
 * <p>
 * mixin 继承 {@code Monster}（Lich 的祖先类，原版必定可编译），{@code super.hurt} 解析到 Lich 父类
 * 链上的原版 {@code LivingEntity.hurt}，跳过 Lich 的全部自定义伤害门控。
 */
@Mixin(targets = "twilightforest.entity.boss.Lich", remap = false)
public abstract class TwilightLichStunMixin extends Monster {

    public TwilightLichStunMixin(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lensouls$lichVanillaWhenStunned(DamageSource src, float damage, CallbackInfoReturnable<Boolean> cir) {
        if (StunPauseHelper.isStunPaused((Entity) (Object) this)) {
            cir.setReturnValue(super.hurt(src, damage));
        }
    }
}
