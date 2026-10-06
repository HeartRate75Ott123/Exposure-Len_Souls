package com.plumejade.lensouls.feather;

import com.plumejade.lensouls.network.CurseSyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

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
        player.displayClientMessage(Component.translatable("message.lensouls.curse.reversed",
                Component.translatable(def.entryKey(entry) + ".name")), false);
    }

    /** 重置某条进度（如「余悸」死亡即重置计数） */
    public static void resetProgress(ServerPlayer player, CurseDef def, int entry) {
        if (player == null || def == null) return;
        state(player).setProgress(def.maskOffset() + entry, 0L);
    }
}
