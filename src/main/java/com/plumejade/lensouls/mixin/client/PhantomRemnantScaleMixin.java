package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.client.render.PhantomRemnantRenderer;
import com.plumejade.lensouls.entity.PhantomRemnantEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 虚影残像放大的<b>双保险</b>。
 * <p>
 * 背景：{@code EntityRenderDispatcher} 的渲染器表按<b>实体类</b>存放，查不到时会<b>沿父类向上回退</b>。
 * 虚影残像是 {@link ItemEntity} 的子类，因此一旦为它注册的自定义渲染器没有被选中，就会回退到原版
 * {@link ItemEntityRenderer} —— 物品照常绘制、发光（发光是同步标记，与渲染器无关）也照常，
 * 唯独写在自定义渲染器里的缩放完全不参与。实机症状正是「发光的 1x 物品」。
 * <p>
 * 本 mixin 直接挂在原版渲染器上：遇到 {@link PhantomRemnantEntity} 时 HEAD 压栈放大、RETURN 弹栈，
 * 保证无论走哪条渲染路径，放大都一定生效；对普通掉落物不做任何事。
 * <p>
 * 与自定义渲染器<b>不冲突</b>：{@code PhantomRemnantRenderer} 继承 {@code EntityRenderer} 并自行调用
 * {@code ItemRenderer#render}，不会经过 {@code ItemEntityRenderer#render}，两条路径互斥，不存在重复放大。
 */
@Mixin(ItemEntityRenderer.class)
public abstract class PhantomRemnantScaleMixin {

    private static final String RENDER_DESC =
            "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    /** 本帧是否由本 mixin 压过栈（渲染在单一线程上进行，静态标记足够） */
    @Unique
    private static boolean lensouls$scaled = false;

    @Inject(method = RENDER_DESC, at = @At("HEAD"), require = 1)
    private void lensouls$scaleRemnantHead(ItemEntity entity, float entityYaw, float partialTick,
                                           PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                           CallbackInfo ci) {
        if (!(entity instanceof PhantomRemnantEntity)) {
            lensouls$scaled = false;
            return;
        }
        poseStack.pushPose();
        poseStack.scale(PhantomRemnantRenderer.SCALE, PhantomRemnantRenderer.SCALE, PhantomRemnantRenderer.SCALE);
        lensouls$scaled = true;
        com.plumejade.lensouls.LenSouls.LOGGER.debug("[PhantomRemnant] 经原版渲染器放大 x{}", PhantomRemnantRenderer.SCALE);
    }

    @Inject(method = RENDER_DESC, at = @At("RETURN"), require = 1)
    private void lensouls$scaleRemnantReturn(ItemEntity entity, float entityYaw, float partialTick,
                                             PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                             CallbackInfo ci) {
        if (lensouls$scaled) {
            lensouls$scaled = false;
            poseStack.popPose();
        }
    }
}
