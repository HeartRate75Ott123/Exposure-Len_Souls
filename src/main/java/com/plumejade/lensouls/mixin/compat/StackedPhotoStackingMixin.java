package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 阻止已注入照片被手动拖入 Exposure StackedPhotographsItem 堆叠（UI 交互反馈）。
 * 入堆时的标记由 PolaroidPrintMixin / LightroomInjectMixin 处理。
 */
@Mixin(targets = "io.github.mortuusars.exposure.world.item.StackedPhotographsItem", remap = false)
public class StackedPhotoStackingMixin {

    private static boolean hasInjected(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("lensouls:injected");
    }

    @Inject(method = "overrideOtherStackedOnMe",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void lensouls$preventStackingIntoExisting(
            ItemStack stack, ItemStack other, Slot slot,
            ClickAction action, Player player, SlotAccess access,
            CallbackInfoReturnable<Boolean> cir) {
        if (hasInjected(other)) {
            cir.setReturnValue(false);
        }
    }
}
