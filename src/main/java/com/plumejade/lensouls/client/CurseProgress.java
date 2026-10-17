package com.plumejade.lensouls.client;

import com.plumejade.lensouls.feather.CurseDef;

/**
 * 「诅咒段 + 祝福段」双段款的**按段计数**（纯显示用）。
 * <p>
 * ⑦ 羽·零号誓炉有 14 条：e1~e7 是**诅咒**（达成后「反转」）、e8~e14 是**祝福**（达成后「解锁」）。
 * 分界点由 {@link CurseDef#sectionAt()} 给出（= 7，即祝福段起点），段长由
 * {@link CurseDef#curseEntries()} / {@link CurseDef#blessingEntries()} 算，
 * <b>一律不写死 7 / 14</b>。
 * <p>
 * 读的是客户端镜像 {@link CurseClientCache}（服务端 {@code CurseStateData} 按 20 tick 心跳同步），
 * 所以本类只在渲染路径（tooltip / 列表行）里调用；服务端逻辑判定请继续用 {@code CurseManager} 的
 * {@code on} / {@code rev}。
 * <p>
 * 未分段款（{@code sectionAt() == 0}）**不要**调 {@link #reversedBefore} / {@link #reversedFrom}
 * ——它们是分段款的专用口径，未分段款请继续用 {@link CurseClientCache#reversedCount(CurseDef)}。
 */
public final class CurseProgress {

    private CurseProgress() {
    }

    /**
     * 统计 {@code [fromEntryInclusive, toEntryExclusive)} 区间里已反转 / 已解锁的条数。
     * <p>
     * 与 {@link CurseClientCache#reversedCount(CurseDef)} 同口径：**跳过分段内标「无反转」的条目**
     * （如 ④ 的「虚无的承诺」），保证分子不会超过分母。越界区间自动夹取，不抛异常。
     */
    public static int countReversed(CurseDef def, int fromEntryInclusive, int toEntryExclusive) {
        if (def == null) return 0;
        int from = Math.max(0, fromEntryInclusive);
        int to = Math.min(def.entries(), toEntryExclusive);
        int n = 0;
        for (int i = from; i < to; i++) {
            if (def.noReverse(i)) continue;
            if (CurseClientCache.isReversed(def, i)) n++;
        }
        return n;
    }

    /** {@code sectionAt()} **之前**（诅咒段）已反转条数 —— 仅对分段款有意义 */
    public static int reversedBefore(CurseDef def) {
        return def == null ? 0 : countReversed(def, 0, def.sectionAt());
    }

    /** {@code sectionAt()} **及之后**（祝福段）已解锁条数 —— 仅对分段款有意义 */
    public static int reversedFrom(CurseDef def) {
        return def == null ? 0 : countReversed(def, def.sectionAt(), def.entries());
    }
}
