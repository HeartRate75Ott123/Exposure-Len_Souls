package com.plumejade.lensouls.feather;

import java.util.Set;

/**
 * 一款诅咒的静态定义（lang 键前缀、条目数、掩码偏移等）。
 * <p>
 * 7 款的条目全部走统一键名：{@code item.lensouls.<id>.eN.{name,tag,curse,cond,after}}，
 * 文案本身写在 lang 里（中英各一份），Java 侧只负责按状态挑键 —— 见
 * {@link com.plumejade.lensouls.item.FeatherCurseItem#appendHoverText}。
 *
 * @param id         道具注册名（feather_timecore …）
 * @param entries    条目数（①3 ②3 ③2 ④12 ⑤23 ⑥7 ⑦14）
 * @param maskOffset 该款第一条在反转位掩码里的起始位
 * @param sharedCond ①②③ 的条件是整款共用（用 {@code .cond_shared} 键，条目下不重复显示）
 * @param grand      ⑤ 有「大成」行
 * @param sectionAt  ⑦ = 7：第 8 条起是祝福，渲染时插一行小标题；其余款为 0
 * @param retained   标〔保留〕的条目序号（1 基）：反转后**原诅咒继续生效**，常显用 {@code .merged} 键
 * @param noReverse  标「无反转」的条目序号（1 基）：Shift 视图里**不显示**条件与反转后
 *                   （如 ④ 的「虚无的承诺」——它没有反转条件，也就没什么可展开的）
 */
public record CurseDef(String id, int entries, int maskOffset,
                       boolean sharedCond, boolean grand, int sectionAt,
                       Set<Integer> retained, Set<Integer> noReverse) {

    /** 该款的 lang 键前缀 */
    public String key() {
        return "item.lensouls." + id;
    }

    /** 第 i 条（0 基）的 lang 键前缀 */
    public String entryKey(int i) {
        return key() + ".e" + (i + 1);
    }

    /** 第 i 条（0 基）是否为〔保留〕条目 */
    public boolean retained(int i) {
        return retained.contains(i + 1);
    }

    /** 第 i 条（0 基）是否**没有反转**（Shift 视图不展开） */
    public boolean noReverse(int i) {
        return noReverse.contains(i + 1);
    }
}
