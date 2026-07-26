package com.plumejade.lensouls.gui;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import com.plumejade.lensouls.integration.ExposureHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;

/**
 * 摄魂照片 GUI——单槽位容器。
 * <p>
 * 限放入 Exposure 照片物品，读取其中实体 ID 并保存到武器 CustomData。
 */
public class PhotoGuiMenu extends AbstractContainerMenu {

    private static final ResourceLocation PHOTO_ITEM_ID =
            ResourceLocation.parse("exposure:photograph");

    private final Player player;
    private final ItemStack weaponStack;
    private final boolean isServer;
    private final SimpleContainer photoSlot = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (isServer) onPhotoChanged();
        }
    };

    public PhotoGuiMenu(int id, Inventory playerInventory) {
        super(ModMenus.PHOTO_GUI.get(), id);
        this.player = playerInventory.player;
        this.weaponStack = findWeapon(player);
        this.isServer = !player.level().isClientSide();

        // 照片槽
        addSlot(new Slot(photoSlot, 0, 80, 35) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return ExposureHelper.isSwordSlotSuitable(stack);
            }
        });

        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }
        // 快捷栏
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        // 加载已有照片
        loadFromWeapon();
    }

    // ========== 数据持久化 ==========

    private void loadFromWeapon() {
        if (weaponStack.isEmpty()) return;
        CustomData data = weaponStack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        if (tag.contains("SoulPhotoStack")) {
            var access = player.registryAccess();
            photoSlot.setItem(0, ItemStack.parseOptional(access, tag.getCompound("SoulPhotoStack")));
        }
    }

    private void saveToWeapon() {
        if (weaponStack.isEmpty()) return;
        ItemStack photo = photoSlot.getItem(0);
        CompoundTag tag = weaponStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        if (photo.isEmpty()) {
            tag.remove("SoulPhotoStack");
            tag.remove("SoulPhotoEntityId");
        } else {
            var access = player.registryAccess();
            CompoundTag photoTag = new CompoundTag();
            tag.put("SoulPhotoStack", photo.save(access, photoTag));

            // 读取实体 ID
            ResourceLocation entityId = ExposureHelper.getEntityId(photo, access);
            if (entityId != null) {
                tag.putString("SoulPhotoEntityId", entityId.toString());
            } else {
                tag.remove("SoulPhotoEntityId");
            }
        }
        weaponStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private void onPhotoChanged() {
        saveToWeapon();
    }

    /** 检测玩家主手/副手是否有摄魂附魔武器 */
    public static ItemStack findWeapon(Player player) {
        ItemStack main = player.getMainHandItem();
        if (hasEnchantment(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (hasEnchantment(off)) return off;
        return ItemStack.EMPTY;
    }

    private static boolean hasEnchantment(ItemStack stack) {
        return stack.getEnchantmentLevel(
                ModEnchantments.SOUL_PHOTOGRAPHY) > 0;
    }

    // ========== 容器逻辑 ==========

    @Override
    @NotNull
    public ItemStack quickMoveStack(@NotNull Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (slotIndex == 0) {
            // 照片槽 → 背包
            if (!moveItemStackTo(stack, 1, slots.size(), true))
                return ItemStack.EMPTY;
        } else {
            // 背包 → 照片槽
            if (!ExposureHelper.isSwordSlotSuitable(stack)
                    || !moveItemStackTo(stack, 0, 1, false))
                return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return !findWeapon(player).isEmpty();
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (isServer) saveToWeapon();
    }

    public SimpleContainer getPhotoSlot() {
        return photoSlot;
    }

    public static String getEntityId(ItemStack weaponStack) {
        CustomData data = weaponStack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.contains("SoulPhotoEntityId") ? tag.getString("SoulPhotoEntityId") : null;
    }

    /**
     * 检查武器中存储的照片是否可用于剑槽增伤（仅弱点透镜照片有效）。
     * 从武器的 {@code SoulPhotoStack} 反序列化照片后检查能力类型，防止空间扭曲/时空回溯照片混入。
     */
    public static boolean hasSoulDataInWeapon(ItemStack weaponStack, RegistryAccess access) {
        CustomData data = weaponStack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        CompoundTag tag = data.copyTag();
        if (!tag.contains("SoulPhotoStack", net.minecraft.nbt.Tag.TAG_COMPOUND)) return false;
        var opt = ItemStack.parseOptional(access, tag.getCompound("SoulPhotoStack"));
        return !opt.isEmpty() && ExposureHelper.isSwordSlotSuitable(opt);
    }
}
