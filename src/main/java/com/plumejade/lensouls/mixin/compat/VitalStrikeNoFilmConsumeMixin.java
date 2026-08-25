package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.CameraAbilityStore;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import io.github.mortuusars.exposure.world.entity.CameraHolder;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import io.github.mortuusars.exposure_polaroid.world.item.InstantCameraItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 防止要害打击消耗胶卷/相纸。
 * <p>
 * 相机 GUI 通过 {@code ActiveCameraReleaseC2SP} C2S 包直接触发服务端
 * {@link CameraItem#release}，绕过 {@code RightClickItem} 事件。
 * 此处直接令 {@code canTakePhoto()} 在 VITAL_STRIKE 时返回 false，
 * {@code release()} 检测到 false 后跳过 {@code takePhoto()}。
 */
@Mixin(value = {CameraItem.class, InstantCameraItem.class})
public abstract class VitalStrikeNoFilmConsumeMixin {

    @Inject(method = "canTakePhoto", at = @At("HEAD"), cancellable = true)
    private void lensouls$preventFilmConsumptionOnVitalStrike(
            CameraHolder holder, ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        Entity entity = holder.asHolderEntity();
        if (!(entity instanceof Player player)) return;

        if (CameraAbilityStore.getSelected(player) != AbilityType.VITAL_STRIKE) return;

        if (ModEnchantments.getSoulPhotographyLevel(player.registryAccess(), stack) <= 0) return;

        cir.setReturnValue(false);
    }
}
