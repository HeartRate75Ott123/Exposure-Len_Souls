package com.plumejade.lensouls.gui;

import com.plumejade.lensouls.reinforce.ReinforceDataLoader;
import com.plumejade.lensouls.reinforce.ReinforceHelper;
import com.plumejade.lensouls.reinforce.ReinforceInventoryScanner;
import com.plumejade.lensouls.reinforce.ReinforceMaterial;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 次元强化菜单（次元锤右键打开）。
 * <p>
 * 界面本体由 {@link ReinforceScreen} 全部自绘，本菜单承担：
 * <ul>
 *     <li><b>同步玩家物品栏</b>：注册 41 个隐藏槽位（坐标在屏幕外，界面不渲染它们）。
 *         没有这些槽位，服务端改动玩家背包物品（强化写入组件）就不会同步给客户端——
 *         原版只在当前打开的菜单槽位里同步玩家背包；</li>
 *     <li><b>选中槽位</b>：{@link ContainerData} 单值（-1 = 未选中），原版自动双端同步；</li>
 *     <li><b>权威结算</b>：选中校验、材料消耗（三容器）、属性写入都在服务端完成；</li>
 *     <li><b>数量下发</b>：三容器材料持有量统计后经 {@code ReinforceCountsPacket} 推给客户端显示。</li>
 * </ul>
 */
public class ReinforceMenu extends AbstractContainerMenu {

    /** 玩家物品栏槽位数（0..35 主背包 + 快捷栏，36..39 护甲，40 副手；与 Inventory 索引一致） */
    public static final int PLAYER_SLOTS = 41;
    /** 未选中 */
    public static final int NO_SELECTION = -1;
    /** 隐藏槽位坐标（屏幕外；界面自绘，不使用原版槽位渲染） */
    private static final int HIDDEN_X = -10000;
    private static final int HIDDEN_Y = -10000;

    private final Player player;
    private final ContainerData data = new SimpleContainerData(1);
    private boolean countsDirty = true;
    private int refreshTimer;
    /** 数量统计一旦抛过异常就停用（避免每 tick 刷日志、也避免把服务端主循环带崩） */
    private boolean countsDisabled;
    /** 材料物品集合缓存（按数据包版本失效） */
    private Set<Item> materialItems;
    private int materialVersion = Integer.MIN_VALUE;
    /** 上次下发的数量（相同则不再发包） */
    private int[] lastSentCounts;

    public ReinforceMenu(int id, Inventory playerInventory) {
        super(ModMenus.REINFORCE.get(), id);
        this.player = playerInventory.player;
        for (int i = 0; i < PLAYER_SLOTS; i++) {
            this.addSlot(new Slot(playerInventory, i, HIDDEN_X, HIDDEN_Y));
        }
        this.data.set(0, NO_SELECTION);
        this.addDataSlots(this.data);
    }

    // ========== 选中状态 ==========

    /** 选中的物品栏槽位（-1 = 未选中）；组件同步，双端一致。 */
    public int getSelectedSlot() {
        return this.data.get(0);
    }

    private void setSelectedSlot(int slot) {
        this.data.set(0, slot);
        this.countsDirty = true;
    }

    /** 选中的物品（未选中/槽位非法 → 空）。 */
    public ItemStack getSelectedStack() {
        int slot = getSelectedSlot();
        if (slot < 0 || slot >= PLAYER_SLOTS) return ItemStack.EMPTY;
        return this.player.getInventory().getItem(slot);
    }

    /** 服务端：处理客户端的选择请求（选中物品栏中某个槽位）。黑名单物品也能选中，只是不能强化。 */
    public void onSelectRequest(int slot) {
        if (this.player.level().isClientSide) return;
        if (slot < 0 || slot >= PLAYER_SLOTS) {
            setSelectedSlot(NO_SELECTION);
            return;
        }
        ItemStack stack = this.player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            setSelectedSlot(NO_SELECTION);
            return;
        }
        setSelectedSlot(slot);
    }

    // ========== 强化结算 ==========

    /** 服务端：处理客户端的一次强化点击。 */
    public void onApplyRequest(ResourceLocation materialId) {
        if (this.player.level().isClientSide) return;
        ReinforceMaterial material = ReinforceDataLoader.getMaterial(materialId);
        if (material == null) return;

        ItemStack target = getSelectedStack();
        if (target.isEmpty()) {
            feedback("gui.lensouls.reinforce.no_target");
            return;
        }
        if (ReinforceDataLoader.isBlacklisted(target)) {
            feedback("gui.lensouls.reinforce.blacklisted");
            return;
        }
        if (ReinforceHelper.isUsed(target, materialId)) {
            feedback("gui.lensouls.reinforce.duplicate");
            return;
        }
        if (!ReinforceHelper.canApply(target, materialId)) {
            feedback("gui.lensouls.reinforce.no_target");
            return;
        }

        Item materialItem = ReinforceInventoryScanner.itemOf(materialId);
        if (materialItem == null) return;
        if (!ReinforceInventoryScanner.consume(this.player, materialItem)) {
            feedback("gui.lensouls.reinforce.no_material");
            return;
        }

        if (!ReinforceHelper.apply(target, materialId)) {
            // 理论不可达（前面已校验），兜底退还材料，避免吞物品
            this.player.getInventory().add(new ItemStack(materialItem));
            feedback("gui.lensouls.reinforce.no_target");
            return;
        }

        this.player.getInventory().setChanged();
        this.countsDirty = true;
        this.player.displayClientMessage(Component.translatable("gui.lensouls.reinforce.done",
                new ItemStack(materialItem).getHoverName()), true);
    }

    private void feedback(String translationKey) {
        this.player.displayClientMessage(Component.translatable(translationKey), true);
    }

    // ========== 同步 ==========

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.player.level().isClientSide) return;
        if (countsDisabled) return;
        if (this.countsDirty || --this.refreshTimer <= 0) {
            this.refreshTimer = 20;
            this.countsDirty = false;
            // 兜底：统计里包含跨模组反射，任何异常都不允许冒泡进服务端主循环（会直接踢掉玩家）
            try {
                sendCounts();
            } catch (Throwable t) {
                countsDisabled = true;
                com.plumejade.lensouls.LenSouls.LOGGER.error(
                        "[Reinforce] 材料数量统计失败，已停用本界面的数量同步（其余功能不受影响）", t);
            }
        }
    }

    /**
     * 三容器材料持有量统计并下发（下标 = 材料列表顺序）。
     * <p>
     * 只统计材料集合里的物品并支持提前退出（详见 {@link ReinforceInventoryScanner#tally}）；
     * 材料集合本身按数据包版本缓存，避免每秒重建。
     */
    private void sendCounts() {
        List<ReinforceMaterial> materials = ReinforceDataLoader.getMaterials();
        Map<Item, Long> tally = ReinforceInventoryScanner.tally(this.player, materialSet());
        int[] counts = new int[materials.size()];
        for (int i = 0; i < materials.size(); i++) {
            Item item = ReinforceInventoryScanner.itemOf(materials.get(i).id());
            Long value = item == null ? null : tally.get(item);
            long amount = value == null ? 0L : value;
            counts[i] = (int) Math.min(Integer.MAX_VALUE, amount);
        }
        if (this.player instanceof ServerPlayer serverPlayer) {
            // 数量没变化就不发包：省掉每秒一次的同步，也省掉客户端重建显示列表
            if (java.util.Arrays.equals(counts, this.lastSentCounts)) return;
            this.lastSentCounts = counts;
            PacketDistributor.sendToPlayer(serverPlayer,
                    new com.plumejade.lensouls.network.ReinforceCountsPacket(counts));
        }
    }

    /** 材料物品集合缓存（数据包 /reload 后 version 变化才重建）。 */
    private Set<Item> materialSet() {
        int version = ReinforceDataLoader.version();
        Set<Item> cached = this.materialItems;
        if (cached != null && this.materialVersion == version) return cached;
        Set<Item> set = new java.util.HashSet<>();
        for (ReinforceMaterial material : ReinforceDataLoader.getMaterials()) {
            Item item = ReinforceInventoryScanner.itemOf(material.id());
            if (item != null) set.add(item);
        }
        this.materialItems = set;
        this.materialVersion = version;
        return set;
    }

    // ========== AbstractContainerMenu ==========

    /** 本界面不做物品搬运（槽位仅为同步而存在）。 */
    @Override
    @NotNull
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.isAlive();
    }

    /** 材料对应的展示物品（供界面复用）。 */
    public static ItemStack displayStack(ReinforceMaterial material) {
        Item item = BuiltInRegistries.ITEM.getOptional(material.id()).orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
