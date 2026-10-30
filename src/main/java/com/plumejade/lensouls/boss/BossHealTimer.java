package com.plumejade.lensouls.boss;

/**
 * 浓雾的治愈（boss_heal）的「在场 tick 累计器」，挂在实体附件上
 * （{@link ModAttachments#BOSS_HEAL_TIMER}）。
 * <p>
 * <b>为什么是累计器，而不是时间戳 / {@code duration}</b>：
 * <ul>
 *   <li>按 {@code duration % 20} 判定（旧实现）：buff 每被刷新一次 duration 就被重设，
 *       新时长的每个 20 的倍数都会误触发回血 ⇒ 高频获得 buff = 高频回血；</li>
 *   <li>按「下一个可回血的绝对 tick 时间戳」判定（上一版）：稳态没问题，但 0 这个默认值
 *       在绝对值语义里没有位置，只能靠特判补救；不做特判就会「获得 buff 的第一 tick 立刻回血」，
 *       即「移除 → 重新获得」白送一跳。此外绝对时刻绑在<b>关卡时钟</b>上
 *       （{@code level().getGameTime()} 每个维度各自计数），换维度会让时间戳瞬间失效；</li>
 *   <li>本实现数的是「这个效果在这只实体身上待了几个 tick」：单位与原版再生一致（按 tick 计），
 *       相位却锚在实体而不是效果实例上 ⇒ 刷新、移除重获、换维度、换 level 都动不了节奏。</li>
 * </ul>
 * 语义约定：
 * <ul>
 *   <li><b>获得</b>：满 {@link #HEAL_INTERVAL_TICKS} 个在场 tick 才回第一跳（1 秒），
 *       中途怎么刷新都不影响——刷新只改 {@code MobEffectInstance.duration}，
 *       而 {@code duration} 只决定「buff 还在不在」，与节奏无关；</li>
 *   <li><b>刷新</b>：{@code MobEffectInstance.update()} 只改 duration/amplifier，
 *       既不触发 {@code MobEffectEvent.Remove}/{@code Expired}，也碰不到本类字段，
 *       故累计进度完整保留；</li>
 *   <li><b>真断档</b>：两次在场之间隔了超过 {@link #GAP_TOLERANCE} 个 tick，
 *       视为「buff 确实消失过」，累计归零、重新起算（不补偿断档期间的回血）。
 *       同一 tick 内先 remove 再 add（部分模组/脚本的刷新方式）间隔 0~1，按连续处理
 *       ⇒ 不会出现「被高频 remove+add 刷得一次都回不上」；</li>
 *   <li><b>满血</b>：回血这一跳照常消耗（与原版再生一致），不做「满血攒跳」，
 *       否则会多出一条「憋 19 tick、一掉血立刻回」的白送路径。</li>
 * </ul>
 * 状态不序列化（见 {@link ModAttachments#BOSS_HEAL_TIMER}）：区块卸载/重载后实体附件重建，
 * 等效于断档 ⇒ 重新等 1 秒，不给补偿，也不会产生暴血。
 */
public class BossHealTimer {

    /** 回血间隔：每 20 个「在场 tick」回一跳（每秒一次），与效果实例的 duration 无关 */
    public static final int HEAL_INTERVAL_TICKS = 20;

    /**
     * 断档容差：两次在场之间最多允许漏掉几个 tick 仍算连续。
     * <p>
     * 取 2 是因为「同一 tick 内 remove + add」最多表现为间隔 1；
     * 再大就会把「漏掉 2~3 tick 的瞬时重建」也当成连续，失去断档判定的意义。
     */
    private static final int GAP_TOLERANCE = 2;

    /** 已累计的「在场 tick」数；达到 {@link #HEAL_INTERVAL_TICKS} 时回血并归零 */
    private int ticks;

    /** 上一次观察到本效果在场的实体 tick（{@code Entity#tickCount}）；-1 = 从未在场 */
    private int lastSeenTick = -1;

    /**
     * 每 tick 结算一次：返回 {@code true} 表示「这一 tick 该回血」。
     * <p>
     * 只在 {@code BossHealEffect#applyEffectTick} 里调用，且每 tick 只调一次——
     * 速率上限（每 20 个在场 tick 一跳）由本方法的结构保证，不依赖任何调用方的刷新频率。
     *
     * @param entityTick 当前实体 tick（{@code LivingEntity#tickCount}）。
     *                   用它与上次值的差判定断档：它只随实体 tick 前进，
     *                   不受维度、关卡时钟、时间类功能影响。
     */
    public boolean tickAndShouldHeal(int entityTick) {
        if (this.lastSeenTick >= 0) {
            int gap = entityTick - this.lastSeenTick;
            // gap < 0：tickCount 回绕或实体被替换，同样按断档处理，宁可重算也不白送
            if (gap > GAP_TOLERANCE || gap < 0) {
                this.ticks = 0;
            }
        }
        this.lastSeenTick = entityTick;

        if (++this.ticks < HEAL_INTERVAL_TICKS) {
            return false;
        }
        this.ticks = 0;
        return true;
    }

    /** 还差几个在场 tick 回下一跳（0 = 本 tick 刚回完）。仅用于调试/日志。 */
    public int pendingTicks() {
        return HEAL_INTERVAL_TICKS - this.ticks;
    }
}
