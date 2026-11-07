package com.plumejade.lensouls.handler.curse;

import net.minecraft.world.entity.LivingEntity;

/**
 * 「这次治疗是从哪来的」—— 给 ⑤ 的<b>恢复量拦截</b>放行两类来源。
 * <p>
 * 原版 {@link net.neoforged.neoforge.event.entity.living.LivingHealEvent} 只给「谁、多少」，
 * <b>不告诉你来源</b>，而 ⑤ 有两条文案承诺了「这种治疗照常生效」：
 * <ul>
 *   <li><b>再生类效果</b>：⑤ e8 止息 反转的文案是「生命自然恢复 ×2；<b>再生类效果正常生效</b>」。
 *       由 {@code mixin/RegenerationHealMarkMixin} 在 {@code RegenerationMobEffect#applyEffectTick}
 *       调 {@code LivingEntity.heal} 的那一处打标记。</li>
 *   <li><b>次元瓶</b>（{@code item/HealPotionItem}）：用户定案「让次元瓶的回血正常，赦免它，
 *       无需在文案特别说明」—— 由 {@link #healAsDimensionalBottle} 打标记。</li>
 * </ul>
 * 两个标记都用 {@code ThreadLocal} 而不是字段，且包在 try/finally 里 —— 服务端多线程/异常都不会漏标记。
 * <p>
 * <b>作用域只有 ⑤ 自己的恢复量拦截</b>（e1 绝疗 −80% / e2 饥禁 / e8 止息）；⑥（竭泉/血偿）、
 * ④（食之无味）各自按自己的诅咒态生效，不做跨款覆盖（用户定案：每条只对自己负责）。
 */
public final class HealSource {

    /** 嵌套深度（1 = 当前正在结算再生效果带来的治疗） */
    private static final ThreadLocal<int[]> REGEN_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    /** 嵌套深度（1 = 当前正在结算次元瓶带来的治疗） */
    private static final ThreadLocal<int[]> BOTTLE_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private HealSource() {
    }

    /** 当前这次 {@code LivingHealEvent} 是否来自生命恢复效果 */
    public static boolean isRegenerationHeal() {
        return REGEN_DEPTH.get()[0] > 0;
    }

    /** 当前这次 {@code LivingHealEvent} 是否来自次元瓶（HealPotionItem） */
    public static boolean isDimensionalBottleHeal() {
        return BOTTLE_DEPTH.get()[0] > 0;
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

    /**
     * 仅由 {@code HealPotionItem} 调用：把这次 heal 标记为「来自次元瓶」（异常安全）。
     * <p>
     * 次元瓶自己的实现是 {@code player.heal(player.getMaxHealth())}，必须走这里而不是直接调 heal，
     * 否则 ⑤ e2 饥禁 / e8 止息 会把它清零、e1 绝疗会把它砍到 20%。
     */
    public static void healAsDimensionalBottle(LivingEntity entity, float amount) {
        int[] depth = BOTTLE_DEPTH.get();
        depth[0]++;
        try {
            entity.heal(amount);
        } finally {
            depth[0]--;
        }
    }
}
