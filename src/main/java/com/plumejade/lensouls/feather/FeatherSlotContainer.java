package com.plumejade.lensouls.feather;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 把 {@link FeatherSlotData} 包成原版 {@link Container}，供 {@code FeatherSlotMenu} 挂 5 个槽位。
 * <p>
 * 这样槽内容是**真实物品**：菜单槽位会自动同步到客户端（界面里能看到槽里放了什么），
 * 服务端改动也会正常下发——这与「41 个隐藏玩家背包槽」是同一套机制。
 * <p>
 * {@code setChanged()} 是空实现：数据本身就在玩家附件里（写入即持久化），
 * 没有需要 markDirty 的载体。
 */
public class FeatherSlotContainer implements Container {

    private final Player player;
    private final FeatherSlotData data;

    public FeatherSlotContainer(Player player) {
        this.player = player;
        this.data = FeatherAttachments.get(player);
    }

    public FeatherSlotData data() {
        return data;
    }

    public Player player() {
        return player;
    }

    @Override
    public int getContainerSize() {
        return FeatherSlotData.SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < FeatherSlotData.SLOTS; i++) {
            if (!data.isEmpty(i)) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return data.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack current = data.get(slot);
        if (current.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack taken = current.split(amount);
        data.set(slot, current.isEmpty() ? ItemStack.EMPTY : current);
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = data.get(slot);
        data.set(slot, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        data.set(slot, stack);
    }

    @Override
    public void setChanged() {
        // 数据在玩家附件里，写入即持久化
    }

    /** 只允许本人操作，且槽位有效 */
    @Override
    public boolean stillValid(Player player) {
        return player != null && player.isAlive() && player == this.player;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < FeatherSlotData.SLOTS; i++) {
            data.set(i, ItemStack.EMPTY);
        }
    }
}
