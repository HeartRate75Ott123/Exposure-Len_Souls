package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.handler.ElementActivityMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 元素活性等级的**施加前调整钩子**（两条落桌入口 + 一条强制禁用）。
 *
 * <pre>
 *   LivingEntity.addEffect(inst, entity)      ← 照片 / 药水 / 命令 / 大多数模组
 *   LivingEntity.forceAddEffect(inst, entity) ← 绕过免疫与 addEffect 的"强制施加"（药水发放器一类）
 * </pre>
 *
 * 两条都在**落桌之前**换成修整版（只改等级，时长与外观继承来源），所以高频来源也不会先闪一 tick：
 * <ul>
 *   <li>{@code addEffect}：结果为 ≤0 时原样交回，由 {@code MobEffectEvent.Applicable} 拒绝；</li>
 *   <li>{@code forceAddEffect}：它<b>不经过</b> Applicable，所以这里自己 cancel（严格禁用）。</li>
 * </ul>
 * 自己写的（{@link ElementActivityMath#PUSHING}）一律放行。
 */
@Mixin(LivingEntity.class)
public abstract class ElementActivityAdjustMixin {

    // ==================== 落桌前的等级修整 ====================

    @ModifyVariable(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0
    )
    private MobEffectInstance lensouls$adjustActivity(MobEffectInstance inst) {
        MobEffectInstance adjusted = lensouls$prepare(inst);
        return adjusted == null ? inst : adjusted;   // null = 该拒绝，交给 Applicable
    }

    @ModifyVariable(
            method = "forceAddEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0
    )
    private MobEffectInstance lensouls$adjustForced(MobEffectInstance inst) {
        MobEffectInstance adjusted = lensouls$prepare(inst);
        return adjusted == null ? inst : adjusted;
    }

    /**
     * 「算出来 ≤ 0」时**在落地前直接拒绝**。
     * <p>
     * 以前这里靠 {@code MobEffectEvent.Applicable} 拒绝，但那个事件只在 ① 诅咒态才拒；
     * 「源 + boost + 套装 − pen 减到 0」是算术结果，没人拦 ⇒ 原始那份先落地、被渲染一帧，
     * 下一 tick 才被 push 摘掉（实测的"闪一下"就是这个）。
     * <p>
     * 所以两条入口都自己 cancel：表现为「从未施加」。
     */
    @Inject(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void lensouls$rejectAdd(MobEffectInstance inst, Entity applier,
                                    org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (lensouls$mustReject(inst)) cir.setReturnValue(false);
    }

    @Inject(
            method = "forceAddEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void lensouls$rejectForced(MobEffectInstance inst, Entity applier, CallbackInfo ci) {
        if (lensouls$mustReject(inst)) ci.cancel();
    }

    /** 当前可见等级（0 = 身上没有） */
    private static int visibleLevel(ServerPlayer sp, int idx) {
        MobEffectInstance cur = sp.getEffect(ElementActivityMath.INFUSIONS[idx]);
        return cur == null ? 0 : cur.getAmplifier() + 1;
    }

    /** 该不该拒绝落地（只对四种活性生效；顺便把来源记账，解禁后能按它恢复） */
    private boolean lensouls$mustReject(MobEffectInstance inst) {
        try {
            if (inst == null) return false;
            if (ElementActivityMath.PUSHING.get()) return false;
            if (!((Object) this instanceof ServerPlayer sp)) return false;
            int idx = ElementActivityMath.indexOf(inst.getEffect());
            if (idx < 0) return false;
            int sourceLevel = inst.getAmplifier() + 1;
            // 等级高者胜：已有更高等级的活跃源 ⇒ 这一份不落地、也不记账（否则会挤掉高等级那份）
            // 例外：等级等于当前可见等级的**回声**（别的处理器拿我们修整后的实例再施加）必须放行。
            int active = ElementActivityMath.activeSourceLevel(sp, idx);
            if (active > sourceLevel && sourceLevel != visibleLevel(sp, idx)) return true;
            if (active > sourceLevel) return false;                  // 回声：放行但不记账
            ElementActivityMath.recordSource(sp, idx, sourceLevel, inst.getDuration());
            return ElementActivityMath.resolve(sp, idx, sourceLevel) <= 0;
        } catch (Throwable t) {
            return false;                                           // 绝不让异常打断别人的药水逻辑
        }
    }

    // ==================== 共用实现 ====================

    /**
     * @return 原实例 = 原样放行 / 新实例 = 修整版 / {@code null} = 拒绝落地
     */
    private MobEffectInstance lensouls$prepare(MobEffectInstance inst) {
        try {
            if (inst == null) return inst;
            if (ElementActivityMath.PUSHING.get()) return inst;      // 我方写入，已是最终值
            if (!((Object) this instanceof ServerPlayer sp)) return inst;   // 只在服务端算

            int idx = ElementActivityMath.indexOf(inst.getEffect());
            if (idx < 0) return inst;                               // 不是四种活性 ⇒ 原样放行

            int sourceLevel = inst.getAmplifier() + 1;

            // 等级高者胜：同元素已有更高等级的活跃源 ⇒ 这一份既不落地也不记账
            int active = ElementActivityMath.activeSourceLevel(sp, idx);
            if (active > sourceLevel) {
                // 例外：这是**回声**——某个处理器把"我们修整后的实例"又施加了一遍
                // （典型：女巫照片的"效果减半"会拿当前实例再 addEffect）。
                // 此时等级恰好等于可见等级，必须放行（否则外层那次也会被它 DO_NOT_APPLY，什么都不落下），
                // 但**不记账**，账本只认原始来源。
                if (sourceLevel == visibleLevel(sp, idx)) {
                    logAdjust(sp, idx, sourceLevel, sourceLevel, inst.getDuration(), 200L, "回声放行");
                    return inst;
                }
                logAdjust(sp, idx, sourceLevel, active, inst.getDuration(), 100L, "忽略(更低等级)");
                return null;
            }

            int target = ElementActivityMath.resolve(sp, idx, sourceLevel);

            // 先记账：不论这一份最终能不能落地，来源本身已经存在
            ElementActivityMath.recordSource(sp, idx, sourceLevel, inst.getDuration());

            if (target <= 0) {
                logAdjust(sp, idx, sourceLevel, target, inst.getDuration(), 100L, "拒绝落地");
                return null;                                        // 拒绝落地（表现为「不施加」）
            }

            // ★ 不改写实例，原样放行。
            //  实测：返回"改写后的新实例"时这一份会落不了地（只有 pen>0 才走这条路，
            //  也就是戴上荒厄之后才复现），而原样放行一定落地。
            //  等级统一交给 push —— 它在同一个 tick 的 Post 阶段跑，remove→add 一次完成，
            //  客户端只会收到最终快照（这就是早先要的"施加与移动同步，不周期性延迟"）。
            logAdjust(sp, idx, sourceLevel, target, inst.getDuration(), 400L,
                    target == sourceLevel ? "无需修整" : "放行·待push修整");
            return inst;
        } catch (Throwable t) {
            return inst;                                            // 绝不让异常打断别人的药水逻辑
        }
    }

    /** 同一玩家/元素/取值 在节流窗口内不重复（诊断开关打开时不节流） */
    private static final java.util.Map<String, Long> LAST_LOG = new java.util.HashMap<>();

    private static void logAdjust(ServerPlayer sp, int idx, int sourceLevel, int target, int duration,
                                  long throttle, String outcome) {
        try {
            long now = sp.level().getGameTime();
            String key = sp.getUUID() + "/" + idx + "/" + sourceLevel + ">" + target + outcome;
            Long prev = LAST_LOG.get(key);
            if (!ElementActivityMath.VERBOSE && prev != null && now - prev < throttle) return;
            if (LAST_LOG.size() > 512) LAST_LOG.clear();
            LAST_LOG.put(key, now);
            LenSouls.LOGGER.info("[ActAdjust] {} {} 来源={} (时长={}) → {}={} (boost={} pen={} 禁={})",
                    sp.getName().getString(), ElementActivityMath.INFUSIONS[idx].value().getDescriptionId(),
                    sourceLevel, duration, outcome, target,
                    ElementActivityMath.boost(sp), ElementActivityMath.penalty(sp),
                    ElementActivityMath.blocked(sp));
        } catch (Throwable ignored) {
        }
    }
}
