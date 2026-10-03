package com.plumejade.lensouls.gui;

import com.plumejade.lensouls.feather.FeatherAttachments;
import com.plumejade.lensouls.feather.FeatherSlotContainer;
import com.plumejade.lensouls.feather.FeatherSlotData;
import com.plumejade.lensouls.feather.FeatherSlotLoader;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 羽毛装配菜单：5 个羽毛槽（真实容器）+ 41 个隐藏玩家背包槽（只为同步）。
 * <p>
 * 与 {@code ReinforceMenu} 同款结构：
 * <ul>
 *   <li>界面全部自绘（屏幕是 {@code Screen implements MenuAccess}，不渲染原版槽位），
 *       槽坐标放屏幕外 {@code -10000}；</li>
 *   <li><b>41 个玩家背包槽不能省</b>：原版只在当前打开的菜单槽位里同步玩家物品栏，
 *       没有它们，服务端改背包不会下发到客户端；</li>
 *   <li><b>ContainerData 初值必须在 addDataSlots 之前设置</b>，否则两端初值不一致。</li>
 * </ul>
 * <p>
 * <b>槽位下标</b>：{@code menu.slots} 里 0..4 = 羽毛槽，5..45 = 玩家背包。
 * 选择界面（{@code FeatherSelectScreen}）因此**不按 menu.slots 下标读背包**，
 * 而是直接用 {@code player.getInventory().getItem(i)}（与 {@code ReinforceMenu#getSelectedStack} 同口径）。
 * <p>
 * <b>规则（服务端权威）</b>：
 * <ul>
 *   <li>生存：只能装入**空且未锁定**的槽，装入后该槽<b>永久锁定</b>；</li>
 *   <li>创造：可装入/更换（无视锁定），右键已装槽可卸下（同时解除锁定）；</li>
 *   <li>卸下/更换下来的旧物品：先回背包，满了掉地上——绝不凭空吞掉。</li>
 * </ul>
 */
public class FeatherSlotMenu extends AbstractContainerMenu {

    public static final int FEATHER_SLOTS = FeatherSlotData.SLOTS;
    public static final int PLAYER_SLOTS = 41;
    private static final int HIDDEN_X = -10000;
    private static final int HIDDEN_Y = -10000;

    private final Player player;
    private final FeatherSlotContainer container;
    /** 同步给客户端的锁定标记（下标 0..4 各一） */
    private final ContainerData data = new SimpleContainerData(FEATHER_SLOTS);

    public FeatherSlotMenu(int id, Inventory playerInventory) {
        super(ModMenus.FEATHER.get(), id);
        this.player = playerInventory.player;
        this.container = new FeatherSlotContainer(this.player);

        for (int i = 0; i < FEATHER_SLOTS; i++) {
            this.addSlot(new FeatherSlot(this.container, i, HIDDEN_X, HIDDEN_Y));
        }
        for (int i = 0; i < PLAYER_SLOTS; i++) {
            this.addSlot(new Slot(playerInventory, i, HIDDEN_X, HIDDEN_Y));
        }

        // 初值必须早于 addDataSlots：两端初值一致是 ContainerData 同步的前提
        for (int i = 0; i < FEATHER_SLOTS; i++) {
            this.data.set(i, this.container.data().isLocked(i) ? 1 : 0);
        }
        this.addDataSlots(this.data);
    }

    // ========== 双端查询 ==========

    public boolean isLocked(int slot) {
        return slot >= 0 && slot < FEATHER_SLOTS && this.data.get(slot) != 0;
    }

    public ItemStack featherStack(int slot) {
        return this.container.getItem(slot);
    }

    private boolean isServer() {
        return !this.player.level().isClientSide;
    }

    private boolean creative() {
        return this.player.isCreative();
    }

    /**
     * 客户端点击预检（用于本地提示；服务端仍会二次校验——包可以被伪造）。
     *
     * @return true = 本次点击允许打开选择界面/执行更换
     */
    public boolean canModify(int slot) {
        if (slot < 0 || slot >= FEATHER_SLOTS) return false;
        if (creative()) return true;
        return !isLocked(slot) && featherStack(slot).isEmpty();
    }

    // ========== 服务端权威结算 ==========

    /** 从玩家背包第 {@code inventoryIndex} 格装 1 个羽毛到 {@code slot} */
    public void onInstall(int slot, int inventoryIndex) {
        if (!isServer()) return;
        if (slot < 0 || slot >= FEATHER_SLOTS) return;
        if (inventoryIndex < 0 || inventoryIndex >= PLAYER_SLOTS) return;

        ItemStack source = this.player.getInventory().getItem(inventoryIndex);
        if (!FeatherSlotLoader.isSupported(source)) return;

        boolean creative = creative();
        // 生存：锁定槽、非空槽都不许换（「选定后无法更改」）
        if (!creative && (isLocked(slot) || !featherStack(slot).isEmpty())) return;

        ItemStack replaced = featherStack(slot).copy();
        this.container.setItem(slot, source.copyWithCount(1));
        source.shrink(1);

        if (!creative) {
            // 生存装入 = 永久锁定（写入附件，随存档与死亡重生保留）
            this.container.data().setLocked(slot, true);
        }
        this.data.set(slot, this.container.data().isLocked(slot) ? 1 : 0);
        giveBack(replaced);
        this.broadcastChanges();
    }

    /** 卸下 {@code slot} 的羽毛（仅创造） */
    public void onUninstall(int slot) {
        if (!isServer()) return;
        if (slot < 0 || slot >= FEATHER_SLOTS) return;
        if (!creative()) return;

        ItemStack removed = featherStack(slot).copy();
        if (removed.isEmpty()) return;

        this.container.setItem(slot, ItemStack.EMPTY);
        this.container.data().setLocked(slot, false);
        this.data.set(slot, 0);
        giveBack(removed);
        this.broadcastChanges();
    }

    /** 旧物品去向：优先背包，满了掉地上（不吞物品） */
    private void giveBack(ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!this.player.getInventory().add(stack)) {
            this.player.drop(stack, false);
        }
    }

    // ========== 原版覆写 ==========

    /** 本界面不做物品搬运（槽位只为同步而存在），与 ReinforceMenu 同口径 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * 羽毛槽本身<b>不接受任何直接槽位交互</b>（只能走我们自己的按钮/选择屏）。
     * <p>
     * 关键的防伪造：{@code AbstractContainerMenu.clicked} 处理的是客户端发来的槽位点击包。
     * 若不封掉 {@code mayPlace}/{@code mayPickup}，改包客户端可以直接往槽里塞东西或把
     * 已锁定的羽毛抠出来——「生存装入即永久锁定」会被绕过。槽内容同步不受影响
     * （这两个方法只管交互，不管下发）。
     */
    private static class FeatherSlot extends Slot {

        FeatherSlot(net.minecraft.world.Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player != null && player.isAlive() && player == this.player;
    }
}
