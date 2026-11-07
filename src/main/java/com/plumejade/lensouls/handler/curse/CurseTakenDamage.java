package com.plumejade.lensouls.handler.curse;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.handler.FeatherAbyssHandler;
import com.plumejade.lensouls.handler.FeatherElementRiseHandler;
import com.plumejade.lensouls.handler.FeatherHardmanHandler;
import com.plumejade.lensouls.handler.FeatherTwitcherHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 受伤侧的<b>统一易伤乘区</b>（唯一收口点）。
 * <p>
 * <b>为什么要收口</b>：7 款羽毛原本各自在受伤链上乘自己的倍率，多款同戴时是「乘到一起」。
 * 实测反馈（一名玩家）：扭曲 100%（② e1）+ 满扭曲 100%（② e2）+ 生命 &gt;50% 的 50%（⑤ e5）
 * + 荒厄 35%（① e3）叠加后，30 点魔法伤害被放大到 200 以上。这正是 {@code Π(1 + xi)} 的爆炸性。
 * <p>
 * <b>现行口径</b>：所有诅咒的「受到伤害 +X%」全部并入<b>同一个加算池</b>，一条伤害链上只乘一次：
 * <pre>
 *   d' = d × (1 + Σ 易伤) × Π 减伤
 * </pre>
 * 例：上面那套 = 30 × (1 + 1.0 + 1.0 + 0.5 + 0.35) = 30 × 3.85 = 115.5（旧算法是 30 × 8.1 = 243）。
 * <p>
 * <b>为什么减伤仍然连乘、不并入 Σ</b>：易伤侧加算只会「叠加变缓」；减伤侧若也加算，
 * −70%（⑤ e13 反转）+ −25%（⑤ 大成）+ −13%（④ e3 反转）会累到 ≤ −100% ⇒ 直接无敌。
 * 这是设计文档 §2 明确禁止的结果，所以减伤侧保持各自乘算（乘算永远到不了 0）。
 * <p>
 * <b>分工</b>：每款羽毛只负责「报数」——{@code collectTaken(...)} 往 {@link Pool} 里丢自己的加数，
 * 真正的乘只发生在 {@link #onDamagePre}。判定点选 {@link LivingDamageEvent.Pre} 的
 * {@link EventPriority#LOW}：
 * <ul>
 *   <li>晚于护甲/附魔/抗性结算与所有默认优先级的受伤修正（元素附加等已经算完）；</li>
 *   <li>早于 {@link EventPriority#LOWEST} 的法师胸针 −50%，保持「攻击方乘区最后收」的既有顺序；</li>
 *   <li>早于 {@code LivingDamageEvent.Post} 的真实扣血统计（{@link DamageIntake}）。</li>
 * </ul>
 * <p>
 * ④ e12 恶意（元素附加伤害 +12% → −12%）需要从最终伤害里反推出「元素附加伤害」本身，
 * 反推要除以<b>本次受伤的诅咒总倍率</b>，所以这里算完立刻回调
 * {@link FeatherAbyssHandler#correctElementBonusAfterTaken}。
 */
public final class CurseTakenDamage {

    /** 浮点比较容差：倍率与 1.0 差不到这个量级就不改数值（避免无意义的 setNewDamage）。 */
    private static final float EPS = 1.0e-4f;

    private CurseTakenDamage() {
    }

    /**
     * 一次受伤事件的加算池。
     * <p>
     * 池子只活在一次 {@link LivingDamageEvent.Pre} 里（由 {@link #onDamagePre} 现造现用），
     * 不落任何静态状态 —— 因此不存在「上一击的加数漏到下一击」的串档问题。
     */
    public static final class Pool {

        private final DamageSource source;
        /** Σ 易伤（加算） */
        private float vulnerability;
        /** Π 减伤（连乘，初值 1） */
        private float reduction = 1.0f;

        Pool(DamageSource source) {
            this.source = source;
        }

        /** 本次受伤的伤害来源（① e3 的「摔落伤害 +500%」这类条件型条目要用）。 */
        public DamageSource source() {
            return this.source;
        }

        /** 往易伤加算池里加一个百分比：{@code add(0.35f)} = 受到伤害 +35%。 */
        public void add(float fraction) {
            if (fraction != 0.0f && Float.isFinite(fraction)) {
                this.vulnerability += fraction;
            }
        }

        /** 往减伤连乘链上乘一个系数：{@code multiply(0.75f)} = 受到伤害 -25%。 */
        public void multiply(float factor) {
            if (Float.isFinite(factor)) {
                this.reduction *= Math.max(0.0f, factor);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            float d = event.getNewDamage();
            if (!Float.isFinite(d) || d <= 0.0f) return;

            Pool pool = new Pool(event.getSource());
            TimecoreCurse.collectTaken(player, pool);
            FinalriteCurse.collectTaken(player, pool);
            FeatherTwitcherHandler.collectTaken(player, pool);
            FeatherHardmanHandler.collectTaken(player, pool);
            FeatherElementRiseHandler.collectTaken(player, pool);
            FeatherAbyssHandler.collectTaken(player, pool);

            // 易伤加算池至少是 1（未装诅咒 / 全是减伤时就是 1 + 0）
            float factor = Math.max(0.0f, 1.0f + pool.vulnerability) * pool.reduction;
            if (!Float.isFinite(factor) || factor < 0.0f) factor = 0.0f;

            if (Math.abs(factor - 1.0f) > EPS) {
                event.setNewDamage(Math.max(0.0f, d * factor));
            }

            AttackDiag.log("受伤乘区 易伤Σ={} 减伤={} → 总倍率={} dmg {}→{}",
                    pool.vulnerability, pool.reduction, factor, d, event.getNewDamage());

            // ④ e12 恶意 反转：反推「元素附加伤害」要用本次受伤的诅咒总倍率
            FeatherAbyssHandler.correctElementBonusAfterTaken(event, player, factor);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] 受伤乘区结算异常", t);
        }
    }
}
