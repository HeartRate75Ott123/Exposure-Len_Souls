package com.plumejade.lensouls.reinforce;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 客户端强化数据缓存（由 {@link com.plumejade.lensouls.network.ReinforceCountsPacket} 填充）。
 * <p>
 * 材料列表本身来自数据包同步（{@link ReinforceDataLoader} 客户端缓存），
 * 数量由服务端权威统计后下发；本类只做缓存与展示辅助。
 */
public final class ReinforceClientData {

    private static volatile int[] counts = new int[0];
    private static volatile int stamp;

    private ReinforceClientData() {
    }

    /** 服务端下发的持有数量（下标 = 材料列表顺序）。 */
    public static void setCounts(int[] newCounts) {
        counts = newCounts == null ? new int[0] : newCounts.clone();
        stamp++;
    }

    /** 数量版本号（界面据此判断是否需要重建显示列表）。 */
    public static int stamp() {
        return stamp;
    }

    public static void clear() {
        counts = new int[0];
        stamp++;
    }

    /** 按材料下标取数量；越界 → 0。 */
    public static int countAt(int index) {
        int[] current = counts;
        if (index < 0 || index >= current.length) return 0;
        return current[index];
    }

    public static int materialCount() {
        return ReinforceDataLoader.getMaterials().size();
    }

    /** 材料对应的物品（未注册 → null）。 */
    public static Item itemOf(ResourceLocation materialId) {
        return BuiltInRegistries.ITEM.getOptional(materialId).orElse(null);
    }

    /** 材料显示名（用于搜索与提示）。 */
    public static String displayName(ResourceLocation materialId) {
        Item item = itemOf(materialId);
        return item == null ? materialId.toString() : item.getDefaultInstance().getHoverName().getString();
    }

    public static List<ReinforceMaterial> materials() {
        return ReinforceDataLoader.getMaterials();
    }
}
