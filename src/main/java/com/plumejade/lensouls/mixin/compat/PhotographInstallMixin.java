package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.util.WeaknessLensPhoto;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 双持右键装弱点透镜照片时，不要打开 Exposure 的照片查看界面。
 * <p>
 * 背景：{@code PhotographItem.use} 在**客户端**直接
 * {@code ClientGUI.openPhotographsScreenFromItem(slot)} 并返回 success。装照片走的正是
 * 「一手照片、另一手附魔摄魂术的物品」右键，如果不管，每次装机都会先弹一张查看照片的界面
 * （服务端那侧已被 {@code WeaknessLensHandler} 取消，客户端这侧只能在这里掐）。
 * <p>
 * 这里返回 {@code success}（而不是 pass）：原版客户端 {@code Minecraft.startUseItem} 的
 * 双手循环在结果 {@code consumesAction()} 时会停下，否则会继续把这次右键转交给另一只手，
 * 让目标物品自己的右键效果被顺带触发一次。
 * <p>
 * 判定复用 {@link WeaknessLensPhoto#isInstallTarget}（与服务端同一个口径）；
 * 注入点在 Exposure 更新导致 {@code use} 签名变化时静默失效（compat 配置 defaultRequire=0），
 * 最坏结果只是「又弹出了照片界面」，装机本身仍由服务端完成。
 */
@Mixin(targets = "io.github.mortuusars.exposure.world.item.PhotographItem", remap = false)
public abstract class PhotographInstallMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true, require = 0)
    private void lensouls$suppressPhotoScreenWhenInstalling(
            Level level, Player player, InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (level == null || !level.isClientSide) return;

        ItemStack photo = player.getItemInHand(hand);
        if (!WeaknessLensPhoto.isWeaknessLensPhoto(photo)) return;

        ItemStack other = hand == InteractionHand.MAIN_HAND
                ? player.getOffhandItem() : player.getMainHandItem();
        if (!WeaknessLensPhoto.isInstallTarget(player, other)) return;

        cir.setReturnValue(InteractionResultHolder.success(photo));
    }
}
