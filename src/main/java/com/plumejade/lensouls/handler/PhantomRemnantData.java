package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 虚影残像的数据层：捕获玩家全部物品/经验 → 写进容器物品；以及按**原槽位**归还。
 * <p>
 * 复刻自 Enigmatic Legacy 的 Extradimensional Vessel（1.20.X 源码），并按 1.21.1 与需求调整：
 * <ul>
 *   <li><b>1.21.1 适配</b>：EL 用 {@code ItemStack.serializeNBT()} 存子物品（该方法已移除）。
 *       这里改用原版 {@link DataComponents#CONTAINER}（{@link ItemContainerContents}）承载物品本体
 *       —— 序列化交给原版自己的组件编解码器，不需要手动碰 {@code NbtOps}/注册表上下文；
 *       槽位归属另存为与物品<b>同序</b>的字符串表（{@code inv:12} / {@code curio:ring:1} / {@code drop}）。</li>
 *   <li><b>槽位精确归位</b>：EL 只用 {@code inventory.add()} 随便塞；这里优先放回<b>原槽位</b>
 *       （含 Curios 槽位），原槽位被占用才退回背包，再放不下才掉在原地 —— 对应
 *       「自动回归当时的物品栏，饰品等的状态」。</li>
 *   <li><b>经验全部收走</b>（EL 只存一部分）。</li>
 * </ul>
 */
public final class PhantomRemnantData {

    private static final String TAG_XP = "lensouls:remnant_xp";
    private static final String TAG_LEVEL = "lensouls:remnant_level";
    private static final String TAG_PROGRESS = "lensouls:remnant_progress";
    private static final String TAG_SLOTS = "lensouls:remnant_slots";
    private static final String TAG_OWNER = "lensouls:remnant_owner";
    private static final String TAG_PRESENT = "lensouls:remnant_present";

    /** 原版 ItemContainerContents 的容量上限 */
    public static final int MAX_STORED = 256;

    private PhantomRemnantData() {
    }

    // ==================== 槽位表读写 ====================

    private static List<String> readSlots(ItemStack remnant) {
        List<String> slots = new ArrayList<>();
        CustomData data = remnant.get(DataComponents.CUSTOM_DATA);
        if (data == null) return slots;
        ListTag list = data.copyTag().getList(TAG_SLOTS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) slots.add(list.getString(i));
        return slots;
    }

    private static void writeSlots(ItemStack remnant, List<String> slots) {
        CustomData data = remnant.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        ListTag list = new ListTag();
        for (String slot : slots) list.add(StringTag.valueOf(slot));
        tag.put(TAG_SLOTS, list);
        remnant.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static List<ItemStack> readItems(ItemStack remnant) {
        // 1.21.1 的 nonEmptyItems() 返回 Iterable（顺序与写入一致）
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stored : remnant.getOrDefault(DataComponents.CONTAINER,
                ItemContainerContents.EMPTY).nonEmptyItems()) {
            items.add(stored);
        }
        return items;
    }

    // ==================== 捕获 ====================

    /**
     * 把玩家的全部物品与全部经验打包成一个虚影残像物品。
     * <p>
     * 物品来源有三处，互不重叠（Curios 掉落时会<b>先把物品从槽位取出</b>，
     * 所以「槽位还有的」与「掉落列表里有的」不可能同时命中同一个物品）：
     * <ol>
     *   <li>玩家 Inventory（1.21 的 {@code Inventory} 已包含主背包 0-35、盔甲 36-39、副手 40）；</li>
     *   <li>Curios 全部槽位（记录槽位标识符 + 下标，用于归位）；</li>
     *   <li>{@code LivingDropsEvent} 的掉落列表（Curios 已先行丢出的饰品等）。</li>
     * </ol>
     *
     * @param extraDrops 死亡掉落列表（可为 null）
     */
    public static ItemStack capture(ServerPlayer player, Collection<ItemEntity> extraDrops) {
        List<ItemStack> items = new ArrayList<>();
        List<String> slots = new ArrayList<>();

        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            items.add(stack.copy());
            slots.add("inv:" + i);
            inv.setItem(i, ItemStack.EMPTY);
        }

        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var entry : handler.getCurios().entrySet()) {
                var stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    // 虚影核心自身「死亡不掉落」（ALWAYS_KEEP）：不搬进容器、也不清空该槽位，
                    // 否则 Curios 的保留规则会被这里的清空动作抵消。
                    if (stack.is(ModItems.PHANTOM_CORE.get())) continue;
                    items.add(stack.copy());
                    slots.add("curio:" + entry.getKey() + ":" + i);
                    stacks.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        });

        if (extraDrops != null) {
            for (ItemEntity drop : extraDrops) {
                ItemStack stack = drop.getItem();
                if (stack.isEmpty()) continue;
                items.add(stack.copy());
                slots.add("drop");
                drop.setItem(ItemStack.EMPTY);
            }
        }

        ItemStack remnant = new ItemStack(ModItems.PHANTOM_REMNANT.get());
        remnant.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));

        CompoundTag tag = new CompoundTag();
        ListTag slotList = new ListTag();
        for (String slot : slots) slotList.add(StringTag.valueOf(slot));
        tag.put(TAG_SLOTS, slotList);
        // 经验：以「等级 + 当前级进度」为权威（玩家看到的就是等级），总额备用
        tag.putInt(TAG_LEVEL, player.experienceLevel);
        tag.putFloat(TAG_PROGRESS, player.experienceProgress);
        tag.putInt(TAG_XP, player.totalExperience);
        tag.putUUID(TAG_OWNER, player.getUUID());
        tag.putBoolean(TAG_PRESENT, true);
        remnant.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        // 经验全部收进容器
        int storedXp = player.totalExperience;
        player.totalExperience = 0;
        player.experienceLevel = 0;
        player.experienceProgress = 0.0F;

        LenSouls.LOGGER.info("[PhantomRemnant] 缓存 {} 件物品 / 等级 {} (+{}%) / totalXP {}",
                items.size(), tag.getInt(TAG_LEVEL), tag.getFloat(TAG_PROGRESS), storedXp);

        return remnant;
    }

    // ==================== 归还 ====================

    /**
     * 把容器内容按原槽位归还给玩家。
     *
     * @return 是否确实归还了一次（false = 这是个空容器/已被领取过）
     */
    public static boolean restore(ServerPlayer player, ItemStack remnant) {
        CustomData data = remnant.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        CompoundTag tag = data.copyTag();
        if (!tag.getBoolean(TAG_PRESENT)) return false;

        List<ItemStack> items = readItems(remnant);
        List<String> slots = readSlots(remnant);

        // ── 1) 先发经验 ──
        // 必须放在物品循环之前：万一某件物品归还时抛异常，经验也不会被整段跳过
        //（这正是上一版「物品回来了、经验没回来」最可能的机制）。
        int level = tag.getInt(TAG_LEVEL);
        float progress = tag.getFloat(TAG_PROGRESS);
        int xp = xpForLevel(level, progress);
        if (xp <= 0) xp = tag.getInt(TAG_XP);   // 等级为 0 时退回总额备用值
        if (xp > 0) {
            try {
                player.giveExperiencePoints(xp);
            } catch (Exception e) {
                LenSouls.LOGGER.error("[PhantomRemnant] 经验发放失败（{} 点）", xp, e);
            }
        }

        // ── 2) 按原槽位归还物品；逐件兜底，单件失败不影响其余 ──
        Inventory inv = player.getInventory();
        int failed = 0;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            String slot = i < slots.size() ? slots.get(i) : "";
            try {
                if (!place(player, inv, slot, stack) && !stack.isEmpty()) {
                    // 背包与槽位都放不下 → 掉在原地（Inventory.add 可能已塞入一部分）
                    ItemEntity drop = new ItemEntity(player.level(),
                            player.getX(), player.getY() + 0.5, player.getZ(), stack);
                    player.level().addFreshEntity(drop);
                }
            } catch (Exception e) {
                failed++;
                LenSouls.LOGGER.warn("[PhantomRemnant] 第 {} 件物品归位失败，掉在原地", i, e);
                if (!stack.isEmpty()) {
                    ItemEntity drop = new ItemEntity(player.level(),
                            player.getX(), player.getY() + 0.5, player.getZ(), stack);
                    player.level().addFreshEntity(drop);
                }
            }
        }

        // ── 3) 清空载荷，避免二次领取 ──
        CompoundTag cleared = tag.copy();
        cleared.putBoolean(TAG_PRESENT, false);
        cleared.remove(TAG_SLOTS);
        cleared.putInt(TAG_XP, 0);
        cleared.putInt(TAG_LEVEL, 0);
        cleared.putFloat(TAG_PROGRESS, 0.0F);
        remnant.set(DataComponents.CUSTOM_DATA, CustomData.of(cleared));
        remnant.remove(DataComponents.CONTAINER);

        LenSouls.LOGGER.info("[PhantomRemnant] 归还 {} 件物品（失败 {}）/ {} 点经验（缓存等级 {}+{}%）",
                items.size(), failed, xp, level, progress);
        return true;
    }

    // ==================== 经验换算 ====================

    /**
     * 等级 + 当前级进度 → 经验总量。
     * <p>
     * 曲线常数与 {@code Player.getXpNeededForNextLevel()} 的字节码一致
     * （{@code ≥30: 112+(L-30)*9}、{@code ≥15: 37+(L-15)*5}、否则 {@code 7+2L}），
     * 累计量用原版经典公式；自检：16 级=352、30 级=1395、50 级=5345。
     */
    public static int xpForLevel(int level, float progress) {
        if (level < 0) level = 0;
        int total = totalXpToReach(level);
        if (progress > 0.0F) {
            float p = Math.min(1.0F, progress);
            total += Math.round(p * xpNeededForNext(level));
        }
        return total;
    }

    /** 从 0 级升到 level 级所需的累计经验 */
    private static int totalXpToReach(int level) {
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    /** 从 level 级升到下一级所需经验 */
    private static int xpNeededForNext(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    /**
     * 优先放回原槽位；原槽位被新拾取的东西占用时，退回该容器内的空位，最后才交给调用方掉在原地。
     */
    private static boolean place(ServerPlayer player, Inventory inv, String slot, ItemStack stack) {
        if (slot != null && slot.startsWith("inv:")) {
            int idx = parseInt(slot.substring(4));
            if (idx >= 0 && idx < inv.getContainerSize() && inv.getItem(idx).isEmpty()) {
                inv.setItem(idx, stack);
                return true;
            }
        } else if (slot != null && slot.startsWith("curio:")) {
            String[] parts = slot.split(":");
            if (parts.length >= 3) {
                int idx = parseInt(parts[2]);
                var curios = CuriosApi.getCuriosInventory(player);
                if (curios.isPresent()) {
                    var handler = curios.get().getCurios().get(parts[1]);
                    if (handler != null) {
                        var stacks = handler.getStacks();
                        if (idx >= 0 && idx < stacks.getSlots() && stacks.getStackInSlot(idx).isEmpty()) {
                            stacks.setStackInSlot(idx, stack);
                            return true;
                        }
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            if (stacks.getStackInSlot(i).isEmpty()) {
                                stacks.setStackInSlot(i, stack);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return inv.add(stack);
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ==================== 追加（兜底） ====================

    /**
     * 把额外掉落并入<b>已有</b>的容器（用于 {@code LivingDropsEvent} 兜底：
     * 其它模组在本模组打包之后才往掉落列表里塞东西，例如 Curios 的独立掉落实现）。
     *
     * @return 实际并入的物品数量；超过原版容器上限的部分留在原地（返回未并入数）
     */
    public static int append(ItemStack remnant, Collection<ItemEntity> extraDrops) {
        if (remnant == null || extraDrops == null || extraDrops.isEmpty()) return 0;
        if (!hasPayload(remnant)) return 0;

        List<ItemStack> items = readItems(remnant);
        List<String> slots = readSlots(remnant);
        int added = 0;
        for (ItemEntity drop : extraDrops) {
            ItemStack stack = drop.getItem();
            if (stack.isEmpty()) continue;
            if (items.size() >= MAX_STORED) break;
            items.add(stack.copy());
            slots.add("drop");
            drop.setItem(ItemStack.EMPTY);
            added++;
        }
        if (added == 0) return 0;

        remnant.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        writeSlots(remnant, slots);
        return added;
    }

    // ==================== 查询 ====================

    /** 是否还装着东西（空容器没必要弹提示/归还） */
    public static boolean hasPayload(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(TAG_PRESENT);
    }

    /** 该容器的所有者（仅其本人可拾取） */
    public static UUID getOwner(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }
}
