package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.PhotoInjector;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.CameraHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拍立得 {@code printPhotograph} 出片时注入能力数据。
 * <p>
 * 拦截照片进入背包前的关键调用点：
 * <ul>
 *   <li>{@code Inventory.setItem} — 空格直接塞入</li>
 *   <li>{@code StackedPhotographsItem.addPhotographOnTop} / {@code StackedPhotographs.addPhotographOnTop} — 堆叠</li>
 * </ul>
 * 通过 ThreadLocal 暂存照片引用，RETURN 时从队列取能力并注入。
 */
@Mixin(targets = "io.github.mortuusars.exposure_polaroid.world.item.InstantCameraItem", remap = false)
public class PolaroidPrintMixin {

    @Unique
    private static final ThreadLocal<ItemStack> capturedPhoto = new ThreadLocal<>();

    // ── 空空插槽路径 ──
    @ModifyArg(method = "printPhotograph", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V"), index = 1, require = 0)
    private static ItemStack lensouls$captureSetItem(ItemStack stack) {
        LenSouls.LOGGER.debug("[Polaroid] capture: setItem");
        if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) capturedPhoto.set(stack);
        return stack;
    }

    // ── 堆叠路径（首次和已有都走 StackedPhotographsItem.addPhotographOnTop） ──
    // 注意：不需要 ordinal，因为只有最后一个 addPhotographOnTop 会写入新照片
    @ModifyArg(method = "printPhotograph", at = @At(value = "INVOKE", target = "Lio/github/mortuusars/exposure/world/item/StackedPhotographsItem;addPhotographOnTop(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"), index = 1, require = 0)
    private static ItemStack lensouls$captureStack(ItemStack stack) {
        if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) capturedPhoto.set(stack);
        return stack;
    }

    // ── 背包满时直接丢地上路径 ──
    @ModifyArg(method = "printPhotograph", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"), index = 0, require = 0)
    private static ItemStack lensouls$captureDrop(ItemStack stack) {
        LenSouls.LOGGER.debug("[Polaroid] capture: drop");
        if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) capturedPhoto.set(stack);
        return stack;
    }

    @Inject(method = "printPhotograph", at = @At("RETURN"), require = 0)
    private static void lensouls$inject(
            CameraHolder holder, ItemStack cameraStack, Frame frame, CallbackInfo ci) {
        ItemStack photo = capturedPhoto.get();
        capturedPhoto.remove();

        if (!(holder.asHolderEntity() instanceof ServerPlayer player)) return;

        // 出片注入统一走 PhotoInjector（与暗房共用同一实现）
        PhotoInjector.inject(photo, frame, player, true);
    }

}
