package com.plumejade.lensouls.handler.curse;

import net.minecraft.world.entity.LivingEntity;

/**
 * 「这次治疗是不是**生命恢复（Regeneration）效果**带来的」—— 供 ⑤ e8 的反转承诺使用。
 * <p>
 * ⑤ e8 止息 反转的文案是「生命自然恢复 ×2；<b>再生类效果正常生效</b>」。原版
 * {@code LivingHealEvent} 只给「谁、多少」，<b>不告诉你来源</b>，所以恢复量拦截
 * （e1 绝疗 −80%、e2 饥禁禁疗）会把再生效果的治疗也一起吃掉，看起来就是「反转了还被压」。
 * <p>
 * 这里由 {@code mixin/RegenerationHealMarkMixin} 在 {@code RegenerationMobEffect#applyEffectTick}
 * 调 {@code LivingEntity.heal} 的那一处打标记：标记期间 ⑤ 自己的恢复量拦截全部放行。
 * 用 {@code ThreadLocal} 而不是字段，且包在 try/finally 里 —— 服务端多线程/异常都不会漏标记。
 * <p>
 * <b>作用域只有 ⑤ 自己</b>：⑥（竭泉/血偿）、④（食之无味）各自按自己的诅咒态生效，
 * 不做跨款覆盖（用户定案：每条只对自己负责）。
 */
public final class HealSource {

    /** 嵌套深度（1 = 当前正在结算再生效果带来的治疗） */
    private static final ThreadLocal<int[]> REGEN_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private HealSource() {
    }

    /** 当前这次 {@code LivingHealEvent} 是否来自生命恢复效果 */
    public static boolean isRegenerationHeal() {
        return REGEN_DEPTH.get()[0] > 0;
    }

    /** 仅由 mixin 调用：把这次 heal 标记为「来自生命恢复效果」（异常安全） */
    public static void healAsRegeneration(LivingEntity entity, float amount) {
        int[] depth = REGEN_DEPTH.get();
        depth[0]++;
        try {
            entity.heal(amount);
        } finally {
            depth[0]--;
        }
    }
}
