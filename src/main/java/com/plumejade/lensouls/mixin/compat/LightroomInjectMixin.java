package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.ability.PhotoInjector;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.camera.frame.Photographer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 Lightroom createPrintResult 产出照片时直接打好标记。
 * <p>
 * 不碰 slot、不碰堆叠，只改返回的 ItemStack；出片注入本体委托给
 * {@link PhotoInjector}（与拍立得 {@code PolaroidPrintMixin} 共用同一实现）。
 */
@Mixin(targets = "io.github.mortuusars.exposure.world.block.entity.LightroomBlockEntity", remap = false)
public class LightroomInjectMixin {

    @Inject(method = "createPrintResult", at = @At("RETURN"), require = 0)
    private void lensouls$injectAtCreation(Frame frame,
                                            io.github.mortuusars.exposure.world.lightroom.PrintingProcess process,
                                            CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;
        if (!(result.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem)) return;

        // 暗房不保留拍摄者实体，按帧里记录的摄影师 UUID 反查在线玩家
        PhotoInjector.inject(result, frame, lensouls$resolvePhotographer(frame), false);
    }

    private static ServerPlayer lensouls$resolvePhotographer(Frame frame) {
        Photographer photographer = frame.photographer();
        if (photographer == null || photographer.uuid() == null) return null;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(photographer.uuid());
    }
}
