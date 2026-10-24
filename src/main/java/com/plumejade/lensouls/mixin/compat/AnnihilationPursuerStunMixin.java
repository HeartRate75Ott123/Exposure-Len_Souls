package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.SpaceStation.Flameborn.AnnihilationPursuer.AnnihilationPursuerEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 湮灭猎影（传奇怪物 {@code legendary_monsters:annihilation_pursuer}）定身兼容。
 * <p>
 * <b>症状（实测）</b>：被破刹/时间定格在「传送类动画」的某几帧时，实体直接**消失**
 * （不是走出视野，模型+碰撞都看不见了），之后也一直不回来。
 * <p>
 * <b>根因（反编译 legendary_monsters 2.1.20 确认）</b>：
 * <ol>
 *   <li>它**重写了 {@code tick()}**（不调 {@code super.tick()} 之前先跑自己的 {@code UpdateWithAttack()}），
 *       而通用 {@code BossStunTickMixin} 只取消 {@code LivingEntity.tick} 基类体
 *       ⇒ 定身期间它自己的攻击序列（传送/连段/冷却）**照跑**，随时把自己传到别处；</li>
 *   <li>它的**隐身是客户端逐刻按状态算出来的**：
 *       {@code tick()} 里 {@code if (level().isClientSide && isDuringTeleportation()) setInvisible(true);}
 *       而 {@code isDuringTeleportation()} 读的是 {@code attackState + attackTicks} 的窗口
 *       （例如 state 4 且 8 &lt; attackTicks &lt; 13）。实体刻被暂停后 {@code tickCount}/动画时钟冻结，
 *       整个窗口就停在那里 ⇒ 每一客户端 tick 都把 {@code invisible} 重新置为 true，看起来就是「实体消失了」；</li>
 *   <li>同一个窗口还带伤害免疫：{@code hurt()} 里
 *       {@code if (isDuringTeleportation() && !source.is(BYPASSES_INVULNERABILITY)) return false;}。</li>
 * </ol>
 * <b>解法</b>：定身期间（非死亡）把它的 {@code tick()} 整段暂停（与 {@code IgnisStunMixin} /
 * {@code BlockFactorysBossStunMixin} 一致，真正冻住攻击序列），并在暂停前做两件收尾：
 * <ul>
 *   <li>若此刻正处于「传送中 / 潜伏」窗口 ⇒ {@code setAttackState(0)}。
 *       {@code onSyncedDataUpdated} 会 {@code stopAllAnimationStates()} 并切回 idle，
 *       既解掉 {@code invisible}（下一客户端 tick 的 {@code isDuringTeleportation()} 变成 false），
 *       也解掉传送期伤害免疫，还把模型从「缩放到 0 的传送帧」拉回正常姿态；</li>
 *   <li>{@code setInvisible(false)} 兜底（服务器端改同步标志，客户端本地那半也被暂停了，不会被写回 true）。</li>
 * </ul>
 * 定身结束后 tick 恢复：状态是 0，AI 的 {@code IAttackGoal} 会重新起手，不残留半截传送。
 * <p>
 * 死亡放行（{@code isDeadOrDying} 时不动），保证 {@code tickDeath}/死亡动画正常执行。
 */
@Mixin(value = AnnihilationPursuerEntity.class, remap = false)
public abstract class AnnihilationPursuerStunMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lensouls$pauseTickOnStun(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isDeadOrDying()) return; // 死亡放行：tickDeath 需要执行
        if (!StunPauseHelper.isStunPaused(self)) return;

        AnnihilationPursuerEntity boss = (AnnihilationPursuerEntity) self;
        if (!self.level().isClientSide) {
            if (boss.isDuringTeleportation() || boss.isSleep()) {
                boss.setAttackState(0);
            }
        }
        if (self.isInvisible()) {
            self.setInvisible(false);
        }
        ci.cancel();
    }
}
