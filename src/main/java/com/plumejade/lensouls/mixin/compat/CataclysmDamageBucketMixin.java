package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.damage.ElementBypassHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 灾变 BOSS 伤害限制绕过 Mixin。
 * <p>
 * 灾变 {@code hurt()} 里有<b>两道</b>限伤，都读 {@code DamageCap()}：
 * <ol>
 *   <li>{@code amount = Math.min(DamageCap(), amount)}（单次上限）</li>
 *   <li>{@code if (damageBucket + amount > DamageCap()) amount = DamageCap() - damageBucket;}</li>
 * </ol>
 * 第二段是纯算术、没有 {@code Math.min} 调用 ⇒ 只改第一段入参拦不住它
 * （实测伤害被钉在 {@code DamageCap()} 上，约 75）。
 * <p>
 * 因此改成 {@code @Redirect} 掉 {@code hurt} 内对 {@code DamageCap()} 的调用本身：
 * 一次顶掉两道闸。注入点是「方法调用」而不是「Math.min 的参数」或「返回注入」，
 * 在多目标 mixin 里最不容易被静默吞掉——{@code require = 0} 一旦对不上就是安静失效，
 * 这正是之前"装了但没生效"的原因。
 * <p>
 * ⚠ 两个目标类<b>各自声明</b> {@code DamageCap()}，描述符 owner 不同 ⇒ 必须各写一个
 * {@code @Redirect}，并全部 {@code require = 0}（对不上的一方安静跳过，不会连累另一方）。
 */
@Mixin(value = {
        com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.LLibrary_Boss_Monster.class,
        com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.IABoss_monster.class
}, remap = false)
public abstract class CataclysmDamageBucketMixin {

    @Shadow(remap = false)
    private float damageBucket;

    /** 每次受击前：清桶 + 计算是否需要绕过（定身 / 元素弱点武器） */
    @Inject(method = "hurt", at = @At("HEAD"), remap = false, require = 0)
    private void lensouls$resetBucket(DamageSource source, float amount,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        // ① 桶限制全移除：无条件清空
        this.damageBucket = 0;

        // ② 定身 / 元素弱点 → 置绕过标志
        LivingEntity self = (LivingEntity) (Object) this;
        ElementBypassHelper.evaluateAndShouldBypassBucket(source, self);
    }

    /** LLibrary_Boss_Monster 分支：顶掉 hurt 内对 DamageCap() 的调用 */
    @Redirect(
            method = "hurt",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/L_Ender/cataclysm/entity/AnimationMonster/BossMonsters/LLibrary_Boss_Monster;DamageCap()F"),
            remap = false,
            require = 0
    )
    private static float lensouls$redirectCapL(
            com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.LLibrary_Boss_Monster self) {
        return ElementBypassHelper.shouldBypassCap() ? Float.MAX_VALUE : self.DamageCap();
    }

    /** IABoss_monster 分支：同上 */
    @Redirect(
            method = "hurt",
            at = @At(value = "INVOKE",
                    target = "Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/IABossMonsters/IABoss_monster;DamageCap()F"),
            remap = false,
            require = 0
    )
    private static float lensouls$redirectCapI(
            com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.IABoss_monster self) {
        return ElementBypassHelper.shouldBypassCap() ? Float.MAX_VALUE : self.DamageCap();
    }

    /** hurt 结束清标志，避免战斗外的 DamageCap() 调用读到残留 */
    @Inject(method = "hurt", at = @At("RETURN"), remap = false, require = 0)
    private void lensouls$clearBypassFlag(DamageSource source, float amount,
                                          CallbackInfoReturnable<Boolean> cir) {
        ElementBypassHelper.clearBypass();
    }
}
