package com.plumejade.lensouls.timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家绑定计时器服务（单例）。
 * <p>
 * 为每个玩家维护一组计时器（UUID → Map&lt;timerId, endTick&gt;）。
 * 由 {@link com.plumejade.lensouls.LenSouls} 在服务端每 tick 驱动 {@link #tick()} 以清理过期条目。
 */
public class TimerService {

    private static final Logger LOGGER = LoggerFactory.getLogger("LenSoulsTimer");
    private static final TimerService INSTANCE = new TimerService();

    /** 玩家 UUID → (计时器 ID → 到期 tick) */
    private final Map<UUID, Map<String, Long>> playerTimers = new ConcurrentHashMap<>();
    private long currentTick = 0;

    private TimerService() {}

    public static TimerService getInstance() {
        return INSTANCE;
    }

    // ========== 公开 API ==========

    /**
     * 启动或重置一个计时器。
     *
     * @param playerUuid  玩家 UUID
     * @param timerId     计时器标识（如 "soul_cooldown_fire"）
     * @param durationTicks 持续刻数（20 ticks = 1 秒）
     */
    public void start(UUID playerUuid, String timerId, long durationTicks) {
        long endTick = currentTick + durationTicks;
        playerTimers.computeIfAbsent(playerUuid, k -> new HashMap<>()).put(timerId, endTick);
    }

    /**
     * 检查指定计时器是否仍在运行。
     */
    public boolean isActive(UUID playerUuid, String timerId) {
        Map<String, Long> timers = playerTimers.get(playerUuid);
        if (timers == null) return false;
        Long endTick = timers.get(timerId);
        if (endTick == null) return false;
        if (currentTick >= endTick) {
            timers.remove(timerId);
            return false;
        }
        return true;
    }

    /**
     * 返回剩余刻数（≤ 0 表示无活跃计时器）。
     */
    public long getRemainingTicks(UUID playerUuid, String timerId) {
        Map<String, Long> timers = playerTimers.get(playerUuid);
        if (timers == null) return 0;
        Long endTick = timers.get(timerId);
        if (endTick == null) return 0;
        long remaining = endTick - currentTick;
        if (remaining <= 0) {
            timers.remove(timerId);
            return 0;
        }
        return remaining;
    }

    /**
     * 取消指定计时器。
     */
    public void cancel(UUID playerUuid, String timerId) {
        Map<String, Long> timers = playerTimers.get(playerUuid);
        if (timers != null) {
            timers.remove(timerId);
            if (timers.isEmpty()) {
                playerTimers.remove(playerUuid);
            }
        }
    }

    /**
     * 清除指定玩家的所有计时器。
     */
    public void clearPlayer(UUID playerUuid) {
        playerTimers.remove(playerUuid);
    }

    // ========== 内部 ==========

    /**
     * 每 tick 调用一次（仅服务端）。
     * 递增内部计数器并清理过期条目。
     */
    public void tick() {
        currentTick++;
        if (currentTick % 20 == 0) {
            cleanup();
        }
    }

    /**
     * 扫描并移除所有过期的计时器条目。
     */
    private void cleanup() {
        playerTimers.values().forEach(timers -> {
            timers.entrySet().removeIf(entry -> currentTick >= entry.getValue());
        });
        playerTimers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
