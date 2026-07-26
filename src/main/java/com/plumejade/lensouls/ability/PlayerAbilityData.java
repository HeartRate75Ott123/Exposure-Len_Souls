package com.plumejade.lensouls.ability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/**
 * 玩家能力数据持有者。
 * <p>
 * 存 4 个能力的 unlocked 状态 + 当前启用能力索引 + 空间扭曲激活坐标（以照片拍摄位置为圆心）。
 * 通过 NBT 序列化/反序列化实现持久化。
 */
public class PlayerAbilityData {

    private static final int COUNT = AbilityType.values().length;
    /** 空间扭曲范围圈半径 */
    public static final double SPATIAL_WARP_RADIUS = 7.0;

    private final boolean[] unlocked = new boolean[COUNT];
    private int currentOrdinal = 0;                     // 当前启用能力的 ordinal

    // 空间扭曲：以照片拍摄坐标为中心的范围圈（warpDim == null 表示未激活）
    private double warpX, warpY, warpZ;
    private String warpDimension;

    // 已发送过首次描述的能力 ID 列表（NBT 持久化防重复）
    private final java.util.Set<String> descriptionsSent = new java.util.HashSet<>();

    public PlayerAbilityData() {
        // 不再默认解锁任何能力，通过能力球获取
    }

    // ========== 读取 ==========

    public boolean isUnlocked(AbilityType type) {
        return unlocked[type.ordinal()];
    }

    public AbilityType getEnabled() {
        AbilityType t = AbilityType.values()[currentOrdinal];
        if (!unlocked[t.ordinal()]) {
            // 兜底：当前能力被锁定 → 寻找第一个已解锁的能力
            for (int i = 0; i < COUNT; i++) {
                if (unlocked[i]) {
                    currentOrdinal = i;
                    return AbilityType.values()[i];
                }
            }
            // 没有任何已解锁的能力，返回当前（安全默认）
        }
        return AbilityType.values()[currentOrdinal];
    }

    /** 空间扭曲是否激活（有坐标圈） */
    public boolean isSpatialWarpActive() {
        return warpDimension != null;
    }

    /** 获取空间扭曲圈中心坐标 */
    public Vec3 getWarpCenter() {
        return new Vec3(warpX, warpY, warpZ);
    }

    /** 获取空间扭曲所在维度 */
    public String getWarpDimension() {
        return warpDimension;
    }

    public boolean hasSentDescription(AbilityType type) {
        return descriptionsSent.contains(type.getId());
    }

    // ========== 写入 ==========

    public void setUnlocked(AbilityType type, boolean value) {
        unlocked[type.ordinal()] = value;
        if (!value && currentOrdinal == type.ordinal()) {
            fallbackToFirstUnlocked();
        }
        if (!value && type == AbilityType.SPATIAL_WARP) {
            resetSpatialWarp();
        }
    }

    public void setEnabled(AbilityType type) {
        if (unlocked[type.ordinal()]) {
            // 切走时保留空间扭曲坐标，切回时自动恢复
            currentOrdinal = type.ordinal();
        }
    }

    /**
     * 激活空间扭曲：以照片拍摄坐标为圆心。
     *
     * @param center    照片拍摄时的坐标
     * @param dimension 所在维度（ResourceLocation 字符串）
     */
    public void setWarpCenter(Vec3 center, String dimension) {
        this.warpX = center.x;
        this.warpY = center.y;
        this.warpZ = center.z;
        this.warpDimension = dimension;
    }

    /** 强制关闭空间扭曲 */
    public void resetSpatialWarp() {
        this.warpDimension = null;
        this.warpX = 0;
        this.warpY = 0;
        this.warpZ = 0;
    }

    public void markDescriptionSent(AbilityType type) {
        descriptionsSent.add(type.getId());
    }

    // ========== 循环 ==========

    /**
     * 按固定顺序切换到下一个已解锁的能力。
     * 若只有一个已解锁能力，返回当前（不变）。
     *
     * @return 切换到的能力，若没有变化返回 null
     */
    public AbilityType cycleToNext() {
        int start = currentOrdinal;
        for (int i = 1; i <= COUNT; i++) {
            int idx = (start + i) % COUNT;
            if (unlocked[idx]) {
                // 切走时保留空间扭曲坐标，切回时自动恢复
                currentOrdinal = idx;
                return AbilityType.values()[idx];
            }
        }
        return null;
    }

    private void fallbackToFirstUnlocked() {
        for (int i = 0; i < COUNT; i++) {
            if (unlocked[i]) {
                currentOrdinal = i;
                return;
            }
        }
        // 全部锁定：保持当前索引不变
    }

    // ========== NBT 序列化 ==========

    private static final String TAG_ABILITIES = "abilities";
    private static final String TAG_UNLOCKED = "unlocked";
    private static final String TAG_CURRENT = "current";
    private static final String TAG_SW_DIM = "sw_dim";     // 空间扭曲维度（null=未激活）
    private static final String TAG_SW_X = "sw_x";
    private static final String TAG_SW_Y = "sw_y";
    private static final String TAG_SW_Z = "sw_z";
    private static final String TAG_DESC_SENT = "desc_sent";

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (AbilityType type : AbilityType.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", type.getId());
            entry.putBoolean(TAG_UNLOCKED, unlocked[type.ordinal()]);
            list.add(entry);
        }
        tag.put(TAG_ABILITIES, list);
        tag.putInt(TAG_CURRENT, currentOrdinal);

        // 空间扭曲坐标
        if (warpDimension != null) {
            tag.putString(TAG_SW_DIM, warpDimension);
            tag.putDouble(TAG_SW_X, warpX);
            tag.putDouble(TAG_SW_Y, warpY);
            tag.putDouble(TAG_SW_Z, warpZ);
        }

        ListTag descList = new ListTag();
        for (String id : descriptionsSent) {
            net.minecraft.nbt.StringTag st = net.minecraft.nbt.StringTag.valueOf(id);
            descList.add(st);
        }
        tag.put(TAG_DESC_SENT, descList);
        return tag;
    }

    public static PlayerAbilityData deserialize(CompoundTag tag) {
        PlayerAbilityData data = new PlayerAbilityData();

        if (tag.contains(TAG_ABILITIES, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_ABILITIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                AbilityType type = AbilityType.byId(entry.getString("id"));
                if (type != null) {
                    data.unlocked[type.ordinal()] = entry.getBoolean(TAG_UNLOCKED);
                }
            }
        }

        if (tag.contains(TAG_CURRENT)) {
            data.currentOrdinal = tag.getInt(TAG_CURRENT);
        }

        // 空间扭曲坐标恢复
        if (tag.contains(TAG_SW_DIM)) {
            data.warpDimension = tag.getString(TAG_SW_DIM);
            data.warpX = tag.getDouble(TAG_SW_X);
            data.warpY = tag.getDouble(TAG_SW_Y);
            data.warpZ = tag.getDouble(TAG_SW_Z);
        }

        if (tag.contains(TAG_DESC_SENT, Tag.TAG_LIST)) {
            ListTag descList = tag.getList(TAG_DESC_SENT, Tag.TAG_STRING);
            for (int i = 0; i < descList.size(); i++) {
                data.descriptionsSent.add(descList.getString(i));
            }
        }

        return data;
    }
}
