package com.plumejade.lensouls.ability;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * 能力展示顺序（<b>存档级</b>）。
 *
 * <h2>为什么放存档而不是放玩家/相机</h2>
 * 顺序是玩家的排版偏好，不该跟着某台相机走，也不该跟着玩家实体走；
 * 用 {@link SavedData} 存在主世界 {@code data/lensouls_ability_order.dat}，
 * 天然「一个存档一份」——换存档就是另一份数据，不存在跨档污染；
 * 单机切档时 {@code DimensionDataStorage} 也随之换实例，无需清缓存。
 *
 * <h2>与「默认顺序」的关系</h2>
 * 没被玩家拖拽过时（{@link #isCustom()} 为 false）完全沿用旧口径：
 * 已解锁按解锁顺序倒序（新解锁最前），未解锁按枚举序排在后面。
 * 一旦拖拽过，就以这份自定义顺序为准，且<b>未解锁的能力同样算在顺序里</b>
 * （菜单要展示全 8 张卡，轮盘/滚动列表再从中筛出已解锁的）。
 */
public class AbilityOrderData extends SavedData {

    private static final String FILE_ID = "lensouls_ability_order";
    private static final String TAG_ORDER = "order";

    private static final SavedData.Factory<AbilityOrderData> FACTORY =
            new SavedData.Factory<>(AbilityOrderData::new, AbilityOrderData::load);

    /** 自定义顺序（空 = 未自定义，走默认排序） */
    private final List<AbilityType> custom = new ArrayList<>();

    /** 取本存档的顺序数据；拿不到服务器时退回一个临时实例（只读，不落盘） */
    public static AbilityOrderData get(MinecraftServer server) {
        if (server == null) return new AbilityOrderData();
        var overworld = server.overworld();
        if (overworld == null) return new AbilityOrderData();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /** 是否已被玩家自定义过 */
    public boolean isCustom() {
        return !custom.isEmpty();
    }

    /**
     * 生效的展示顺序（覆盖全部 {@link AbilityType}）：自定义优先，缺项按枚举序补在末尾
     * （新版本追加的枚举常量会自动排到最后，不会掉出列表）。
     */
    public List<AbilityType> resolve(Player player) {
        if (custom.isEmpty()) return defaultOrder(player);
        List<AbilityType> order = new ArrayList<>(custom);
        for (AbilityType type : AbilityType.values()) {
            if (!order.contains(type)) order.add(type);
        }
        return order;
    }

    /** 默认顺序：已解锁按解锁顺序倒序（新解锁最前），未解锁按枚举序在后 */
    public static List<AbilityType> defaultOrder(Player player) {
        AbilityType[] values = AbilityType.values();
        List<AbilityType> order = new ArrayList<>(values.length);
        if (player != null) {
            AbilityManager manager = AbilityManager.getInstance();
            List<AbilityType> unlockOrder = manager.getUnlockOrder(player);
            for (int i = unlockOrder.size() - 1; i >= 0; i--) {
                AbilityType type = unlockOrder.get(i);
                if (manager.isUnlocked(player, type) && !order.contains(type)) order.add(type);
            }
            for (AbilityType type : values) {
                if (!manager.isUnlocked(player, type) && !order.contains(type)) order.add(type);
            }
            return order;
        }
        for (AbilityType type : values) order.add(type);
        return order;
    }

    /** 写入自定义顺序：去重、剔除空项、缺项按枚举序补全后落盘 */
    public void setOrder(List<AbilityType> order) {
        List<AbilityType> normalized = new ArrayList<>();
        if (order != null) {
            for (AbilityType type : order) {
                if (type != null && !normalized.contains(type)) normalized.add(type);
            }
        }
        for (AbilityType type : AbilityType.values()) {
            if (!normalized.contains(type)) normalized.add(type);
        }
        custom.clear();
        custom.addAll(normalized);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (AbilityType type : custom) list.add(IntTag.valueOf(type.ordinal()));
        tag.put(TAG_ORDER, list);
        return tag;
    }

    private static AbilityOrderData load(CompoundTag tag, HolderLookup.Provider registries) {
        AbilityOrderData data = new AbilityOrderData();
        if (tag.contains(TAG_ORDER, Tag.TAG_LIST)) {
            AbilityType[] values = AbilityType.values();
            ListTag list = tag.getList(TAG_ORDER, Tag.TAG_INT);
            for (int i = 0; i < list.size(); i++) {
                int ordinal = list.getInt(i);
                if (ordinal >= 0 && ordinal < values.length && !data.custom.contains(values[ordinal])) {
                    data.custom.add(values[ordinal]);
                }
            }
        }
        return data;
    }
}
