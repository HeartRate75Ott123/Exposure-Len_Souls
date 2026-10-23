package com.plumejade.lensouls.fx;

import com.plumejade.lensouls.LenSouls;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 服务端延迟任务调度器（tick 驱动）。
 * <p>
 * 用途：给"前摇 → 生效"这类需要服务端配合的时序留位置。
 * 目前只有时空回溯用它（先让客户端播 0.3s 拉伸，再真正传送）。
 * <p>
 * 刻意做得极小：只存短生命周期任务、异常吞掉并记日志（绝不让特效/时序把玩法主循环带崩）、
 * 服务器停止时清空。
 */
public final class FxScheduler {

    private static final class Task {
        int delay;
        final Runnable action;

        Task(int delay, Runnable action) {
            this.delay = delay;
            this.action = action;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();

    private FxScheduler() {
    }

    /** 在 {@code delayTicks} 之后（0 = 下一 tick）执行。 */
    public static void schedule(int delayTicks, Runnable action) {
        if (action == null) return;
        TASKS.add(new Task(Math.max(0, delayTicks), action));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) return;

        List<Task> due = null;
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task task = it.next();
            if (--task.delay <= 0) {
                it.remove();
                if (due == null) due = new ArrayList<>();
                due.add(task);
            }
        }
        if (due == null) return;

        for (Task task : due) {
            try {
                task.action.run();
            } catch (Exception ex) {
                LenSouls.LOGGER.error("[FxScheduler] 延迟任务执行失败", ex);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
    }
}
