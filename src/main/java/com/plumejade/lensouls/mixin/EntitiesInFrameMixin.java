package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.util.FrameEntities;
import io.github.mortuusars.exposure.util.PointOfView;
import io.github.mortuusars.exposure.world.camera.frame.EntitiesInFrame;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 画面内实体检索的收口（Exposure {@code EntitiesInFrame.get}）。
 * <p>
 * <b>本 mixin 只做适配：</b>取宿主参数 → 交给 {@link FrameEntities#assemble} 组装 → 原样交回。
 * 全部口径（收窄 / 补回被 Exposure 眼睛预筛刷掉的实体 / 子部件→父实体 / 排序）都在那个普通类里，
 * 便于阅读、加日志与调试。
 *
 * <p><b>为什么是单一 TAIL 注入，而不是 {@code @Redirect} Exposure 的预筛：</b>
 * 旧实现用三个 {@code @Redirect}（{@code FrustumCheck.contains} / {@code calculateVisibleDistance} /
 * {@code hasLineOfSight}）加一个 {@code ThreadLocal} 串联，其中 {@code contains} 的处理器多声明了一个
 * {@code Entity} 参数——它并非"被检测的实体"，而是 Mixin 的隐式参数捕获，拿到的是宿主方法
 * {@code get} 的第一个参数 {@code cameraHolder}。这种隐式签名约束一旦与预期不符就会<b>静默失效</b>
 * （既不报错也不生效，实测表现为"拍了墙后的生物"）。TAIL 注入没有这类签名歧义：
 * 参数就是宿主方法的参数，逻辑全部是可直接阅读、可加日志的普通 Java。**不要再退回 {@code @Redirect}。**
 *
 * <p>弱点透镜 / 能力窃取 / 时间定格 / 韧性削韧都消费本方法的结果。
 */
@Mixin(EntitiesInFrame.class)
public abstract class EntitiesInFrameMixin {

    @Inject(method = "get(Lnet/minecraft/world/entity/Entity;Lio/github/mortuusars/exposure/util/PointOfView;D)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true)
    private static void lensouls$assembleEntitiesInFrame(Entity cameraHolder, PointOfView pov, double fov,
                                                         CallbackInfoReturnable<List<LivingEntity>> cir) {
        cir.setReturnValue(FrameEntities.assemble(cameraHolder, pov, fov, cir.getReturnValue()));
    }
}
