package com.plumejade.lensouls.util;

import com.plumejade.lensouls.LenSouls;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 出片诊断日志：<b>INFO 可见 + 按原因限流</b>。
 *
 * <p><b>为什么要这一层</b>：出片链路的「没成片」原因对玩家/服主是<b>可见症状</b>，
 * 藏在 debug 里等于没有（FML 默认配置里 debug 只进 {@code logs/debug.log}，
 * 不进 {@code latest.log} 与控制台）。但这类分支又挂在<b>每次拍照</b>上
 * （没选能力、拍到牛、空帧…都是正常玩法），直接 INFO 会刷屏。
 * ⇒ 折中：**同一条原因 60 秒最多一条，一场最多 40 条**，且到达上限后只报一次「已抑制 N 条」。
 *
 * <p><b>性能</b>：
 * <ul>
 *   <li>调用方传的是 {@link Supplier}，<b>被限流时连字符串都不拼</b>——热路径上只有一次
 *       {@code HashMap} 查询（原因键是常量，集合大小固定十几条，不增长）；</li>
 *   <li>限流表用常量原因键，不按玩家/帧 id 建键，因此<b>永不清理也不会涨</b>；</li>
 *   <li>只做「时间戳比较 + 计数」，没有正则、没有栈、没有字符串格式化的前置开销。</li>
 * </ul>
 *
 * <p>注意：这是**诊断**日志，不是玩法提示。要提示玩家就发动作栏消息，不要在这里做。
 */
public final class PhotoLog {

    /** 同一条原因在这么长时间内最多输出一条（60 秒）。 */
    private static final long COOLDOWN_NANOS = 60_000_000_000L;
    /** 一场游戏最多输出多少条（防某个未知原因每 tick 刷）。 */
    private static final int SESSION_CAP = 40;

    /** 原因键 → 上次输出的纳秒时间戳（键是有限常量集合，不需要清理） */
    private static final Map<String, Long> LAST_AT = new ConcurrentHashMap<>();
    private static final AtomicInteger EMITTED = new AtomicInteger();
    private static final AtomicInteger SUPPRESSED = new AtomicInteger();
    private static volatile boolean capNoticeLogged;

    private PhotoLog() {
    }

    /** 记一条「没成片」诊断：同一原因 60 秒一条、全场 40 条上限。 */
    public static void info(String reason, Supplier<String> message) {
        if (!allow(reason)) return;
        LenSouls.LOGGER.info("[PhotoInject] {}", message.get());
    }

    /** 同一原因 60 秒一条、全场 40 条上限的 debug 版（给「按设计如此」的正常分支用）。 */
    public static void debug(String reason, Supplier<String> message) {
        if (!allow(reason)) return;
        LenSouls.LOGGER.debug("[PhotoInject] {}", message.get());
    }

    /** 是否放行这条原因（放行才允许调用方拼字符串）。 */
    private static boolean allow(String reason) {
        int emitted = EMITTED.get();
        if (emitted >= SESSION_CAP) {
            if (!capNoticeLogged) {
                capNoticeLogged = true;
                LenSouls.LOGGER.info("[PhotoInject] 诊断日志已达上限 {} 条，之后只计数不再打印"
                        + "（本场已抑制 {} 条；开启 debug 可看全部）", SESSION_CAP, SUPPRESSED.get());
            }
            SUPPRESSED.incrementAndGet();
            return false;
        }
        long now = System.nanoTime();
        Long last = LAST_AT.get(reason);
        if (last != null && now - last < COOLDOWN_NANOS) {
            SUPPRESSED.incrementAndGet();
            return false;
        }
        LAST_AT.put(reason, now);
        EMITTED.incrementAndGet();
        return true;
    }
}
