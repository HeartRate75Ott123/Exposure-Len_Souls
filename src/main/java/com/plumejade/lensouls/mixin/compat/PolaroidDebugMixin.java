package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 仅调试：追踪拍立得 overrideOtherStackedOnMe 的调用，不修改任何逻辑。
 */
@Mixin(
    targets = "io.github.mortuusars.exposure_polaroid.world.item.InstantCameraItem",
    remap = false
)
public class PolaroidDebugMixin {

    @Inject(
        method = "overrideOtherStackedOnMe",
        at = @At("HEAD"),
        require = 0
    )
    private void lensouls$logHead(
            ItemStack stack, ItemStack otherStack, Slot slot, ClickAction action,
            Player player, SlotAccess access, CallbackInfoReturnable<Boolean> cir
    ) {
        LenSouls.LOGGER.warn("[PolaroidDebug] ENTER: player={}, slotIdx={}, slotContainer={}, action={}, creative={}, client={}, stackItem={}, otherItem={}, stackCount={}",
                player.getName().getString(),
                slot.index,
                slot.container.getClass().getName(),
                action,
                player.isCreative(),
                player.level().isClientSide(),
                stack.getItem().toString(),
                otherStack.isEmpty() ? "empty" : otherStack.getItem().toString(),
                stack.getCount());
    }

    @Inject(
        method = "overrideOtherStackedOnMe",
        at = @At("RETURN"),
        require = 0
    )
    private void lensouls$logReturn(
            ItemStack stack, ItemStack otherStack, Slot slot, ClickAction action,
            Player player, SlotAccess access, CallbackInfoReturnable<Boolean> cir
    ) {
        LenSouls.LOGGER.warn("[PolaroidDebug] RETURN: player={}, result={}, stackCount={}, otherCount={}",
                player.getName().getString(),
                cir.getReturnValue(),
                stack.getCount(),
                otherStack.getCount());
    }
}
