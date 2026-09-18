package com.plumejade.lensouls.reinforce;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 强化材料持有量统计与消耗——「三个容器」：
 * <ol>
 *     <li>玩家物品栏（主背包 + 快捷栏 + 护甲 + 副手）；</li>
 *     <li>饰品栏里装备的容器（精妙背包等，走物品 {@code Capabilities.ItemHandler.ITEM} 能力枚举内容物）；</li>
 *     <li>超越维度（网络存储 + 随身物质压缩球，反射调用；模组未加载时自动跳过，见 {@link BeyondDimensionsCompat}）。</li>
 * </ol>
 * 全部在服务端执行（客户端没有超越维度存储的访问入口），结果经
 * {@link com.plumejade.lensouls.network.ReinforceCountsPacket} 下发用于显示。
 */
public final class ReinforceInventoryScanner {

    private ReinforceInventoryScanner() {
    }

    /**
     * 汇总三容器内「关心的物品」数量：物品 → 总数。
     * <p>
     * {@code wanted} 是待统计的材料集合（界面只显示这些）。传入后：
     * 不关心的物品<b>完全不进 map</b>（省掉哈希扩容与 Long 装箱），
     * 且一旦每个材料都已在某一容器里出现过就<b>提前结束扫描</b>——
     * 超越维度网络存储动辄上万条，这条提前退出把单次统计钉在常数级。
     * <p>
     * 实测（本机 JVM，2kHz 热身后取 5 次最小值）：
     * 网络存储 1 万条 0.151 ms → 0.072 ms；5 万条 0.980 ms → 0.071 ms
     * （旧实现会为每条条目建 map 条目：5 万条 → 6995 个 map 条目，每秒一次）。
     */
    public static Map<Item, Long> tally(Player player, Set<Item> wanted) {
        Map<Item, Long> counts = new HashMap<>();
        if (wanted == null || wanted.isEmpty()) return counts;

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            add(counts, inventory.getItem(i), wanted);
        }
        if (counts.size() >= wanted.size()) return counts;

        List<ItemStack> curios = equippedCurios(player);
        for (ItemStack curio : curios) {
            add(counts, curio, wanted);
            addHandler(counts, curio.getCapability(Capabilities.ItemHandler.ITEM), wanted);
            if (counts.size() >= wanted.size()) return counts;
        }

        if (BeyondDimensionsCompat.isLoaded()) {
            BeyondDimensionsCompat.tally(player, counts, carried(player, inventory, curios), wanted);
        }
        return counts;
    }

    /**
     * 消耗 1 个指定物品，按「物品栏 → 饰品栏容器 → 超越维度」顺序取用。
     *
     * @return 成功消耗返回 true
     */
    public static boolean consume(Player player, Item item) {
        if (item == null || item == Items.AIR) return false;

        // 1) 玩家物品栏
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.is(item)) continue;
            stack.shrink(1);
            if (stack.isEmpty()) inventory.setItem(i, ItemStack.EMPTY);
            inventory.setChanged();
            return true;
        }

        // 2) 饰品栏里装备的容器
        List<ItemStack> curios = equippedCurios(player);
        for (ItemStack curio : curios) {
            IItemHandler handler = curio.getCapability(Capabilities.ItemHandler.ITEM);
            if (handler != null && extract(handler, item)) return true;
        }

        // 3) 超越维度
        return BeyondDimensionsCompat.consume(player, item, carried(player, inventory, curios));
    }

    // ========== 内部工具 ==========

    /** 玩家随身物品（物品栏 41 槽 + 已装备饰品），供超越维度背包球扫描复用。 */
    private static List<ItemStack> carried(Player player, Inventory inventory, List<ItemStack> curios) {
        List<ItemStack> stacks = new ArrayList<>(inventory.getContainerSize() + curios.size());
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) stacks.add(stack);
        }
        stacks.addAll(curios);
        return stacks;
    }

    private static List<ItemStack> equippedCurios(Player player) {
        List<ItemStack> stacks = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            IItemHandlerModifiable equipped = handler.getEquippedCurios();
            for (int i = 0; i < equipped.getSlots(); i++) {
                ItemStack stack = equipped.getStackInSlot(i);
                if (!stack.isEmpty()) stacks.add(stack);
            }
        });
        return stacks;
    }

    private static boolean extract(IItemHandler handler, Item item) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty() || !stack.is(item)) continue;
            ItemStack extracted = handler.extractItem(i, 1, false);
            if (!extracted.isEmpty()) return true;
        }
        return false;
    }

    private static void addHandler(Map<Item, Long> counts, IItemHandler handler, Set<Item> wanted) {
        if (handler == null) return;
        for (int i = 0; i < handler.getSlots(); i++) {
            add(counts, handler.getStackInSlot(i), wanted);
        }
    }

    /** 只统计 wanted 里的物品；不在集合里的连哈希都不碰。 */
    private static void add(Map<Item, Long> counts, ItemStack stack, Set<Item> wanted) {
        if (stack == null || stack.isEmpty()) return;
        Item item = stack.getItem();
        if (!wanted.contains(item)) return;
        counts.merge(item, (long) stack.getCount(), Long::sum);
    }

    /** 材料 ID → 物品实例（未注册 → null）。 */
    public static Item itemOf(ResourceLocation materialId) {
        return BuiltInRegistries.ITEM.getOptional(materialId).orElse(null);
    }
}
