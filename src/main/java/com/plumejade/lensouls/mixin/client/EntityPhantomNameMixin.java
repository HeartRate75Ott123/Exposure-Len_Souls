package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.client.phantom.ClientPhantomHandler;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 虚灵实体是半透明外壳叠加，不该显示名字牌。
 * 原生（off-heap）OOM 根因：虚灵实体被设了 customName，且部分 BOSS 渲染器（如 TF Naga）
 * 重写了 shouldShowName 恒显示名字，导致名字牌每帧走
 * EntityRenderer.renderNameTag → ModernUI TextRenderType.makeSDFFillType，
 * 其 SDF 填充 RenderType 创建因固定 Map 抛 UnsupportedOperationException 而无法缓存，
 * 每帧重新分配 SDF 填充 RenderType / ShaderInstance（含原生 GL program + 纹理）且不释放 → 原生泄漏。
 * 这里直接在 renderNameTag 处取消虚灵实体的名字牌渲染（绕开 shouldShowName 重写）。
 */
@Mixin(EntityRenderer.class)
public class EntityPhantomNameMixin {

    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void lensouls$skipPhantomNameTag(Entity entity, Component name, PoseStack poseStack,
                                             MultiBufferSource bufferSource, int packedLight, float unused,
                                             CallbackInfo ci) {
        if (ClientPhantomHandler.isPhantomEntity(entity)) {
            ci.cancel();
        }
    }
}
