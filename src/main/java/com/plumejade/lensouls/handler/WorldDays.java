package com.plumejade.lensouls.handler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 世界天数：口径对齐 Xaero 的世界地图 / 小地图「天」（{@code gui.xaero_day}）。
 * <p>
 * <b>Xaero 的算法</b>（反编译 {@code xaero.hud.minimap.info.BuiltInInfoDisplays} 的
 * {@code lambda$static$14} 确认，两条信息行共用同一份计算）：
 * <pre>
 * long t   = 6000L + level.getDayTime();     // 偏移 6000 tick ⇒ 以**午夜**换日（dayTime 0 = 06:00）
 * int  day = (int) (t / 24000L) + 1;         // 第 1 天 = 开局那天
 * int  hh  = (int) (t % 24000L) / 1000;      // 0..23（午夜为 0 点，与真实时钟同构）
 * int  mm  = (int) (((t % 24000L) % 1000) / 1000.0D * 60.0D);
 * </pre>
 * 也就是说「天」是绝对量（全世界同一个数），换日点是<b>午夜</b>而不是原版的「夜→昼」，
 * 而且睡一觉 / {@code /time set} 造成的跳跃会被如实算进去。
 * <p>
 * <b>本模组为什么要再叠一层「每个人自己的天」</b>：折翼沉渊的 e3 安魂曲按「已过天数」扣血
 * （每天 −1 上限）且反转窗口是「天数 &lt; 5」。如果直接拿绝对天数，一个刚进服务器的新人
 * 会因为世界已经过了几十天而立刻被扣成 1 血、并且永远错过反转窗口 ——
 * 所以这里记「该玩家在本世界的起点天」（首次被观测时写进玩家持久数据），
 * 之后一律用 {@link #elapsedDays}（0 起）：
 * <ul>
 *   <li>开局就在线的玩家：起点天 = 1 ⇒ 自己的天数与地图上看到的天数完全一致；</li>
 *   <li>中途加入的玩家：起点天 = 加入那天 ⇒ 从自己加入的那天开始数，独立计时。</li>
 * </ul>
 * 这也是旧实现（{@code FeatherAbyssDayData}：主世界夜→昼跳变计数、全服共用一份 SavedData）
 * 被替换掉的原因：那份计数既与地图不一致，也不可能按玩家独立。旧存档里的计数值直接作废
 * （{@code lensouls_abyss_days} 成为无主数据，读都不读）。
 * <p>
 * 唯一的入口语义：{@link #absoluteDay} 只回答「现在是世界第几天」（与地图一致），
 * {@link #elapsedDays} 只回答「你在这个世界过了几天」（本模组所有效果都用它）。
 */
public final class WorldDays {

    /** 一天 24000 tick（原版常量，Xaero 同款） */
    public static final long TICKS_PER_DAY = 24000L;

    /**
     * 换日偏移：{@code dayTime} 0 是早上 6:00，加 6000 tick 后 0 点落在午夜 ——
     * 与 Xaero「天 / 时:分」的换日点逐 tick 对齐。
     */
    public static final long DAY_BOUNDARY_OFFSET = 6000L;

    /** 玩家持久数据里「本世界起点天」的键（绝对天，1 起） */
    private static final String KEY_START_DAY = "lensouls:world_day_start";

    /**
     * 折翼沉渊 e3 安魂曲的反转窗口（天）：**自己的**已过天数 ≥ 该值即过期。
     * <p>
     * 放在这里是为了让「天数」只有一个出处（{@code FeatherAbyssHandler.E3_DAY_LIMIT}
     * 与调试指令 {@code /lensouls curse days} 都引这一个常量）。
     */
    public static final int ABYSS_REVERSE_WINDOW_DAYS = 5;

    private WorldDays() {
    }

    /** 绝对天数（1 起，全世界同一个值；等于 Xaero 地图上的「第 N 天」） */
    public static long absoluteDay(ServerLevel overworld) {
        if (overworld == null) return 1L;
        return Math.floorDiv(overworld.getDayTime() + DAY_BOUNDARY_OFFSET, TICKS_PER_DAY) + 1L;
    }

    /** 绝对天数：取主世界（与服务端全局一致） */
    public static long absoluteDay(MinecraftServer server) {
        if (server == null) return 1L;
        return absoluteDay(server.getLevel(Level.OVERWORLD));
    }

    /**
     * 记下该玩家的「起点天」：**首次被观测到的当天**（= 他加入这个世界的第 1 天）。
     * <p>
     * 由 {@code FeatherAbyssHandler} 在登录当刻与每 20 tick 兜底各调一次；已经有值就什么都不做
     * （换维度、重进、死亡重生都保持同一个起点）。
     */
    public static void ensureOrigin(ServerPlayer player) {
        if (player == null) return;
        CompoundTag tag = player.getPersistentData();
        if (tag.contains(KEY_START_DAY)) return;
        tag.putLong(KEY_START_DAY, absoluteDay(player.getServer()));
    }

    /**
     * 该玩家自己的「已过天数」（0 起）：{@code 绝对天 − 起点天}。
     * <p>
     * 起点天由 {@link #ensureOrigin} 在玩家首次出现时写入（跨死亡 / 重进 / 换维度保留）；
     * 万一还没写过，这里先补写再返回 0。
     * 时间被回拨（回档、{@code /time set}）导致绝对天小于起点时，把起点拉回当天 ——
     * 天数取 0 而不是负数，旧效果不会被反算。
     */
    public static int elapsedDays(ServerPlayer player) {
        if (player == null) return 0;
        MinecraftServer server = player.getServer();
        if (server == null) return 0;
        ensureOrigin(player);
        long abs = absoluteDay(server);
        CompoundTag tag = player.getPersistentData();
        long start = tag.getLong(KEY_START_DAY);
        if (abs < start) {
            tag.putLong(KEY_START_DAY, abs);
            start = abs;
        }
        long elapsed = abs - start;
        return elapsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsed;
    }

    /** 该玩家自己的「第几天」（1 起，= 已过天数 + 1，仅用于日志 / 展示） */
    public static int ownDay(ServerPlayer player) {
        return elapsedDays(player) + 1;
    }

    /** 调试 / 迁移用：清掉某个玩家的起点天（下次调用重新以当天为起点） */
    public static void resetOrigin(ServerPlayer player) {
        if (player == null) return;
        player.getPersistentData().remove(KEY_START_DAY);
    }
}
