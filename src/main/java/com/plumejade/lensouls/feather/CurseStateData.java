package com.plumejade.lensouls.feather;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * 诅咒的「反转位掩码 + 逐条受难进度」（玩家附件，序列化 + {@code copyOnDeath}）。
 * <p>
 * <b>反转一次达成即永久解锁</b>：位一旦置上就不复位（不因死亡 / 摘除 / 掉线回退）。
 * <p>
 * 7 个槽位的内容不在这里 —— 仍在 {@link FeatherSlotData}（已由 5 槽扩到 7 槽），
 * 本类只承担「哪几条反转了、进度多少」这半边状态。
 * <p>
 * 进度**只在该款被选进槽位期间**累计（门控见 {@code CurseManager.isActive}），
 * 未选中不累计；单位由各条自定（次数 / 格数 / 分钟 / 伤害点数…）。
 */
public class CurseStateData implements INBTSerializable<CompoundTag> {

    private static final String TAG_MASK = "reversedMask";
    private static final String TAG_PROGRESS = "progress";

    /** bit i = 第 i 条已反转（位序见 {@link CurseDefs}） */
    private long reversedMask;
    /** 逐条进度（与掩码同序） */
    private final long[] progress = new long[CurseDefs.MASK_BITS];

    public boolean isReversed(int index) {
        return index >= 0 && index < CurseDefs.MASK_BITS && (reversedMask & (1L << index)) != 0L;
    }

    public void setReversed(int index, boolean value) {
        if (index < 0 || index >= CurseDefs.MASK_BITS) return;
        if (value) {
            reversedMask |= (1L << index);
        } else {
            reversedMask &= ~(1L << index);
        }
    }

    public long mask() {
        return reversedMask;
    }

    public void setMask(long value) {
        this.reversedMask = value;
    }

    public long progress(int index) {
        return (index >= 0 && index < progress.length) ? progress[index] : 0L;
    }

    public void setProgress(int index, long value) {
        if (index >= 0 && index < progress.length) progress[index] = value;
    }

    public void addProgress(int index, long delta) {
        if (index >= 0 && index < progress.length) progress[index] += delta;
    }

    /** 该款已反转条数（tooltip 的「已反转 N / M」用） */
    public int reversedCount(CurseDef def) {
        int n = 0;
        for (int i = 0; i < def.entries(); i++) {
            if (isReversed(def.maskOffset() + i)) n++;
        }
        return n;
    }

    /** 进度快照（同步用，长度 64） */
    public long[] progressCopy() {
        return progress.clone();
    }

    // ========== NBT ==========

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TAG_MASK, reversedMask);
        tag.put(TAG_PROGRESS, new LongArrayTag(progress.clone()));
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        reversedMask = tag.getLong(TAG_MASK);
        long[] saved = tag.getLongArray(TAG_PROGRESS);
        for (int i = 0; i < progress.length; i++) {
            progress[i] = (i < saved.length) ? saved[i] : 0L;
        }
    }
}
