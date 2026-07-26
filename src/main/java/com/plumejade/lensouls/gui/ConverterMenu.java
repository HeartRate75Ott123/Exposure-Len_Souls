package com.plumejade.lensouls.gui;

import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.component.SoulCooldownData;
import com.plumejade.lensouls.item.ConverterItem;
import com.plumejade.lensouls.item.LensoulItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;

/**
 * 转换器容器菜单——9 槽镜魂容器 + 玩家背包。
 * <p>
 * 槽位限制：仅允许 {@link LensoulItem} 放入。
 * 容器数据持久化存储于转换器物品的 CustomData 组件中。
 */
public class ConverterMenu extends AbstractContainerMenu {

    private static final int SOUL_COUNT = 9;

    private final Player player;
    private final ItemStack converterStack;
    private final boolean isServer;
    private final SimpleContainer soulContainer;

    public ConverterMenu(int id, Inventory playerInventory, ItemStack converterStack) {
        super(ModMenus.CONVERTER.get(), id);
        this.player = playerInventory.player;
        this.converterStack = converterStack;
        this.isServer = !player.level().isClientSide();

        // ---- 镜魂容器（自动持久化） ----
        this.soulContainer = new SimpleContainer(SOUL_COUNT) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (isServer) saveToStack();
            }
        };
        loadFromStack();

        // ---- 镜魂槽位（3x3 网格） ----
        for (int i = 0; i < SOUL_COUNT; i++) {
            int row = i / 3;
            int col = i % 3;
            addSlot(new Slot(soulContainer, i, 62 + col * 18, 17 + row * 18) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return stack.getItem() instanceof LensoulItem;
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

    // ========== 数据持久化（CustomData 组件） ==========

    private void loadFromStack() {
        if (converterStack.isEmpty()) return;
        CustomData customData = converterStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;

        CompoundTag tag = customData.copyTag();
        if (!tag.contains("ConverterItems", Tag.TAG_LIST)) return;

        ListTag itemsList = tag.getList("ConverterItems", Tag.TAG_COMPOUND);
        var access = player.registryAccess();

        // SoulItemIds / SoulCooldowns 由 G 键写入，saveToStack 不覆盖
        CompoundTag slotUuids = tag.getCompound("SoulItemIds");
        CompoundTag cooldowns = tag.getCompound("SoulCooldowns");

        for (int i = 0; i < itemsList.size(); i++) {
            CompoundTag slotTag = itemsList.getCompound(i);
            int slot = slotTag.getByte("Slot") & 0xFF;
            if (slot >= 0 && slot < SOUL_COUNT) {
                ItemStack loaded = ItemStack.parseOptional(access, slotTag);
                String itemUuid = slotUuids.getString("slot_" + slot);
                if (!loaded.isEmpty() && !itemUuid.isEmpty()
                        && cooldowns.contains(itemUuid, Tag.TAG_COMPOUND)) {
                    CompoundTag cd = cooldowns.getCompound(itemUuid);
                    long end = cd.getLong("end");
                    int dur = cd.getInt("dur");
                    if (end > player.level().getGameTime()) {
                        loaded.set(ModDataComponents.SOUL_COOLDOWN.get(),
                                new SoulCooldownData(end, dur));
                    }
                }
                soulContainer.setItem(slot, loaded);
            }
        }
    }

    private void saveToStack() {
        var access = player.registryAccess();
        CompoundTag tag = converterStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag itemsList = new ListTag();

        // 保留已有的 SoulItemIds（G 键路径写入），只补充新物品的 UUID
        CompoundTag existingUuids = tag.contains("SoulItemIds", Tag.TAG_COMPOUND)
                ? tag.getCompound("SoulItemIds") : new CompoundTag();

        for (int i = 0; i < SOUL_COUNT; i++) {
            ItemStack stack = soulContainer.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putByte("Slot", (byte) i);
                itemsList.add(stack.save(access, slotTag));
                // 保留已有 UUID，仅新物品生成新 UUID
                String slotKey = "slot_" + i;
                if (!existingUuids.contains(slotKey, Tag.TAG_STRING)) {
                    existingUuids.putString(slotKey, LensoulItem.getOrCreateItemId(stack));
                }
            } else {
                // 空槽位：清理对应 UUID 条目以防下次放置不同物品时继承旧冷却
                String slotKey = "slot_" + i;
                existingUuids.remove(slotKey);
            }
        }
        tag.put("ConverterItems", itemsList);
        tag.put("SoulItemIds", existingUuids); // 保留旧映射，不覆盖
        // SoulCooldowns 由 G 键路径写入，此处不覆盖
        converterStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ========== 容器逻辑 ==========

    @Override
    @NotNull
    public ItemStack quickMoveStack(@NotNull Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (slotIndex < SOUL_COUNT) {
            // 镜魂槽 → 玩家背包
            if (!moveItemStackTo(stack, SOUL_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 玩家背包 → 镜魂槽
            if (!(stack.getItem() instanceof LensoulItem)
                    || !moveItemStackTo(stack, 0, SOUL_COUNT, false)) {
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
        return converterStack.getItem() instanceof ConverterItem;
    }

    /**
     * 菜单关闭时确保持久化。
     */
    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (isServer) saveToStack();
    }

    public SimpleContainer getSoulContainer() {
        return soulContainer;
    }
}
