package com.plumejade.lensouls.mixin;

import io.github.mortuusars.exposure.world.camera.frame.EntitiesInFrame;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 削韧判定优化：Exposure 曝光帧实体收集时，多部件实体（九头蛇的头、娜迦子节等）
 * 用其子部件位置做视锥判定——拍到头部/子节也算（父实体会进帧实体列表）。
 */
@Mixin(EntitiesInFrame.class)
public abstract class EntitiesInFrameMixin {

    @Redirect(method = "get(Lnet/minecraft/world/entity/Entity;Lio/github/mortuusars/exposure/util/PointOfView;D)Ljava/util/List;",
            at = @At(value = "INVOKE",
                    target = "Lio/github/mortuusars/exposure/world/camera/frame/EntitiesInFrame$FrustumCheck;contains(Lnet/minecraft/world/phys/Vec3;)Z"))
    private static boolean lensouls$containsMultiPart(EntitiesInFrame.FrustumCheck frustum, Vec3 eye,
                                                      Entity entity) {
        if (frustum.contains(eye)) return true;
        // 多部件：任一子部件位置在视锥内 → 父实体算在帧内
        PartEntity<?>[] parts = entity.getParts();
        if (parts != null) {
            for (PartEntity<?> p : parts) {
                if (p != null && p.isAlive() && frustum.contains(p.getEyePosition())) {
                    return true;
                }
            }
        }
        return false;
    }
}
