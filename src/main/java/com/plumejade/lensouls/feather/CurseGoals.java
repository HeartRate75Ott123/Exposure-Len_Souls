package com.plumejade.lensouls.feather;

import net.minecraft.network.chat.Component;

/**
 * 诅咒「反转条件进度」的<b>目标值 + 单位</b>表。
 * <p>
 * <b>为什么要有这个类</b>：目标值原先只以字面量的形式散落在各 handler 的
 * {@link CurseManager#tick(net.minecraft.server.level.ServerPlayer, CurseDef, int, long, long)}
 * 实参里（服务端才读得到），而 tooltip 是在<b>客户端</b>渲染的、只拿得到
 * {@code CurseClientCache} 里同步过来的「当前进度」。所以要有一张纯静态、无注册表访问的表，
 * 让客户端也能把「当前 / 目标 + 单位」画出来。
 * <p>
 * <b>维护纪律（硬性）</b>：本表的目标值必须与各 handler 的 {@code tick(...)} 实参（或
 * {@code setProgress(...)} 配套的目标常量）<b>逐条一致</b>——抄错等于给玩家显示假数据。
 * 改条件数值时两处同改（下方每条注释都标了出处文件:行号）。
 * <p>
 * <b>单位分类</b>：挖方块 / 治疗·伤害·经验点数 / 击杀 / 次数 / 种类 / 移动格数 / 天数 / 时间（tick）。
 * 没有数值目标的条目（条件由 {@code reach(...)} 直判、进度恒为 0）返回 {@code goal = 0}，
 * tooltip 侧不显示方括号，{@link #format} 则退化为「只显示当前值」。
 */
public final class CurseGoals {

    private CurseGoals() {
    }

    /** 单位 lang 键前缀 */
    private static final String PREFIX = "gui.lensouls.curse.prog.";

    /** 通用：{@code %s / %s}（未指定单位时的兜底） */
    public static final String GENERIC = PREFIX + "generic";
    /** 挖方块：{@code %s 格 / %s 格} */
    public static final String BLOCKS = PREFIX + "blocks";
    /** 治疗 / 伤害 / 经验：{@code %s 点 / %s 点} */
    public static final String POINTS = PREFIX + "points";
    /** 击杀：{@code %s 只 / %s 只} */
    public static final String KILLS = PREFIX + "kills";
    /** 受击 / 丢失 / 丢弃 / 雪球 / 触发：{@code %s 次 / %s 次} */
    public static final String TIMES = PREFIX + "times";
    /** 食物 / 生物 / 饰品 / 镜魂 / 图鉴：{@code %s 种 / %s 种} */
    public static final String KINDS = PREFIX + "kinds";
    /** 移动 / 走地：{@code %s 格 / %s 格} */
    public static final String CELLS = PREFIX + "cells";
    /** 天数：{@code %s 天 / %s 天} */
    public static final String DAYS = PREFIX + "days";
    /** 时间类（进度单位是 tick）：数值先化成 {@code Xm Ys}，模板为 {@code %s / %s} */
    public static final String TIME = PREFIX + "time";
    /** 无目标：{@code %s}（只有当前值） */
    public static final String NONE = PREFIX + "none";

    /** 逐条目标值（下标 = {@code def.maskOffset() + entry}） */
    private static final long[] GOALS = new long[CurseDefs.MASK_BITS];
    /** 逐条单位 lang 键（下标同上） */
    private static final String[] UNITS = new String[CurseDefs.MASK_BITS];

    static {
        // 默认：没有登记目标值的条目 = 无目标（条件由 reach 直判，进度镜像恒为 0）
        java.util.Arrays.fill(UNITS, NONE);

        // ==================== ① 荒厄遗咒（偏移 0，3 条） ====================
        // 三条共用同一条件「空余照片栏位 ≥5 时击杀 4 只不同 BOSS」：
        //   FeatherHardmanHandler:65  DISTINCT_BOSS_GOAL = 4
        //   FeatherHardmanHandler:390-391  逐条 tick(player, D, i, 1L, DISTINCT_BOSS_GOAL)
        for (int i = 0; i < CurseDefs.HARDMAN.entries(); i++) {
            put(CurseDefs.HARDMAN, i, 4L, KILLS);
        }

        // ==================== ② 扭曲之人（偏移 3，3 条） ====================
        // 三条共用「扭曲值 100、护甲 0 时击杀 6 万血以上生物」：
        //   FeatherTwitcherHandler:459-460  逐条 tick(player, D, i, 1L, 1L)
        for (int i = 0; i < CurseDefs.TWITCHER.entries(); i++) {
            put(CurseDefs.TWITCHER, i, 1L, KILLS);
        }

        // ==================== ③ 元素觉醒者（偏移 6，2 条） ====================
        // 两条共用「活性 ≥10、生命 ≥90% 时造成一次 10000 点元素附加伤害」：
        //   FeatherElementRiseHandler:305-306  逐条 tick(player, D, i, 1L, 1L)
        for (int i = 0; i < CurseDefs.ELEMENTRISE.entries(); i++) {
            put(CurseDefs.ELEMENTRISE, i, 1L, TIMES);
        }

        // ==================== ④ 折翼沉渊（偏移 8，12 条） ====================
        // e1「虚无的承诺」无反转条件（CurseDefs.ABYSS noReverse）→ 留空。
        // e2 神秘雪球：FeatherAbyssHandler:795  tick(..., E2_SNOWBALL, 1L, E2_HIT_GOAL[191]=100)
        put(CurseDefs.ABYSS, 1, 100L, TIMES);
        // e3 安魂曲 / e4 愚钝 / e5 自闭：条件由 reach（:356 / :764 / :866）直判，无进度计数 → 留空。
        // e6 厄运：FeatherAbyssHandler:893  tick(..., E6_DOOM, 1L, E6_BLOCK_GOAL[203]=200)
        put(CurseDefs.ABYSS, 5, 200L, BLOCKS);
        // e7 祸之可能性：FeatherAbyssHandler:1261  tick(..., 1L, E7_SUMMON_KILL_GOAL[205]=100)
        put(CurseDefs.ABYSS, 6, 100L, KILLS);
        // e8 挫败 / e10 失忆 / e11 疯狂 / e12 恶意：reach（:1267 / :1274 / :1281 / markPendingReverse :689）直判 → 留空。
        // e9 食之无味：FeatherAbyssHandler:812 setProgress(n) + :814 reach，目标 E9_FOOD_KIND_GOAL[209]=50
        put(CurseDefs.ABYSS, 8, 50L, KINDS);

        // ==================== ⑤ 断时炉心（偏移 20，23 条） ====================
        // 全部出自 handler/curse/TimecoreCurse.java，括号内为 tick 行号。
        put(CurseDefs.TIMECORE, 0, 2000L, POINTS);   // e1 绝疗   :253 累计治疗 2000 点
        put(CurseDefs.TIMECORE, 1, 12000L, TIME);    // e2 饥禁   :114 每 tick +1 → 12000 tick = 10 分钟
        put(CurseDefs.TIMECORE, 2, 1000L, BLOCKS);   // e3 迟凿   :293 挖 1000 个方块
        put(CurseDefs.TIMECORE, 3, 150L, TIMES);     // e4 徒劳   :302 被吞掉落 150 次
        put(CurseDefs.TIMECORE, 4, 3000L, POINTS);   // e5 盈伤   :539 每次满血挨打都累加（同一条命可反复满血累加，死亡清零）
        put(CurseDefs.TIMECORE, 5, 1L, TIMES);       // e6 坠渊   :436 单次摔落 ≥15 点并存活 → 1 次
        put(CurseDefs.TIMECORE, 6, 10000L, POINTS);  // e7 钝锋   :512 累计造成 10000 点
        put(CurseDefs.TIMECORE, 7, 6000L, TIME);     // e8 止息   :127 每 tick +1 → 6000 tick = 5 分钟
        put(CurseDefs.TIMECORE, 8, 200L, KILLS);     // e9 断粮   :623 饥饿 V 下击杀 200 只
        put(CurseDefs.TIMECORE, 9, 5000L, POINTS);   // e10 夺佑  :524 移速属性值 <0.05 时承受 5000 点（死亡不清零）
        put(CurseDefs.TIMECORE, 10, 2000L, POINTS);  // e11 锈甲  :473 护甲 ≤5 时承受 2000 点物理
        put(CurseDefs.TIMECORE, 11, 10000L, POINTS); // e12 缚己  :721 夺魂索命累计 10000 点
        put(CurseDefs.TIMECORE, 12, 30L, TIMES);     // e13 余悸  :445 累计 30 次大击
        put(CurseDefs.TIMECORE, 13, 50L, KINDS);     // e14 裁影  :881 setProgress(n)，目标 STOLEN_KIND_GOAL[785]=50 种
        put(CurseDefs.TIMECORE, 14, 500L, KILLS);    // e15 厄运  :629 击杀 500 只
        put(CurseDefs.TIMECORE, 15, 200L, TIMES);    // e16 释刃  :543 主手脱手 200 次
        put(CurseDefs.TIMECORE, 16, 3000L, CELLS);   // e17 重负  :217 移速属性值 ≤0.02 时累计移动 3000 格（真格累加）
        put(CurseDefs.TIMECORE, 17, 10000L, CELLS);  // e18 滞步  :221 缓慢下走地 10000 格（真格累加）
        put(CurseDefs.TIMECORE, 18, 50L, KILLS);     // e19 障目  :633 失明下击杀 50 只
        put(CurseDefs.TIMECORE, 19, 1000L, POINTS);  // e20 垂翅  :550 飞行中造成 1000 点
        put(CurseDefs.TIMECORE, 20, 10L, TIMES);     // e21 无援  :673 生命 ≤2 时拍立得拍 10 张
        put(CurseDefs.TIMECORE, 21, 100L, TIMES);    // e22 封镜  :775 Y ≥200 处拍 100 张
        put(CurseDefs.TIMECORE, 22, 1L, KILLS);      // e23 平势  :638 击杀暮色巫妖

        // ==================== ⑥ 终焉秘仪（偏移 43，7 条） ====================
        // 七条各配一只 BOSS：FinalriteCurse:95-103 BOSS_ENTRY → :344 tick(player, D, entry, 1L, 1L)
        for (int i = 0; i < CurseDefs.FINALRITE.entries(); i++) {
            put(CurseDefs.FINALRITE, i, 1L, KILLS);
        }

        // ==================== ⑦ 零号誓炉（偏移 50，14 = 7 诅咒 + 7 祝福） ====================
        // 全部出自 handler/curse/ZeroforgeCurse.java。
        put(CurseDefs.ZEROFORGE, 0, 36000L, TIME);   // e1 燃魂   :373 每 tick +1，NO_SOUL_GOAL_TICKS[99]=36000 tick = 30 分钟
        put(CurseDefs.ZEROFORGE, 1, 200L, KILLS);    // e2 反噬   :1305 低血量击杀 200 只，LOW_KILL_GOAL[108]
        put(CurseDefs.ZEROFORGE, 2, 100L, TIMES);    // e3 锈契   :472 累计丢失 100 次，MISS_GOAL[113]
        put(CurseDefs.ZEROFORGE, 3, 1L, KILLS);      // e4 断供   :1313 击杀远古遗魂
        put(CurseDefs.ZEROFORGE, 4, 1L, KILLS);      // e5 封库   :1316 击杀娜迦（twilightforest:naga）
        put(CurseDefs.ZEROFORGE, 5, 50L, TIMES);     // e6 叛主   :725 承受敌对雪球 50 次，SNOWBALL_HIT_GOAL[129]
        put(CurseDefs.ZEROFORGE, 6, 200L, KILLS);    // e7 易手   :1321 Y <0 处击杀 200 只，LOW_Y_KILL_GOAL[142]
        put(CurseDefs.ZEROFORGE, 7, 1L, TIMES);      // e8 神愤   :868 四种活性药水 ≥10 级（达成一次）
        put(CurseDefs.ZEROFORGE, 8, 1L, TIMES);      // e9 阅历   :877 经验等级 ≥30（达成一次）
        put(CurseDefs.ZEROFORGE, 9, 10L, KINDS);     // e10 灵通  :911 setProgress(n)，GLIMPSE_GOAL[167]=10 种
        put(CurseDefs.ZEROFORGE, 10, 50L, KINDS);    // e11 博识  :1028 setProgress(n)，SPECIES_GOAL[179]=50 种
        put(CurseDefs.ZEROFORGE, 11, 12L, KINDS);    // e12 天卷  :1098 setProgress(n)，ACCESSORY_GOAL[188]=12 件
        put(CurseDefs.ZEROFORGE, 12, 500L, TIMES);   // e13 坚毅  :1130 累计 500 次负面效果，DEBUFF_GOAL[199]
        put(CurseDefs.ZEROFORGE, 13, 6L, KINDS);     // e14 银环  :1171 setProgress(n)，SOUL_KIND_GOAL[206]=6 种
    }

    private static void put(CurseDef def, int entry, long goal, String unit) {
        int index = def.maskOffset() + entry;
        if (index < 0 || index >= CurseDefs.MASK_BITS) return;
        GOALS[index] = goal;
        UNITS[index] = unit;
    }

    /** 该条目标值；未知 / 无目标条目返回 {@code 0} */
    public static long goal(CurseDef def, int entry) {
        if (def == null || entry < 0 || entry >= def.entries()) return 0L;
        int index = def.maskOffset() + entry;
        return (index >= 0 && index < CurseDefs.MASK_BITS) ? GOALS[index] : 0L;
    }

    /** 该条是否带数值目标（决定 tooltip 要不要显示 {@code [当前 / 目标]}） */
    public static boolean hasGoal(CurseDef def, int entry) {
        return goal(def, entry) > 0L;
    }

    /**
     * 该条单位对应的 lang 键（含 {@code gui.lensouls.curse.prog.} 前缀）。
     * 无目标条目返回 {@link #NONE}；未知条目返回 {@link #GENERIC}。
     */
    public static String unitKey(CurseDef def, int entry) {
        if (def == null || entry < 0 || entry >= def.entries()) return GENERIC;
        int index = def.maskOffset() + entry;
        if (index < 0 || index >= CurseDefs.MASK_BITS) return GENERIC;
        String unit = UNITS[index];
        return (unit == null || unit.isEmpty()) ? GENERIC : unit;
    }

    /** 便捷入口：按定义 + 条目格式化「当前 / 目标 + 单位」 */
    public static String format(CurseDef def, int entry, long value) {
        return format(value, unitKey(def, entry), goal(def, entry));
    }

    /**
     * 生成「当前 / 目标 + 单位」文本。
     * <ul>
     *   <li>{@code goal ≤ 0}（无目标）：只显示当前值，用 {@link #NONE}（{@code %s}）</li>
     *   <li>时间类（{@link #TIME}）：当前值与目标值都由 tick 化成 {@code Xm Ys}（整分钟不写秒、不足 1 分钟写 {@code 45s}）</li>
     *   <li>其余：直接套该条的单位模板</li>
     * </ul>
     */
    public static String format(long value, String unit, long goal) {
        if (goal <= 0L) {
            return Component.translatable(NONE, Long.toString(Math.max(0L, value))).getString();
        }
        String key = (unit == null || unit.isEmpty() || NONE.equals(unit)) ? GENERIC : unit;
        if (TIME.equals(key)) {
            return Component.translatable(TIME, ticks(value), ticks(goal)).getString();
        }
        return Component.translatable(key, Long.toString(value), Long.toString(goal)).getString();
    }

    /** tick → {@code Xm Ys}：整分钟不写秒（{@code 10m}），不足 1 分钟只写秒（{@code 45s}） */
    public static String ticks(long ticks) {
        long seconds = Math.max(0L, ticks) / 20L;
        long minutes = seconds / 60L;
        long rest = seconds % 60L;
        if (minutes <= 0L) return rest + "s";
        if (rest == 0L) return minutes + "m";
        return minutes + "m " + rest + "s";
    }
}
