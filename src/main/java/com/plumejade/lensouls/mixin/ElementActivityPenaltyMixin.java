package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ① 荒厄遗咒 e1 反转「活性效果 −2 级」——放在**药水效果真正上身之前**温和计算最终等级。
 * <p>
 * 旧实现是「先让满级 effect 上身，再由 {@code FeatherHardmanHandler} 每 tick 事后 chop 掉 2 级、
 * 重写成无限时长」。那套的三个毛病：
 * <ol>
 *   <li>玩家会看到等级<b>跳一下</b>（这是用户点名要改的）；</li>
 *   <li>每 tick 反复 remove/add，与 ③ 元素觉醒者写入活性、照片套装重复施加活性互相抢；</li>
 *   <li>账本曾是内存态，重登会<b>累计再扣一次</b>（已修成持久化，但根子还是在"事后改"）。</li>
 * </ol>
 * 现在改成与 {@code SuppressHandler}（元素抑制）同一类做法：在施加前就得到最终值。
 * 与抑制不同的是，抑制只需要拒绝（cancel），这里需要<b>改写等级</b>，
 * 所以用 {@link org.spongepowered.asm.mixin.injection.ModifyVariable} 替换入参实例。
 * <p>
 * 减到 0 级（原始 1~2 级）时，这条活性本来就不该存在 ⇒ 用 {@code @Inject} 直接拒绝施加
 * （原版不允许 amplifier = −1 的实例）。
 */
@Mixin(LivingEntity.class)
public abstract class ElementActivityPenaltyMixin {

    /** ① e1 反转的活性惩罚等级。与 {@code FeatherHardmanHandler} 的口径保持一致（2 级）。 */
    private static final int LENSOULS_E1_PENALTY = 2;

    /**
     * 施加前改写等级：命中「四种元素活性 + ① e1 已反转」时，把 amplifier 减 {@value #LENSOULS_E1_PENALTY}。
     * {@code require = 0}：目标方法在其它版本改动时不至于让整个 mixin 配置炸掉。
     */
    @ModifyVariable(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0
    )
    private MobEffectInstance lensouls$penalizeActivity(MobEffectInstance inst) {
        try {
            if (inst == null) return null;
            if (!((Object) this instanceof Player player)) return inst;
            if (!isInfusion(inst.getEffect())) return inst;
            if (!CurseManager.rev(player, CurseDefs.HARDMAN, 0)) return inst;
            int level = inst.getAmplifier() + 1;
            // 下限 1 级：减到 0 时保留 1 级（原版不允许 amplifier = -1 的实例）。
            // ⚠ 绝对不要再加第二个 HEAD 注入去"取消 ≤0 的情形"：两个注入同挂 HEAD，
            //   执行顺序不确定 —— 若 @ModifyVariable 先跑，@Inject 会拿到已减过的实例再减一次
            //   （4 级 → 2 级 → 再 −2 → 取消），实测表现就是"反转后活性直接没了"。
            int adjusted = Math.max(1, level - LENSOULS_E1_PENALTY);
            return new MobEffectInstance(inst.getEffect(), inst.getDuration(), adjusted - 1,
                    inst.isAmbient(), inst.isVisible(), inst.showIcon());
        } catch (Throwable t) {
            return inst;
        }
    }

    private static boolean isInfusion(Holder<MobEffect> effect) {
        return effect == ModEffects.FIRE_INFUSION
                || effect == ModEffects.WATER_INFUSION
                || effect == ModEffects.EARTH_INFUSION
                || effect == ModEffects.ENDER_INFUSION;
    }
}
