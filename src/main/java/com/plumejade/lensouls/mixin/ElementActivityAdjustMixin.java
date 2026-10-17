package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.handler.ElementActivityMath;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 元素活性等级的**施加前调整钩子**（唯一注入点）。
 * <p>
 * 在 {@code LivingEntity.addEffect(MobEffectInstance, Entity)} 真正落地之前：
 * <ol>
 *   <li>把「外部这一份来源等级 + 它的剩余时长」记进账本（{@link ElementActivityMath#recordSource}），</li>
 *   <li>等级改成 {@code 来源 + boost(③) − penalty(①)}，<b>时长原样保留</b>，
 *       并强制 {@code ambient = true}（原版信标样式的蓝框）。</li>
 * </ol>
 * 结果为 0（① 诅咒态 / 减到 0）时**原样放行**，由 {@code push} 或 {@code MobEffectEvent.Applicable}
 * 在下一 tick 处理 —— 表现为「不施加」。
 * <p>
 * 对其它效果一律原样返回 ⇒ 对其他模组完全透明；没戴任何羽毛时 boost = 0、penalty = 0 ⇒ 空操作。
 */
@Mixin(LivingEntity.class)
public abstract class ElementActivityAdjustMixin {

    @ModifyVariable(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0
    )
    private MobEffectInstance lensouls$adjustActivity(MobEffectInstance inst) {
        try {
            if (inst == null) return inst;
            if (ElementActivityMath.PUSHING.get()) return inst;      // 我方 push 写的已是最终值
            if (!((Object) this instanceof ServerPlayer sp)) return inst;   // 只在服务端算

            int idx = ElementActivityMath.indexOf(inst.getEffect());
            if (idx < 0) return inst;                               // 不是四种活性 ⇒ 原样放行

            int sourceLevel = inst.getAmplifier() + 1;
            int target = ElementActivityMath.resolve(sp, sourceLevel);

            // 先记账：不论这一份最终能不能落地，来源本身已经存在
            ElementActivityMath.recordSource(sp, idx, sourceLevel, inst.getDuration());

            // 真的改了等级才记（诊断开关打开时，连"没改"的那次也记，用来确认钩子确实看到了它）
            if (target != sourceLevel || ElementActivityMath.VERBOSE) {
                logAdjust(sp, idx, sourceLevel, target, inst.getDuration());
            }

            if (target == sourceLevel) return inst;
            if (target <= 0) return inst;                           // 交给 push / Applicable 表现为「不施加」

            // 只改等级；时长与外观（含 ambient）继承源 —— 这一份就是「改·源数据」
            return new MobEffectInstance(inst.getEffect(), inst.getDuration(), target - 1,
                    inst.isAmbient(), inst.isVisible(), inst.showIcon());
        } catch (Throwable t) {
            return inst;                                            // 绝不让异常打断别人的药水逻辑
        }
    }

    /** 同一玩家/元素/取值 5 秒内不重复（诊断开关打开时不节流） */
    private static final java.util.Map<String, Long> LAST_LOG = new java.util.HashMap<>();

    private static void logAdjust(ServerPlayer sp, int idx, int sourceLevel, int target, int duration) {
        try {
            long now = sp.level().getGameTime();
            String key = sp.getUUID() + "/" + idx + "/" + sourceLevel + ">" + target;
            Long prev = LAST_LOG.get(key);
            if (!ElementActivityMath.VERBOSE && prev != null && now - prev < 100L) return;
            if (LAST_LOG.size() > 512) LAST_LOG.clear();
            LAST_LOG.put(key, now);
            LenSouls.LOGGER.info("[ActAdjust] {} {} 来源={} (时长={}) → 可见={} (boost={} pen={} 禁={})",
                    sp.getName().getString(), ElementActivityMath.INFUSIONS[idx].value().getDescriptionId(),
                    sourceLevel, duration, target, ElementActivityMath.boost(sp),
                    ElementActivityMath.penalty(sp), ElementActivityMath.blocked(sp));
        } catch (Throwable ignored) {
        }
    }
}
