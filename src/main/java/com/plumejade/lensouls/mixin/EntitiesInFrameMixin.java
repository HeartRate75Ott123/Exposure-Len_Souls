package com.plumejade.lensouls.mixin;

import io.github.mortuusars.exposure.world.camera.frame.EntitiesInFrame;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 多部件实体（九头蛇的头、娜迦子节等）子部件→本体追溯：
 * 曝光帧实体收集时，用子部件位置做视锥/距离/视线判定——拍到头部/子节也算（父实体会进帧实体列表）。
 */
@Mixin(EntitiesInFrame.class)
public abstract class EntitiesInFrameMixin {

    @Unique
    private static final ThreadLocal<Entity> lensouls$resolved = new ThreadLocal<>();

    @Redirect(method = "get(Lnet/minecraft/world/entity/Entity;Lio/github/mortuusars/exposure/util/PointOfView;D)Ljava/util/List;",
            at = @At(value = "INVOKE",
                    target = "Lio/github/mortuusars/exposure/world/camera/frame/EntitiesInFrame$FrustumCheck;contains(Lnet/minecraft/world/phys/Vec3;)Z"))
    private static boolean lensouls$containsMultiPart(EntitiesInFrame.FrustumCheck frustum, Vec3 eye,
                                                      Entity entity) {
        if (frustum.contains(eye)) {
            lensouls$resolved.set(entity);
            return true;
        }
        PartEntity<?>[] parts = entity.getParts();
        if (parts != null) {
            for (PartEntity<?> p : parts) {
                if (p != null && p.isAlive() && frustum.contains(p.getEyePosition())) {
                    lensouls$resolved.set(p);
                    return true;
                }
            }
        }
        lensouls$resolved.set(entity);
        return false;
    }

    @Redirect(method = "get(Lnet/minecraft/world/entity/Entity;Lio/github/mortuusars/exposure/util/PointOfView;D)Ljava/util/List;",
            at = @At(value = "INVOKE",
                    target = "Lio/github/mortuusars/exposure/world/camera/frame/EntitiesInFrame;calculateVisibleDistance(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)D"))
    private static double lensouls$visibleDistance(Vec3 cameraPos, Entity entity) {
        Entity target = lensouls$resolved.get();
        if (target == null) target = entity;
        return lensouls$calculateVisibleDistance(cameraPos, target);
    }

    @Redirect(method = "get(Lnet/minecraft/world/entity/Entity;Lio/github/mortuusars/exposure/util/PointOfView;D)Ljava/util/List;",
            at = @At(value = "INVOKE",
                    target = "Lio/github/mortuusars/exposure/world/camera/frame/EntitiesInFrame;hasLineOfSight(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean lensouls$lineOfSight(Vec3 cameraPos, Entity entity) {
        Entity target = lensouls$resolved.get();
        if (target == null) target = entity;
        return lensouls$hasLineOfSight(cameraPos, target);
    }

    @Unique
    private static double lensouls$calculateVisibleDistance(Vec3 cameraPos, Entity entity) {
        double d = Math.sqrt(entity.distanceToSqr(cameraPos));
        double size = entity.getBoundingBoxForCulling().getSize();
        if (Double.isNaN(size) || size == 0.0) size = 0.1;
        double factor = (size - 1.0) * 0.6 + 1.0;
        return (d / factor) * 1.15;
    }

    @Unique
    private static boolean lensouls$hasLineOfSight(Vec3 cameraPos, Entity entity) {
        Level level = entity.level();
        Vec3 eye = entity.getEyePosition();
        ClipContext ctx = new ClipContext(cameraPos, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        BlockHitResult hit = level.clip(ctx);
        return hit.getType() == HitResult.Type.MISS;
    }
}
