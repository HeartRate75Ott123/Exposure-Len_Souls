package com.plumejade.lensouls.handler.curse;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 诅咒「把手中的东西丢出去」的统一实现（⑤ e16 释刃 / ⑦ e7 易手）。
 * <p>
 * 之前两处各自写了一遍「读主手 → 拷贝 → 清槽 → {@code player.drop}」，出过两个事故：
 * <ul>
 *   <li><b>凭空多出一份</b>：先丢复制品、再清槽，中间任何一步没生效（读到的槽位与实际清空
 *       的槽位不是同一格、别的处理器同 tick 又把物品塞回去、异常打断），地上就多出一份而手里还在；
 *   <li><b>反复刷</b>：抽概率的入口（每段伤害都判一次 2%）在同一个 tick 里会被反复触发。</li>
 * </ul>
 * 这里的顺序就是防这两件事的：<b>先拿到物品 → 立刻清空该槽 → 回读校验确实空了 → 才生成掉落物</b>，
 * 再加「同一玩家 1 秒内最多丢一次」。也就是说：一次逻辑触发 = 一次「把手里的东西模拟丢出去」，
 * 而不是「额外生成一堆同款掉落」。
 */
public final class CurseHandDrop {

    /** 同一玩家两次丢手的最小间隔（tick）：抽概率的入口在多段伤害下会被反复调用 */
    private static final long MIN_INTERVAL_TICKS = 20L;

    /** 上次真正丢出的游戏刻 */
    private static final Map<UUID, Long> LAST_DROP = new ConcurrentHashMap<>();

    private CurseHandDrop() {
    }

    /**
     * 把 {@code hand} 上的那件物品丢出去。
     *
     * @param around {@code true} = 往四周抛（原版 {@code drop(stack, true)}），{@code false} = 丢在脚下
     * @param reason 日志里的来源标记（{@code "timecore-e16"} / {@code "zeroforge-e7"}）
     * @return 是否真的丢出了一件
     */
    public static boolean dropHeld(ServerPlayer player, InteractionHand hand, boolean around, String reason) {
        if (player == null || hand == null) return false;
        long now = player.level().getGameTime();
        Long last = LAST_DROP.get(player.getUUID());
        if (last != null && now - last.longValue() < MIN_INTERVAL_TICKS) return false;

        Inventory inv = player.getInventory();
        int slot = hand == InteractionHand.MAIN_HAND ? inv.selected : Inventory.SLOT_OFFHAND;
        ItemStack held = inv.getItem(slot);
        if (held.isEmpty()) return false;

        // 1) 先清空槽位 —— 只有清空成功才允许生成掉落物，顺序反了就是「凭空多一份」
        ItemStack copy = held.copy();
        inv.setItem(slot, ItemStack.EMPTY);
        inv.setChanged();

        // 2) 回读校验：清空没生效（槽位内容被别的处理器换掉 / 索引对不上）就放弃，
        //    绝不在「手里还留着」的情况下再丢一份出去
        if (!inv.getItem(slot).isEmpty()) {
            LenSouls.LOGGER.warn("[Curse] {} 丢手放弃：槽位清空失败 slot={}", reason, slot);
            return false;
        }

        // 3) 只生成这一个掉落物（原版 Player#drop：脚下 + 40 tick 拾取延迟）
        player.drop(copy, around);
        player.containerMenu.broadcastChanges();

        LAST_DROP.put(player.getUUID(), now);
        LenSouls.LOGGER.info("[Curse] {} 丢出手中物品 {} x{} slot={}",
                reason, copy.getItem().toString(), copy.getCount(), slot);
        return true;
    }

    /** 退出世界 / 死亡时清计时（与其它瞬时计时同口径） */
    public static void clear(UUID id) {
        if (id != null) LAST_DROP.remove(id);
    }

    /** 仅供调试：忘掉节流 */
    public static void forget(UUID id) {
        clear(id);
    }

    /** 调试用：当前节流状态是否允许立即丢出 */
    public static boolean ready(ServerPlayer player) {
        if (player == null) return false;
        Long last = LAST_DROP.get(player.getUUID());
        return last == null || player.level().getGameTime() - last.longValue() >= MIN_INTERVAL_TICKS;
    }
}
