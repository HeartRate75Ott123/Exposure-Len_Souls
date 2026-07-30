package com.plumejade.lensouls.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.plumejade.lensouls.client.phantom.ClientPhantomHandler;
import com.plumejade.lensouls.client.phantom.PhantomVertexConsumer;
import com.plumejade.lensouls.entity.BossPhantomType;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityPhantomMixin {

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void lensouls$wrap(EntityModel<?> m, PoseStack ps, VertexConsumer vc, int li, int ov, int co,
                                Operation<Void> o, LivingEntity e, float y, float pt, PoseStack ps2, MultiBufferSource buf, int li2) {
        if (isPhantom(e)) { phantom(m, ps, buf, e); } else { o.call(m, ps, vc, li, ov, co); }
    }

    private static boolean isPhantom(LivingEntity e) {
        if (!ClientPhantomHandler.isPhantomEntity(e.getId())) return false;
        BossPhantomType type = BossPhantomType.getTypeForClass(e.getClass().getName());
        if (type == null) return false;
        // 旧路径实体（className为空）不走半透明
        if (type.getClassName().isEmpty()) return false;
        // 娜迦/九头蛇/斯库拉：跳过半透明混入（多部件实体或蛇形模型不适配 PhantomVertexConsumer）
        if (type == BossPhantomType.NAGA || type == BossPhantomType.HYDRA || type == BossPhantomType.SCYLLA) return false;
        return true;
    }

    /**
     * 幻灵渲染：使用 PhantomVertexConsumer 包装 VertexConsumer，
     * 确保所有顶点颜色被替换为元素色 × 幻灵透明度。
     * <p>
     * 通过 VertexConsumer 层级的拦截，兼容任何 EntityModel 实现——
     * 即使模型的 renderToBuffer() 丢弃了颜色参数（如 Cloud_GolemModel /
     * TheObliteratorModel 的 root.render(ps, buf, light, overlay) 4-参数调用），
     * 包装器仍能在顶点写入阶段应用正确的半透明元素色。
     */
    @SuppressWarnings("unchecked")
    private static void phantom(EntityModel model, PoseStack ps, MultiBufferSource buf, LivingEntity entity) {
        int a = calcAlpha(entity.tickCount);
        ResourceLocation tex = BossPhantomType.getTextureForClass(entity.getClass().getName());
        int elemColor = BossPhantomType.getColorForClass(entity.getClass().getName());

        VertexConsumer base = buf.getBuffer(RenderType.entityTranslucent(tex));
        VertexConsumer wrapped = new PhantomVertexConsumer(base, elemColor & 0xFFFFFF, a);
        model.renderToBuffer(ps, wrapped, 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }

    private static int calcAlpha(int tick) {
        int fi = 20, fo = 180, m = 153;
        if (tick < fi) return (int)(tick / (float)fi * m);
        if (tick > fo) return (int)((200 - tick) / (float)(200 - fo) * m);
        return m;
    }
}
