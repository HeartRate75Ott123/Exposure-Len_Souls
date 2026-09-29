package com.plumejade.lensouls.mixin.compat;

import com.github.L_Ender.cataclysm.entity.projectile.Death_Laser_Beam_Entity;
import com.github.L_Ender.cataclysm.client.render.entity.Death_Laser_beam_Renderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.plumejade.lensouls.client.render.PhotoBeamRenderFlag;
import com.plumejade.lensouls.util.PhotoProjMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 先驱者「死亡激光」照片弹幕：把光束本体画成<b>信标同款的四面包围方管</b>。
 * <p>
 * 与 {@link AnnihilationBeamBoxRenderMixin} 同一套修法（那边有完整证据链与踩坑记录）。灾变这份的实情
 * （3.32 实机 jar 字节码，≠ 3.33 源码）：
 * <ul>
 *   <li>{@code drawBeam(FILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V}
 *       在 {@code renderBeam} 里恰好 2 处（offset 101 / 167）⇒ ordinal 0 / 1。</li>
 *   <li>两张面的滚转是 {@code new Quaternionf().rotationY(float)}（<b>弧度</b>）：第一张传
 *       {@code (相机俯仰 + 90)} <b>忘了乘 π/180</b>（原作 bug），第二张才是正确弧度。
 *       抵消时按<b>弧度</b>逆运算。</li>
 *   <li>{@code render()} 不调用 {@code renderStart}（离线核对为 0 处），无「起手光斑贴脸」问题；
 *       光束上的红黄闪电（{@code renderLighting}，{@code tickCount > 20} 才画）照旧，与方管叠加。</li>
 * </ul>
 * 判定只认 {@link PhotoProjMarker#isBarrage}；BOSS 本体的死亡激光原样放行。
 */
@Mixin(targets = "com.github.L_Ender.cataclysm.client.render.entity.Death_Laser_beam_Renderer", remap = false)
public class DeathLaserBoxRenderMixin {

    /** 渲染器自己的「第一人称施法者简化视图」标志（私有字段，靠 @Shadow 读写） */
    @Shadow
    private boolean clearerView;

    /** 目标私有方法，@Redirect 里重画时调用（方法体不会被拷贝，仅占位） */
    @Shadow
    private void drawBeam(float length, int frame, PoseStack poseStack, VertexConsumer consumer, int packedLight) {
        throw new AssertionError();
    }

    @Inject(method = "render", require = 1, at = @At(value = "INVOKE",
            target = "Lcom/github/L_Ender/cataclysm/client/render/entity/Death_Laser_beam_Renderer;renderBeam(FFFILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V"))
    private void lensouls$markOurBeam(Death_Laser_Beam_Entity beam, float entityYaw, float partialTick,
                                      PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                      CallbackInfo ci) {
        boolean ours = PhotoProjMarker.isBarrage(beam);
        PhotoBeamRenderFlag.set(ours);
        if (ours) {
            this.clearerView = false;
        }
    }

    /** 第一张面 → 我们的光束画成绕轴 4 面方管；BOSS 本体的死亡激光原样放行 */
    @Redirect(method = "renderBeam", require = 1, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/github/L_Ender/cataclysm/client/render/entity/Death_Laser_beam_Renderer;drawBeam(FILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V"))
    private void lensouls$drawFourFaceBox(Death_Laser_beam_Renderer self, float length, int frame,
                                          PoseStack poseStack, VertexConsumer consumer, int packedLight) {
        if (!PhotoBeamRenderFlag.get()) {
            this.drawBeam(length, frame, poseStack, consumer, packedLight);
            return;
        }
        poseStack.pushPose();
        // 抵消调用方刚施加的滚转（相机俯仰 + 90，单位=弧度——灾变这里忘了乘 π/180）
        poseStack.mulPose(new Quaternionf()
                .rotationY(-(Minecraft.getInstance().gameRenderer.getMainCamera().getXRot() + 90.0F)));
        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            if (i > 0) {
                poseStack.mulPose(new Quaternionf().rotationY((float) (i * Math.PI / 2.0)));
            }
            this.drawBeam(length, frame, poseStack, consumer, packedLight);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** 第二张面 → 我们的光束已经画了完整方管，跳过；BOSS 本体的死亡激光原样放行 */
    @Redirect(method = "renderBeam", require = 1, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/github/L_Ender/cataclysm/client/render/entity/Death_Laser_beam_Renderer;drawBeam(FILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V"))
    private void lensouls$skipSecondQuad(Death_Laser_beam_Renderer self, float length, int frame,
                                         PoseStack poseStack, VertexConsumer consumer, int packedLight) {
        if (!PhotoBeamRenderFlag.get()) {
            this.drawBeam(length, frame, poseStack, consumer, packedLight);
        }
    }
}
