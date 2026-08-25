package com.plumejade.lensouls.ability;

import com.plumejade.lensouls.ability.handler.CameraInputHandler;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.camera.CameraId;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/**
 * 相机能力选择存储器。
 * <p>
 * 选中能力不再存于玩家维度，而是直接写入手持相机物品的 {@code CustomData}
 * （键 {@code lensouls:selected_ability}，存能力 ordinal），实现「每台相机独立记忆」。
 * 未选中的相机不写入该键；解锁状态仍由 {@link AbilityManager} 按玩家维护。
 * <p>
 * 跨玩家污染防护：{@link #validate} / {@link #validateAll} 在持有相机时校验所选能力
 * 是否仍被该玩家解锁，未解锁则清除记录（玩家从他人处拾取带记录的相机时自动净化）。
 * 另见 {@link #ensureCameraIds}：相机进入背包即补发稳定 camera_id（已有则跳过）。
 */
public final class CameraAbilityStore {

    private static final String KEY = "lensouls:selected_ability";

    private CameraAbilityStore() {
    }

    // ========== 读取 ==========

    /**
     * 仅按相机物品 NBT 读取所选能力（不校验解锁，越界/非相机返回 null）。
     * 客户端与服务端通用——用于 HUD/菜单/换机播种显示当前相机记录的选中项。
     */
    public static AbilityType getSelectedType(ItemStack cam) {
        if (!CameraInputHandler.isCamera(cam)) return null;
        CustomData data = cam.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(KEY)) return null;
        int ord = tag.getInt(KEY);
        if (ord < 0 || ord >= AbilityType.values().length) return null;
        return AbilityType.values()[ord];
    }

    /** 读取玩家主手持相机的选中能力（校验解锁，未解锁/非相机返回 null）。服务端激活消费用。 */
    public static AbilityType getSelected(Player player) {
        return getSelected(player.getMainHandItem(), player);
    }

    public static AbilityType getSelected(ItemStack cam, Player player) {
        AbilityType type = getSelectedType(cam);
        if (type == null) return null;
        if (player != null && !AbilityManager.getInstance().isUnlocked(player, type)) return null;
        return type;
    }

    // ========== 写入 ==========

    /** 设置相机选中能力（仅当玩家已解锁）。 */
    public static void setSelected(ItemStack cam, AbilityType type, Player player) {
        if (!CameraInputHandler.isCamera(cam)) return;
        if (player != null && !AbilityManager.getInstance().isUnlocked(player, type)) return;
        CompoundTag tag = cam.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(KEY, type.ordinal());
        cam.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (player != null) player.getInventory().setChanged();
    }

    /** 清除相机选中（取消选中）。 */
    public static void clearSelected(ItemStack cam) {
        if (!CameraInputHandler.isCamera(cam)) return;
        CompoundTag tag = cam.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(KEY);
        cam.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ========== 校验（防跨玩家污染） ==========

    /**
     * 校验单台相机：若记录了未解锁/越界的能力则清除。
     *
     * @return 是否做了修正
     */
    public static boolean validate(ItemStack cam, Player player) {
        if (!CameraInputHandler.isCamera(cam)) return false;
        AbilityType type = getSelectedType(cam);
        if (type == null) return false;
        if (!AbilityManager.getInstance().isUnlocked(player, type)) {
            clearSelected(cam);
            return true;
        }
        return false;
    }

    /** 遍历玩家背包所有相机：净化未解锁的选中记录，并确保每台相机持有稳定 camera_id。 */
    public static void validateAll(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        boolean changed = false;
        for (ItemStack stack : sp.getInventory().items) {
            if (validate(stack, sp)) changed = true;
        }
        if (validate(sp.getInventory().offhand.get(0), sp)) changed = true;
        if (ensureCameraIds(sp)) changed = true;
        if (changed) sp.getInventory().setChanged();
    }

    /**
     * 为背包中缺少 {@code exposure:camera_id} 的相机补发唯一标识。
     * <p>
     * Exposure 默认在首次使用（举镜 / 拍照 / 上架）时才惰性生成 camera_id；
     * 这里在相机进入背包后即确保它持有稳定 id（已有则跳过），使「每台相机独立」
     * 的语义从获得相机起即可预测，无需先拍过照。
     * 仅缺失时补发、绝不重生成已有 id —— 因此不会像旧去重那样每秒改动物品导致抽搐。
     *
     * @return 是否做了修正
     */
    public static boolean ensureCameraIds(ServerPlayer sp) {
        Inventory inv = sp.getInventory();
        List<ItemStack> all = new ArrayList<>();
        all.addAll(inv.items);
        all.addAll(inv.armor);
        all.addAll(inv.offhand);

        boolean changed = false;
        for (ItemStack stack : all) {
            if (!CameraInputHandler.isCamera(stack)) continue;
            if (!stack.has(Exposure.DataComponents.CAMERA_ID)) {
                stack.set(Exposure.DataComponents.CAMERA_ID, CameraId.create());
                changed = true;
            }
        }
        return changed;
    }
}
