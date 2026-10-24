package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.CollapsedKingdom.PosessedPaladinEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 堕落圣骑（传奇怪物 {@code legendary_monsters:posessed_paladin}）定身期间被动免疫解除。
 * <p>
 * <b>症状（实测）</b>：破刹/时间定格后，攻击它会出现「空挥」——无伤害、无受击红闪。
 * <p>
 * <b>根因（反编译 legendary_monsters 2.1.20 确认）</b>：{@code PosessedPaladinEntity.hurt} 开头两道闸：
 * <pre>
 *   if (getAttackState() == 15) return false;                                  // ① 无 bypass 口子
 *   if (isSleep() &amp;&amp; !source.is(BYPASSES_INVULNERABILITY)) return false;        // ② 沉睡态
 * </pre>
 * ① 的 15 是**二阶段「甲壳崩裂」过渡态**：`tick()` 里「state==0 且 Crackiness ≥ MEDIUM 且还没进二阶段」
 * 就会写 15（所以它出现的概率不低），而推进到二阶段的收尾写在 {@code aiStep} 里。
 * 破刹/时间定格会暂停 {@code LivingEntity.tick}（{@code aiStep} 在其内）⇒ 过渡永远走不完、
 * 状态永远停在 15 ⇒ 之后每一刀都在第一行被 {@code return false}，表现为彻底打不动。
 * 由于 `tick()` 仍在跑，**改状态没用**（下一 tick 又写回 15），只能在读取处动手。
 * ② 的沉睡态（state 1/2）本来就是出生保护，定身期间同样应该放开。
 * <p>
 * <b>解法</b>：定身期间只对 {@code hurt} 内部这两处读数做手脚——
 * ① 把 {@code getAttackState()} 读成 0（等价于「不在崩裂过渡态」）；
 * ② 把 {@code BYPASSES_INVULNERABILITY} 读成 true（短路沉睡保护）。
 * 状态机本身一个字节都不改，定身结束后过渡照常推进，不会打乱它的阶段流程。
 * 伤害仍走原版管线：`IAnimatedMiniBoss.hurt` 的 DamageCap / 抗性 / 伤害适应减伤照常生效。
 */
@Mixin(value = PosessedPaladinEntity.class, remap = false)
public abstract class PosessedPaladinStunHurtMixin {

    /** ② 沉睡态：{@code isSleep() && !bypass} —— 定身期间短路 bypass。 */
    @Redirect(
            method = "hurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z",
                    remap = false),
            require = 0
    )
    private boolean lensouls$bypassSleepGuard(DamageSource source, TagKey<DamageType> tag) {
        if (tag == DamageTypeTags.BYPASSES_INVULNERABILITY
                && StunPauseHelper.isStunPaused((Entity) (Object) this)) {
            return true;
        }
        return source.is(tag);
    }

    /**
     * ① 崩裂过渡态：{@code getAttackState() == 15} 时无条件 {@code return false}（没有 bypass 口子），
     * 所以只能在读取处把定身期间的读数压成 0。
     * <p>
     * <b>处理器签名必须带「接收者」参数</b>（这里是 {@code PosessedPaladinEntity}）：
     * {@code @Redirect} 的处理器参数表 = 〔调用接收者（实例调用才有）, 被调方法的全部参数〕，
     * 少写接收者会被 Mixin 判为签名非法（"Not enough arguments … expected argument type …"），
     * 整条注入直接报错。也因为接收者就是宿主本身，本方法还能顺带当 {@code this} 用。
     * <p>
     * 注意这里读的是**真实值**：重定向只作用于 {@code hurt} 里那一条 invoke，
     * 本方法内直接调 {@code getAttackState()} 不会再次被重定向，不存在递归。
     */
    @Redirect(
            method = "hurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/miauczel/legendary_monsters/entity/AnimatedMonster/Mobs/CollapsedKingdom/PosessedPaladinEntity;getAttackState()I",
                    remap = false),
            require = 0
    )
    private int lensouls$ignoreCrackStateWhenStunned(PosessedPaladinEntity self) {
        if (StunPauseHelper.isStunPaused(self)) {
            return 0;
        }
        return self.getAttackState();
    }
}
