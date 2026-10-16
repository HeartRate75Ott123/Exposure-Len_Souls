package com.plumejade.lensouls.gui;

import com.plumejade.lensouls.photogallery.PhotoGalleryAttachments;
import com.plumejade.lensouls.photogallery.PhotoGalleryData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 照片图鉴菜单：**41 个隐藏的玩家背包槽（坐标在屏幕外）+ 2 条 ContainerData**。
 *
 * <h2>为什么要有这些槽（不能省）</h2>
 * 原版只在「当前打开的菜单」的槽位里同步玩家物品栏。图鉴界面会在开屏状态下改背包
 * （发放照片、清空/剔除退回物品栏），没有这些槽，客户端**看不到**服务端刚放进去的照片。
 * 界面本体全部自绘、不渲染原版槽位，所以坐标扔到 {@code -10000}。
 * <p>
 * 与 {@code FeatherSlotMenu} / {@code ReinforceMenu} 同款结构。
 *
 * <h2>ContainerData</h2>
 * 0 = 点击规则序号，1 = 数据版本号。规则与版本号都是「小状态」，用 ContainerData 自动双端同步，
 * 不必为它写自定义包（见 AGENTS.md 的坑记录）。
 * <b>初值必须在 {@code addDataSlots} 之前设置</b>，否则两端初值不一致。
 */
public class PhotoGalleryMenu extends AbstractContainerMenu {

    public static final int PLAYER_SLOTS = 41;

    private static final int HIDDEN_X = -10000;
    private static final int HIDDEN_Y = -10000;

    private static final int DATA_RULE = 0;
    private static final int DATA_REVISION = 1;

    private final Player player;
    private final ContainerData data = new SimpleContainerData(2);

    public PhotoGalleryMenu(int id, Inventory playerInventory) {
        super(ModMenus.PHOTO_GALLERY.get(), id);
        this.player = playerInventory.player;

        for (int i = 0; i < PLAYER_SLOTS; i++) {
            this.addSlot(new Slot(playerInventory, i, HIDDEN_X, HIDDEN_Y));
        }

        // 初值必须早于 addDataSlots
        PhotoGalleryData gallery = PhotoGalleryAttachments.get(this.player);
        this.data.set(DATA_RULE, gallery.clickRule().ordinal());
        this.data.set(DATA_REVISION, gallery.revision());
        this.addDataSlots(this.data);
    }

    /** 双端可读的当前点击规则（客户端读的就是同步过来的值） */
    public PhotoGalleryData.ClickRule clickRule() {
        int ordinal = this.data.get(DATA_RULE);
        PhotoGalleryData.ClickRule[] values = PhotoGalleryData.ClickRule.values();
        if (ordinal < 0 || ordinal >= values.length) return PhotoGalleryData.ClickRule.GRANT;
        return values[ordinal];
    }

    /** 双端可读的数据版本号（客户端镜像据此判断是否要重算列表） */
    public int revision() {
        return this.data.get(DATA_REVISION);
    }

    /**
     * 服务端每 tick 都会调（以及开屏时 {@code addSlotListener} 调一次）：
     * 把附件里的规则/版本号对齐到 ContainerData，变化随这次广播一起下发。
     */
    @Override
    public void broadcastChanges() {
        if (this.player != null && !this.player.level().isClientSide) {
            PhotoGalleryData gallery = PhotoGalleryAttachments.get(this.player);
            this.data.set(DATA_RULE, gallery.clickRule().ordinal());
            this.data.set(DATA_REVISION, gallery.revision());
        }
        super.broadcastChanges();
    }

    /** 本界面不做物品搬运（槽位只为同步而存在），与 FeatherSlotMenu 同口径 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player != null && player.isAlive() && player == this.player;
    }
}
