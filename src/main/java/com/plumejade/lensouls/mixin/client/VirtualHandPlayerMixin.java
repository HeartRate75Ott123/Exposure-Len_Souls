package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.camera.CameraInCurio;
import io.github.mortuusars.exposure.world.camera.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 虚拟手持（Player 侧）：{@code Player#getItemBySlot} 覆写了 {@code LivingEntity} 的同名方法，
 * 第三人称 {@code ItemInHandLayer} / 渲染状态抽取会走这个访问器 —— 只改 LivingEntity 拦不到，
 * 所以这里补一份。同 {@link VirtualHandCameraMixin}：仅客户端 LocalPlayer、仅取景中的饰品栏相机。
 */
@Mixin(Player.class)
public abstract class VirtualHandPlayerMixin {

    @Inject(method = "getItemBySlot", at = @At("RETURN"), cancellable = true)
    private void lensouls$virtualBySlot(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot != EquipmentSlot.MAINHAND) return;
        if (!((Object) this instanceof net.minecraft.world.entity.player.Player player)) return;
        if (player.level() == null || !player.level().isClientSide()) return;
        ItemStack stack = com.plumejade.lensouls.client.VirtualHandCache.forPlayer(player);
        if (!stack.isEmpty()) cir.setReturnValue(stack);
    }
}
