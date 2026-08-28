package com.plumejade.lensouls.gui;

import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import com.plumejade.lensouls.item.PhotoAlbumItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.NotNull;

/**
 * 相册容器菜单——9 槽（3×3）照片容器 + 玩家背包。
 * <p>
 * 槽位限制：仅允许带有 {@code lensouls:stolen_entity} 且已注册效果的合法照片放入。
 * 容器数据由标准 {@code DataComponents.CONTAINER}（ItemContainerContents）持久化，与 Tarot 卡组一致。
 */
public class AlbumMenu extends AbstractContainerMenu {

    private static final int SLOTS = 9;

    private final Player player;
    private final ItemStack albumStack;
    private final boolean isServer;
    private final SimpleContainer container;

    public AlbumMenu(int id, Inventory playerInventory, ItemStack albumStack) {
        this(ModMenus.PHOTO_ALBUM.get(), id, playerInventory, albumStack);
    }

    protected AlbumMenu(MenuType<?> type, int id, Inventory playerInventory, ItemStack albumStack) {
        super(type, id);
        this.player = playerInventory.player;
        this.albumStack = albumStack;
        this.isServer = !player.level().isClientSide();

        this.container = new SimpleContainer(SLOTS) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (isServer) saveToStack();
            }
        };
        loadFromStack();

        // ---- 照片槽位（3x3 网格） ----
        for (int i = 0; i < SLOTS; i++) {
            int row = i / 3;
            int col = i % 3;
            addSlot(new Slot(container, i, 62 + col * 18, 17 + row * 18) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    String entityId = PhotographEffectRegistry.getStolenEntity(stack);
                    return entityId != null && PhotographEffectRegistry.hasEffect(entityId);
                }
            });
        }

        // ---- 玩家背包 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }
        // ---- 快捷栏 ----
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    private void loadFromStack() {
        if (albumStack.isEmpty()) return;
        ItemContainerContents contents = albumStack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        contents.copyInto(this.container.getItems());
    }

    private void saveToStack() {
        if (albumStack.isEmpty()) return;
        albumStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.container.getItems()));
    }

    @Override
    @NotNull
    public ItemStack quickMoveStack(@NotNull Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (slotIndex < SLOTS) {
            if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stack, 0, SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return albumStack.getItem() instanceof PhotoAlbumItem;
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (isServer) saveToStack();
    }

    public SimpleContainer getContainer() {
        return container;
    }
}
