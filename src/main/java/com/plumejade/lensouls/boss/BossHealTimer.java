package com.plumejade.lensouls.boss;

/**
 * 浓雾的治愈（boss_heal）的「下一次可回血时刻」时钟，挂在实体附件上
 * （{@link ModAttachments#BOSS_HEAL_TIMER}）。
 * <p>
 * 语义与 {@code MobEffectInstance.duration} 无关：buff 每次被重新施加都会重设 duration，
 * 旧实现用 {@code duration % 20 == 0} 判定回血，导致「每次获得 buff 都立刻回一次血」，
 * 高频攻击下回血速度远超每秒一次。改为绝对 tick 时间戳后：
 * <ul>
 *   <li>获得 buff：首次生效 tick 写入 {@code now + 20}，满 1 秒才第一次回血；</li>
 *   <li>刷新 buff：时间戳不动，回血节奏不受任何影响（恒为每秒一次）；</li>
 *   <li>移除 buff：{@link #reset()} 清空，重新获得时从当下重新起算。</li>
 * </ul>
 * 计时挂在实体附件而非效果实例上，故效果实例被刷新/替换都不会丢计时。
 */
public class BossHealTimer {

    /** 下一次允许回血的 tick（实体所在维度的绝对游戏时间）；0 表示尚未起算 */
    public long nextHealTick;

    /**
     * 效果移除时清空时钟（见 {@link com.plumejade.lensouls.handler.BossHealEffectHandler}）。
     * <p>
     * 若不清空，残留的过期时间戳会让「移除后立刻重新获得」在下一 tick 就白送一次回血；
     * 清空后新一次获得重新计时，稳定为「满 1 秒回第一次」。
     */
    public void reset() {
        this.nextHealTick = 0L;
    }
}
