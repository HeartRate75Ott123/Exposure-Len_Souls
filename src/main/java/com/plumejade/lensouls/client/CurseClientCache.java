package com.plumejade.lensouls.client;

import com.plumejade.lensouls.feather.CurseDef;
import com.plumejade.lensouls.feather.CurseDefs;

/**
 * 客户端侧的诅咒状态镜像（只读，由 {@code CurseSyncPacket} 写入）。
 * <p>
 * 权威数据在服务端的 {@code CurseStateData}；这里只服务物品 tooltip 的
 * 「已反转 N / M」与逐条进度显示，<b>不做任何逻辑判定</b>。
 */
public final class CurseClientCache {

    private static long mask;
    private static long[] progress = new long[CurseDefs.MASK_BITS];

    private CurseClientCache() {
    }

    public static void set(long newMask, long[] newProgress) {
        mask = newMask;
        progress = (newProgress != null && newProgress.length == CurseDefs.MASK_BITS)
                ? newProgress
                : new long[CurseDefs.MASK_BITS];
    }

    public static boolean isReversed(CurseDef def, int entry) {
        int index = def.maskOffset() + entry;
        if (index < 0 || index >= CurseDefs.MASK_BITS) return false;
        return (mask & (1L << index)) != 0L;
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
}
