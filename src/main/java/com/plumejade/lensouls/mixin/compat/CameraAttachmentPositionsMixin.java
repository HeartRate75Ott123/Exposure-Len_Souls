package com.plumejade.lensouls.mixin.compat;

import io.github.mortuusars.exposure.world.inventory.AbstractCameraAttachmentsMenu;
import io.github.mortuusars.exposure.world.item.camera.Attachment;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * 修拍立得配件界面的 NPE。
 * <p>
 * Exposure 的 {@code AbstractCameraAttachmentsMenu.addAttachmentSlots} 把槽位坐标写死成
 * FILM/FLASH/LENS/FILTER 四项的 {@code Map.of(...)}，查不到就给 {@code null}，紧接着调
 * {@code pos.x()} → 拍立得的 {@code InstantCameraAttachment.INSTANT_SLIDE} 不在表里，
 * 于是打开面板必崩（服务端 {@code NullPointerException: pos is null}，客户端什么都不显示）。
 * <p>
 * 这里只兜「查不到」这一种情况：给未登记的附件一个默认坐标（相机面板左下那个胶片位），
 * 原版四项仍然走它们自己的坐标。
 */
@Mixin(AbstractCameraAttachmentsMenu.class)
public class CameraAttachmentPositionsMixin {

    @Redirect(method = "addAttachmentSlots",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            remap = false)
    private Object lensouls$fallbackPosition(Map<Attachment<?>, Vector2i> positions, Object key) {
        Object pos = positions.get(key);
        return pos != null ? pos : new Vector2i(13, 42);
    }
}
