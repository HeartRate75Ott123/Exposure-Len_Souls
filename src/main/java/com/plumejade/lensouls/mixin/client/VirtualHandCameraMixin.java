package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.camera.CameraInCurio;
import io.github.mortuusars.exposure.world.camera.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 饰品栏相机的「虚拟手持」：取景框开着时，客户端把主手读成那台相机的栈。
 * <p>
 * 为什么需要：Exposure 的整套取景/渲染/音效都按「相机在手上」写（{@code CameraInHand.find} 读
 * {@code getItemInHand}、快门滴答声、闪光、自拍判定…）。饰品栏相机没有真实手持物，那些路径就全都
 * 静默退化；补上这一层后，客户端看它就是「拿在手上」，渲染与状态自然正确。
 * <p>
 * 只改客户端，服务端玩家永远读到真实物品栏，不会出现凭空多出手持物。
 * <p>
 * 注意：{@code LivingEntity#getItemBySlot} 是 <b>abstract</b>，Mixin 无法注入（会抛
 * "Scanned 0 target(s)" 让整个游戏起不来），所以那个访问器只在
 * {@link VirtualHandPlayerMixin} 里对 {@code Player} 的具体实现注入。
 */
@Mixin(LivingEntity.class)
public abstract class VirtualHandCameraMixin {

    @Inject(method = "getMainHandItem", at = @At("RETURN"), cancellable = true)
    private void lensouls$virtualMainHand(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack virtual = lensouls$curioCamera();
        if (virtual != null) cir.setReturnValue(virtual);
    }

    @Inject(method = "getItemInHand", at = @At("RETURN"), cancellable = true)
    private void lensouls$virtualItemInHand(InteractionHand hand, CallbackInfoReturnable<ItemStack> cir) {
        if (hand != InteractionHand.MAIN_HAND) return;
        ItemStack virtual = lensouls$curioCamera();
        if (virtual != null) cir.setReturnValue(virtual);
    }

    private ItemStack lensouls$curioCamera() {
        if (!((Object) this instanceof net.minecraft.world.entity.player.Player player)) return null;
        if (player.level() == null || !player.level().isClientSide()) return null;
        ItemStack stack = com.plumejade.lensouls.client.VirtualHandCache.forPlayer(player);
        return stack.isEmpty() ? null : stack;
    }
}
