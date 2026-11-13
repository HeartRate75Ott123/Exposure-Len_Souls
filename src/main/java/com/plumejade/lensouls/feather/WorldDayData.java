package com.plumejade.lensouls.feather;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * 玩家「本世界起点天」（绝对天，1 起）—— {@code WorldDays} 的**唯一**数据载体。
 *
 * <h2>为什么从 persistentData 搬到附件</h2>
 * 老实现把起点天写在 {@code player.getPersistentData()}（存档里是 {@code PlayerPersisted} 复合标签）。
 * 玩家实测：戴羽毛过了 3 天、地图仍是第 3 天，**一死**诅咒里的天数就归零。
 * 复活时 NeoForge 只重建玩家实体、只搬它自己列的字段，自定义复合数据不保证跟过来；
 * 而附件注册成 {@code copyOnDeath()} 后是**框架负责**整体复制给新玩家实例的
 * （羽毛槽与诅咒反转掩码就是靠这个机制活过死亡的，见 {@link FeatherAttachments}）。
 * 所以「自己的天数」这种**必须跨死亡连续**的量，只能放附件，不能放 persistentData。
 *
 * <h2>判定语义</h2>
 * {@code startDay = 0} 表示「还没记过起点」（新玩家或老存档未迁移），由
 * {@code WorldDays.ensureOrigin} 在首次观测时写成本世界当天。
 */
public class WorldDayData implements INBTSerializable<CompoundTag> {

    private static final String TAG_START_DAY = "startDay";

    /** 本世界起点天（绝对天，1 起）；0 = 尚未记录 */
    private long startDay;

    /** 是否已经记过起点天 */
    public boolean hasOrigin() {
        return startDay > 0L;
    }

    /** 起点天（绝对天，1 起）；未记录时为 0 */
    public long origin() {
        return startDay;
    }

    /** 写入起点天（0 = 清空，下次调用重新以当天为起点） */
    public void setOrigin(long day) {
        this.startDay = Math.max(0L, day);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TAG_START_DAY, startDay);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        startDay = tag.getLong(TAG_START_DAY);
    }
}
