package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 焰魔（灾变 cataclysm:ignis）定身整段暂停 tick。
 * <p>
 * {@code Ignis_Entity} 完全重写 {@code tick()}（不调 super），其实现分两段：
 * <ul>
 *   <li>前半段（offset 0 起）：动画状态机——格挡/护盾进度、反击动画触发
 *       （{@code setAnimation(COUNTER/STRIKE)} + {@code sendAnimationMessage} 同步客户端）、
 *       AI 目标选择 + 攻击动画；</li>
 *   <li>末尾：{@code super.tick()} → {@code LivingEntity.tick}（由
 *       {@link com.plumejade.lensouls.mixin.BossStunTickMixin} 取消，aiStep 伤害已被挡）。</li>
 * </ul>
 * 原 {@code BossStunTickMixin} 只取消链末的 {@code LivingEntity.tick}，前半段动画状态机在
 * 取消前照跑 → 定身时仍触发反击/攻击动画（客户端播放）。本 mixin 直接在 HEAD 整段取消，
 * 动画状态机/反击/护盾进度/攻击动画全部停止，与 block_factorys 的 boss 定身处理一致。
 */
@Mixin(value = com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity.class, remap = false)
public abstract class IgnisStunMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lensouls$pauseTickOnStun(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isDeadOrDying()) return; // 死亡放行：tickDeath 需要执行
        if (StunPauseHelper.isStunPaused(self)) {
            ci.cancel();
        }
    }
}
