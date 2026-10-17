package com.plumejade.lensouls.client;

import com.plumejade.lensouls.feather.CurseDef;
import com.plumejade.lensouls.feather.CurseDefs;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 客户端侧的诅咒状态镜像（只读，由 {@code CurseSyncPacket} 写入）。
 * <p>
 * 权威数据在服务端的 {@code CurseStateData}；这里只服务物品 tooltip 的
 * 「已反转 N / M」与逐条进度显示，<b>不做任何逻辑判定</b>。
 * <p>
 * <b>{@code activeMask}（1.5.28 追加）</b>：一位一款诅咒，表示「该款此刻被装进 7 个槽位之一」。
 * 反转掩码已经占满 64 位（见 {@link CurseDefs#MASK_BITS}），没地方再塞「是否选中」，
 * 所以单独发一个 {@code int}。有了它客户端才能做 {@link #on} / {@link #rev} ——
 * 即 {@code CurseManager#on} / {@code CurseManager#rev} 的等价判断
 * （<b>不要</b>去读服务端附件：羽毛槽附件刻意不同步到客户端）。
 */
public final class CurseClientCache {

    private static long mask;
    private static long[] progress = new long[CurseDefs.MASK_BITS];
    private static int activeMask;

    private CurseClientCache() {
    }

    public static void set(long newMask, long[] newProgress, int newActiveMask) {
        mask = newMask;
        activeMask = newActiveMask;
        progress = (newProgress != null && newProgress.length == CurseDefs.MASK_BITS)
                ? newProgress
                : new long[CurseDefs.MASK_BITS];
    }

    /** 断线清空镜像（避免跨存档 / 跨服务器残留「还戴着」或「已反转」） */
    public static void clear() {
        mask = 0L;
        activeMask = 0;
        progress = new long[CurseDefs.MASK_BITS];
    }

    /** 断线时清空镜像 */
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static boolean isReversed(CurseDef def, int entry) {
        int index = def.maskOffset() + entry;
        if (index < 0 || index >= CurseDefs.MASK_BITS) return false;
        return (mask & (1L << index)) != 0L;
    }

    /** 该款是否被装进槽位（客户端镜像，位序同 {@link CurseDefs#ALL}） */
    public static boolean isActive(CurseDef def) {
        int which = indexOf(def);
        return which >= 0 && (activeMask & (1 << which)) != 0;
    }

    /** 该条的**诅咒态**是否生效 —— 与 {@code CurseManager#on} 同口径（选中 ∧ 未反转） */
    public static boolean on(CurseDef def, int entry) {
        return isActive(def) && !isReversed(def, entry);
    }

    /** 该条的**反转 / 解锁态**是否生效 —— 与 {@code CurseManager#rev} 同口径（选中 ∧ 已反转） */
    public static boolean rev(CurseDef def, int entry) {
        return isActive(def) && isReversed(def, entry);
    }

    public static long progress(int index) {
        return (index >= 0 && index < progress.length) ? progress[index] : 0L;
    }

    public static int reversedCount(CurseDef def) {
        int n = 0;
        for (int i = 0; i < def.entries(); i++) {
            // 没有反转条件的条目（如 ④ 的「虚无的承诺」）不计入，保证分子不会超过分母
            if (def.noReverse(i)) continue;
            if (isReversed(def, i)) n++;
        }
        return n;
    }

    /** 该款在 {@link CurseDefs#ALL} 里的下标（位序）；不是本模组的款返回 -1 */
    private static int indexOf(CurseDef def) {
        if (def == null) return -1;
        // List.of() 的 indexOf 对 null 会抛 NPE，这里已经先挡掉
        return CurseDefs.ALL.indexOf(def);
    }
}
