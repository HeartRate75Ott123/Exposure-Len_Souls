package com.plumejade.lensouls.feather;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * 玩家的 5 个羽毛槽数据（玩家附件，序列化 + {@code copyOnDeath}）。
 * <p>
 * <b>锁定语义</b>：生存模式下「装入即永久锁定」——该槽此后左右键都无效，只有创造模式能右键卸下。
 * 锁定标记与内容一起持久化，并且必须**跨死亡保留**，所以附件注册时要用 {@code copyOnDeath()}。
 * <p>
 * <b>物品去向</b>：槽里放的是**真实物品**（等同 Curios：装进去＝从背包移入该槽，卸下退回背包）。
 * 生效判定见 {@link FeatherEquip}，它把「界面槽」与「Curios 佩戴」视为等价的两个通道。
 */
public class FeatherSlotData implements INBTSerializable<CompoundTag> {

    /** 槽位数量（界面横排 5 个，与资产 slot.png 的排布一致） */
    public static final int SLOTS = 5;

    private static final String TAG_ITEMS = "items";
    private static final String TAG_LOCKS = "locks";
    private static final String TAG_SLOT = "slot";
    private static final String TAG_STACK = "stack";

    private final ItemStack[] items = new ItemStack[SLOTS];
    private final boolean[] locked = new boolean[SLOTS];

    public FeatherSlotData() {
        for (int i = 0; i < SLOTS; i++) items[i] = ItemStack.EMPTY;
    }

    // ========== 读写 ==========

    public static boolean inRange(int slot) {
        return slot >= 0 && slot < SLOTS;
    }

    public ItemStack get(int slot) {
        return inRange(slot) ? items[slot] : ItemStack.EMPTY;
    }

    public void set(int slot, ItemStack stack) {
        if (!inRange(slot)) return;
        items[slot] = (stack == null) ? ItemStack.EMPTY : stack;
    }

    public boolean isEmpty(int slot) {
        return get(slot).isEmpty();
    }

    /** 该槽是否已被永久锁定（生存装入后为 true） */
    public boolean isLocked(int slot) {
        return inRange(slot) && locked[slot];
    }

    public void setLocked(int slot, boolean value) {
        if (inRange(slot)) locked[slot] = value;
    }

    // ========== 查询（生效判定用） ==========

    /**
     * 是否装配了该物品（任意槽位）。
     * <p>
     * 返回布尔值即天然「同种物品只生效一次」——槽里塞两根同样的羽毛不会让效果翻倍。
     */
    public boolean contains(Item item) {
        if (item == null) return false;
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.is(item)) return true;
        }
        return false;
    }

    /** 该物品在槽位里的总数（供界面/调试显示用） */
    public int countOf(Item item) {
        if (item == null) return 0;
        int n = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.is(item)) n += stack.getCount();
        }
        return n;
    }

    /** 第一个空槽下标；没有则 -1 */
    public int firstEmpty() {
        for (int i = 0; i < SLOTS; i++) {
            if (items[i].isEmpty()) return i;
        }
        return -1;
    }

    // ========== NBT ==========

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();

        ListTag list = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            if (items[i].isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt(TAG_SLOT, i);
            entry.put(TAG_STACK, items[i].save(provider));
            list.add(entry);
        }
        tag.put(TAG_ITEMS, list);

        // 锁定标记用一个位掩码（比 5 个布尔键紧凑，也不怕槽数以后变）
        int mask = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (locked[i]) mask |= (1 << i);
        }
        tag.putInt(TAG_LOCKS, mask);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        for (int i = 0; i < SLOTS; i++) {
            items[i] = ItemStack.EMPTY;
            locked[i] = false;
        }

        ListTag list = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getInt(TAG_SLOT);
            if (!inRange(slot)) continue;
            items[slot] = ItemStack.parseOptional(provider, entry.getCompound(TAG_STACK));
        }

        int mask = tag.getInt(TAG_LOCKS);
        for (int i = 0; i < SLOTS; i++) {
            locked[i] = (mask & (1 << i)) != 0;
        }
    }
}
