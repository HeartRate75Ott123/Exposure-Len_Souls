package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复拍立得创造模式下装相纸复制到副手的问题。
 * <p>
 * 根因：创造模式下 overrideOtherStackedOnMe 的 {@code slot.index} 是创造屏幕的虚拟索引，
 * 不匹配服务端 {@code InventoryMenu} 的槽位索引。
 * {@code handleCreativeModeItemAdd(polaroid, slot.index)} 将偏移后的索引发给服务端
 * → 服务端 {@code InventoryMenu[45]}=副手 → 拍立得被复制到副手。
 * <p>
 * 修复：用 {@link Slot#getContainerSlot()}（真实背包槽位索引）替代 {@code slot.index}。
 * <p>
 * 注意：该方法中唯一读取 {@code Slot.index} 的地方就是 {@code handleCreativeModeItemAdd}。
 * {@code allowModification} 内部也读取了 {@code this.index}，但该分支在创造模式有物品时
 * 因 {@code hasItem()=true} 而短路，不会评估索引值。
 */
@Mixin(
    targets = "io.github.mortuusars.exposure_polaroid.world.item.InstantCameraItem",
    remap = false
)
public class PolaroidFixMixin {

    @Redirect(
        method = "overrideOtherStackedOnMe",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/Slot;index:I", remap = true),
        require = 0
    )
    private int lensouls$fixSlotIndex(Slot slot) {
        return slot.getContainerSlot();
    }
}
