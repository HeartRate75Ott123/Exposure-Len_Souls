package com.plumejade.lensouls.ability.camera;

import io.github.mortuusars.exposure.network.packet.Packet;
import io.github.mortuusars.exposure.world.camera.Camera;
import io.github.mortuusars.exposure.world.camera.CameraId;
import io.github.mortuusars.exposure.world.entity.CameraHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 饰品槽里的相机作为「活跃相机」（Exposure 的 {@link Camera} 子类）。
 * <p>
 * Exposure 只认 {@code CameraInHand}/{@code CameraOnStand}，取景框、快门、胶卷消耗全都通过
 * {@code getItemStack()} 落到具体那台相机上。这里把 {@code getItemStack()} 指到饰品槽里那份，
 * 于是「凭空打开取景框」不需要把相机挪到手上：消耗照旧核销在同一台相机上，
 * 关闭取景也只是写它的 {@code CAMERA_ACTIVE} 组件。
 */
public class CameraInCurio extends Camera {

    private final String slotId;
    private final int index;

    public CameraInCurio(CameraHolder holder, CameraId id, String slotId, int index) {
        super(holder, id);
        this.slotId = slotId;
        this.index = index;
    }

    public String slotId() {
        return slotId;
    }

    public int index() {
        return index;
    }

    @Override
    public ItemStack getItemStack() {
        if (!(getHolder().asHolderEntity() instanceof Player player)) return ItemStack.EMPTY;
        var handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return ItemStack.EMPTY;
        var stacksHandler = handler.getStacksHandler(slotId).orElse(null);
        if (stacksHandler == null) return ItemStack.EMPTY;
        var stacks = stacksHandler.getStacks();
        if (index < 0 || index >= stacks.getSlots()) return ItemStack.EMPTY;
        return stacks.getStackInSlot(index);
    }

    @Override
    public Packet createSyncPacket() {
        return new CameraCurioSyncPacket(getHolder().asHolderEntity().getId(), getId(), slotId, index);
    }
}
