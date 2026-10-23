package com.plumejade.lensouls.feather;

import com.plumejade.lensouls.network.CurseSyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 诅咒状态的统一入口（服务端权威）。
 * <p>
 * <b>选中门控</b>：一款诅咒只有被装进 7 个槽位之一才算「选中」——
 * 未选中不产生任何效果、也不累计进度（见 {@link #isActive}）。
 * <p>
 * 反转与进度的读写都在这里收口，写完之后调用 {@link CurseSyncPacket#send} 推给客户端，
 * 客户端的 tooltip 才读得到「已反转 N / M」与逐条进度。
 */
public final class CurseManager {

    /**
     * 上一轮心跳推送时该玩家「装了哪几款」（位掩码）。
     * <p>
     * 唯一用途：发现「原来戴着、这轮没戴了」并补发一次同步，让客户端镜像跟着清零
     * （客户端靠这份镜像判断 ⑦ e5 的封库态）。空掩码的玩家不会在表里留键。
     */
    private static final Map<UUID, Integer> LAST_ACTIVE = new ConcurrentHashMap<>();

    private CurseManager() {
    }

    public static CurseStateData state(Player player) {
        return player.getData(FeatherAttachments.CURSE_STATE);
    }

    /** 该款是否被选中（装在 7 槽之一）。未选中 → 不生效、不统计 */
    public static boolean isActive(Player player, CurseDef def) {
        if (player == null || def == null) return false;
        FeatherSlotData slots = FeatherAttachments.get(player);
        for (int i = 0; i < FeatherSlotData.SLOTS; i++) {
            ItemStack stack = slots.get(i);
            if (!stack.isEmpty() && CurseDefs.byItem(stack.getItem()) == def) return true;
        }
        return false;
    }

    public static boolean isReversed(Player player, CurseDef def, int entry) {
        return state(player).isReversed(def.maskOffset() + entry);
    }

    /** 标记反转（永久）并同步。调用方负责先判定条件是否达成。 */
    public static void reverse(ServerPlayer player, CurseDef def, int entry) {
        state(player).setReversed(def.maskOffset() + entry, true);
        CurseSyncPacket.send(player);
    }

    /** 取消反转并同步（**仅调试指令使用**：正式流程里反转是永久的） */
    public static void unreverse(ServerPlayer player, CurseDef def, int entry) {
        state(player).setReversed(def.maskOffset() + entry, false);
        CurseSyncPacket.send(player);
    }

    public static long progress(Player player, CurseDef def, int entry) {
        return state(player).progress(def.maskOffset() + entry);
    }

    public static void setProgress(ServerPlayer player, CurseDef def, int entry, long value) {
        state(player).setProgress(def.maskOffset() + entry, value);
        CurseSyncPacket.send(player);
    }

    /** 累加进度（已反转的条目不重复累加）。返回累加后的值。 */
    public static long addProgress(ServerPlayer player, CurseDef def, int entry, long delta) {
        CurseStateData data = state(player);
        int index = def.maskOffset() + entry;
        if (data.isReversed(index)) return data.progress(index);
        data.addProgress(index, delta);
        CurseSyncPacket.send(player);
        return data.progress(index);
    }

    /** 登录 / 重生后补一次同步（客户端 tooltip 依赖这份镜像） */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CurseSyncPacket.send(player);
        }
    }

    /** 登出清掉心跳记账（避免 UUID 在内存里长期残留） */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            LAST_ACTIVE.remove(event.getEntity().getUUID());
        }
    }

    /**
     * 每 20 tick 推一次进度（心跳）。
     * <p>
     * tooltip 的「当前 / 目标」进度（见 {@link CurseGoals}）与列表行的「已反转 N / M」都读**客户端镜像**，
     * 而进度是每 tick 累加的（治疗量 / 伤害量 / 格数 / 分钟…）。只在反转时推会让数字长期停在旧值，
     * 所以这里做低频心跳：20 tick = 1 秒，且**只有装了诅咒的玩家**才收（没装的一个字段判断就跳过）。
     * <p>
     * <b>摘掉羽毛后必须再补一次</b>：客户端还靠这份镜像判断「是否处于封库态」（⑦ e5 的界面拦截），
     * 如果只在佩戴期间推送，摘下后客户端会一直以为还戴着 —— 所以按
     * {@link #LAST_ACTIVE} 记住上一轮是否推过，从「有」变「无」的那一秒补发一包清零。
     */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            int active = activeMask(player);
            if (active == 0) {
                if (LAST_ACTIVE.remove(player.getUUID()) != null) {
                    CurseSyncPacket.send(player);   // 从佩戴变未佩戴：补一次，清掉客户端镜像
                }
                continue;
            }
            LAST_ACTIVE.put(player.getUUID(), active);
            CurseSyncPacket.send(player);
        }
    }

    /** 是否至少装了一款诅咒（决定要不要发心跳同步） */
    public static boolean anyEquipped(Player player) {
        if (player == null) return false;
        FeatherSlotData slots = FeatherAttachments.get(player);
        for (int i = 0; i < FeatherSlotData.SLOTS; i++) {
            if (!slots.get(i).isEmpty() && CurseDefs.byItem(slots.get(i).getItem()) != null) return true;
        }
        return false;
    }

    /**
     * 「当前装了哪几款诅咒」的位掩码（bit i = {@link CurseDefs#ALL} 第 i 项）。
     * <p>
     * 随 {@link CurseSyncPacket} 发到客户端，客户端的 {@code CurseClientCache#isActive}
     * 才有办法做 {@code on} / {@code rev} 的等价判断。
     */
    public static int activeMask(Player player) {
        if (player == null) return 0;
        FeatherSlotData slots = FeatherAttachments.get(player);
        int mask = 0;
        for (int i = 0; i < FeatherSlotData.SLOTS; i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) continue;
            CurseDef def = CurseDefs.byItem(stack.getItem());
            if (def == null) continue;
            int which = CurseDefs.ALL.indexOf(def);
            if (which >= 0) mask |= (1 << which);
        }
        return mask;
    }

    // ========== 效果实装的便利 API ==========

    /** 该条的**诅咒态**是否生效（该款选中 且 该条未反转） */
    public static boolean on(Player player, CurseDef def, int entry) {
        return isActive(player, def) && !isReversed(player, def, entry);
    }

    /** 该条的**反转态**是否生效（该款选中 且 该条已反转） */
    public static boolean rev(Player player, CurseDef def, int entry) {
        return isActive(player, def) && isReversed(player, def, entry);
    }

    /**
     * 累加进度并判定达成：达到 {@code goal} 就**立即反转**（幂等，已反转不再重复触发）。
     *
     * @return 累加后的进度（未选中 / 已反转时返回 0）
     */
    public static long tick(ServerPlayer player, CurseDef def, int entry, long delta, long goal) {
        if (player == null || def == null) return 0L;
        if (!isActive(player, def)) return 0L;
        int index = def.maskOffset() + entry;
        CurseStateData data = state(player);
        if (data.isReversed(index)) return 0L;

        long now = data.progress(index) + delta;
        data.setProgress(index, now);
        if (goal > 0 && now >= goal) {
            reach(player, def, entry);
        }
        return now;
    }

    /** 置位反转 + 同步 + 给玩家一条提示（幂等） */
    public static void reach(ServerPlayer player, CurseDef def, int entry) {
        if (player == null || def == null) return;
        if (!isActive(player, def)) return;
        if (isReversed(player, def, entry)) return;
        reverse(player, def, entry);
        // ⑦ 的祝福不是「反转」而是「解锁」，聊天消息也要区分（tooltip 已按 sectionAt 区分）
        boolean blessing = def.sectionAt() > 0 && entry >= def.sectionAt();
        player.displayClientMessage(Component.translatable(
                blessing ? "message.lensouls.curse.unlocked" : "message.lensouls.curse.reversed",
                Component.translatable(def.entryKey(entry) + ".name")), false);
    }

    /** 重置某条进度（如「余悸」死亡即重置计数） */
    public static void resetProgress(ServerPlayer player, CurseDef def, int entry) {
        if (player == null || def == null) return;
        state(player).setProgress(def.maskOffset() + entry, 0L);
    }
}
