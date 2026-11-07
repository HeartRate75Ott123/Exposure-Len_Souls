package com.plumejade.lensouls.feather;

import com.plumejade.lensouls.handler.WorldDays;

/**
 * 「限天数类」反转条件的**窗口值**与**逾期判定**（目前两条，都在 ④ 折翼沉渊）：
 * <ul>
 *   <li><b>e3 安魂曲</b>：固定 {@link WorldDays#ABYSS_REVERSE_WINDOW_DAYS} 天；</li>
 *   <li><b>e10 失忆</b>：<b>动态</b> —— 佩戴 1 款诅咒给 {@link #AMNESIA_BASE_DAYS} 天，
 *       每多戴 1 款 +{@link #AMNESIA_PER_EXTRA_CURSE_DAYS} 天，满 7 款 = {@value #AMNESIA_MAX_DAYS} 天。</li>
 * </ul>
 *
 * <h2>为什么窗口必须每次现算</h2>
 * 诅咒羽毛在<b>创造模式可以随时卸下/再装上</b>，佩戴款数一变，窗口就变：
 * 例如已过 41 天、当前只戴 1 款（窗口 30）⇒ 已逾期；再戴上 1 款（窗口 40）仍然逾期；
 * 戴到 5 款（窗口 70）⇒ 又回到「还在窗口内」。所以逾期<b>不是一个会被记住的状态</b>，
 * 而是「当前天数 vs 当前窗口」的即时比较 —— 存任何标记都会在卸下/装上后说错话。
 *
 * <h2>两侧共用同一份算术</h2>
 * 服务端判定（{@code FeatherAbyssHandler.onMobKilled}）与客户端 tooltip 必须给出同一个数，
 * 所以窗口只在这里算一次：服务端传 {@code Integer.bitCount(CurseManager.activeMask(player))}，
 * 客户端传 {@code CurseClientCache.activeCurseCount()}（同为「戴了几款羽毛」的位计数）。
 * <p>
 * 本类刻意<b>不引用</b>客户端缓存类，避免服务端加载 client-only 类。
 */
public final class CurseDayLimit {

    /** e10 失忆：佩戴 1 款诅咒时的窗口（天）——数值唯一出处仍在 {@link WorldDays} */
    public static final int AMNESIA_BASE_DAYS = WorldDays.ABYSS_AMNESIA_WINDOW_DAYS;

    /** e10 失忆：每多佩戴 1 款诅咒，窗口 +（天） */
    public static final int AMNESIA_PER_EXTRA_CURSE_DAYS = 10;

    /** e10 失忆：参与加成的最大款数（= 羽毛槽位数 7，满佩戴窗口 90 天） */
    public static final int AMNESIA_MAX_CURSES = FeatherSlotData.SLOTS;

    /** 满佩戴时的窗口（天）= {@value #AMNESIA_BASE_DAYS} + 10 × 6 = 90 */
    public static final int AMNESIA_MAX_DAYS =
            AMNESIA_BASE_DAYS + AMNESIA_PER_EXTRA_CURSE_DAYS * (AMNESIA_MAX_CURSES - 1);

    /** ④ e3 安魂曲（0 基条目号） */
    private static final int ABYSS_REQUIEM = 2;
    /** ④ e10 失忆（0 基条目号） */
    private static final int ABYSS_AMNESIA = 9;

    private CurseDayLimit() {
    }

    /**
     * 该条是否「x 天内」类条件（决定 tooltip 要不要把窗口天数画进条件、要不要做逾期提示）。
     * <p>
     * 只有这两条的窗口与「自己的已过天数」比较，其余条目的进度都是累计量。
     */
    public static boolean isDayWindow(CurseDef def, int entry) {
        return CurseDefs.ABYSS.equals(def) && (entry == ABYSS_REQUIEM || entry == ABYSS_AMNESIA);
    }

    /**
     * 该条当前的窗口天数（天）；非限天数类返回 {@code 0}。
     *
     * @param equippedCurses 当前佩戴的诅咒款数（0~7；0 视为 1 款，因为条件讨论的是「戴上之后」）
     */
    public static int windowDays(CurseDef def, int entry, int equippedCurses) {
        if (!isDayWindow(def, entry)) return 0;
        if (entry == ABYSS_REQUIEM) return WorldDays.ABYSS_REVERSE_WINDOW_DAYS;
        return amnesiaWindowDays(equippedCurses);
    }

    /** e10 失忆的动态窗口：30 天起，每多 1 款 +10 天，最多 90 天（款数会夹到 1~7） */
    public static int amnesiaWindowDays(int equippedCurses) {
        int n = Math.max(1, Math.min(AMNESIA_MAX_CURSES, equippedCurses));
        return AMNESIA_BASE_DAYS + AMNESIA_PER_EXTRA_CURSE_DAYS * (n - 1);
    }

    /**
     * 该条此刻是否已逾期（= 已达成的天数 ≥ 当前窗口）。
     * <p>
     * 反转条件要求的是「天数 &lt; 窗口」，所以到达窗口当天就算逾期
     * （与判定处 {@code FeatherAbyssHandler.checkAmnesia} 的 {@code days < window} 同一口径）。
     *
     * @param elapsedDays 该玩家自己的已过天数（{@link WorldDays#elapsedDays}）
     */
    public static boolean isExpired(CurseDef def, int entry, int equippedCurses, int elapsedDays) {
        int window = windowDays(def, entry, equippedCurses);
        return window > 0 && elapsedDays >= window;
    }
}
