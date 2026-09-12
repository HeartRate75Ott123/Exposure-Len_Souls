package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.util.CameraVisibility;
import io.github.mortuusars.exposure.util.PointOfView;
import io.github.mortuusars.exposure.world.camera.frame.EntitiesInFrame;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 画面内实体检索的收口（Exposure {@code EntitiesInFrame.get}）。
 * <p>
 * <b>设计原则：只收窄，不放宽。</b>本 mixin 在 Exposure 自己的结果（已含视锥 + 焦距距离 +
 * 单点视线）之上，再用 {@link CameraVisibility} 复判一遍：包围盒多采样「遇方块即止」+ 硬射程上限。
 * 因此不可能比原版更宽松，只可能更严格。
 * <p>
 * <b>为什么是单一 TAIL 注入而不是逐点 {@code @Redirect}：</b>
 * 旧实现用三个 {@code @Redirect}（{@code FrustumCheck.contains} / {@code calculateVisibleDistance} /
 * {@code hasLineOfSight}）加一个 {@code ThreadLocal} 串联，其中 {@code contains} 的处理器多声明了一个
 * {@code Entity} 参数——它并非"被检测的实体"，而是 Mixin 的隐式参数捕获，拿到的是宿主方法
 * {@code get} 的第一个参数 {@code cameraHolder}。这种隐式签名约束一旦与预期不符就会**静默失效**
 * （既不报错也不生效，实测表现为"拍了墙后的生物"）。TAIL 注入没有这类签名歧义：
 * 参数就是宿主方法的参数，逻辑全部是可直接阅读、可加日志的普通 Java。
 * <p>
 * 同时保留多部件 BOSS 支持：子部件（九头蛇头/娜迦体节）不是 LivingEntity，会被 Exposure 丢弃，
 * 这里把画面内部件的父实体补进结果列表，使瞄准部件也能拍到本体。
 * <p>
 * 弱点透镜 / 能力窃取 / 时间定格 / 韧性削韧都消费本方法的结果。
 */
@Mixin(EntitiesInFrame.class)
public abstract class EntitiesInFrameMixin {

    /** 与 Exposure 一致：fov 先取 5% 边缘余量 */
    @Unique
    private static final double LENSOULS_FOV_MARGIN = 0.95;

    @Inject(method = "get(Lnet/minecraft/world/entity/Entity;Lio/github/mortuusars/exposure/util/PointOfView;D)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true)
    private static void lensouls$refineEntitiesInFrame(Entity cameraHolder, PointOfView pov, double fov,
                                                       CallbackInfoReturnable<List<LivingEntity>> cir) {
        List<LivingEntity> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        Level level = cameraHolder.level();
        Vec3 camPos = pov.pos();
        Vec3 lookDir = pov.dir();
        // Exposure 的 FrustumCheck 把 fov 当全角用（halfSize = depth * tan(fov / 2)），故半角 = fov / 2
        double halfAngleDeg = (fov * LENSOULS_FOV_MARGIN) / 2.0;
        double maxDistance = CameraVisibility.maxCaptureDistance();

        List<LivingEntity> refined = new ArrayList<>(original.size());
        for (LivingEntity candidate : original) {
            if (candidate == null || !candidate.isAlive()) continue;
            if (CameraVisibility.isVisible(level, camPos, lookDir, halfAngleDeg, maxDistance, candidate)) {
                refined.add(candidate);
            }
        }

        lensouls$addPartParents(level, cameraHolder, camPos, lookDir, halfAngleDeg, maxDistance, refined);

        cir.setReturnValue(refined);
    }

    /**
     * 多部件 BOSS：画面内的子部件追溯到父实体补进结果。
     * 搜索范围按射程上限收窄（而不是无脑 128 格），减少每张照片的实体遍历开销。
     */
    @Unique
    private static void lensouls$addPartParents(Level level, Entity cameraHolder, Vec3 camPos, Vec3 lookDir,
                                                double halfAngleDeg, double maxDistance,
                                                List<LivingEntity> out) {
        double searchRadius = Math.min(maxDistance, 128.0) + 16.0;
        AABB area = new AABB(cameraHolder.blockPosition()).inflate(searchRadius);

        for (Entity entity : level.getEntities(null, area)) {
            if (!(entity instanceof PartEntity<?> part) || !part.isAlive()) continue;
            Entity parent = part.getParent();
            if (!(parent instanceof LivingEntity living) || !living.isAlive()) continue;
            if (out.contains(living)) continue;
            if (!CameraVisibility.isVisible(level, camPos, lookDir, halfAngleDeg, maxDistance, part)) continue;
            out.add(living);
        }
    }
}
