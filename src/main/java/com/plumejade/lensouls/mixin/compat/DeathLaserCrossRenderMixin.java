package com.plumejade.lensouls.mixin.compat;

import com.github.L_Ender.cataclysm.client.render.entity.Death_Laser_beam_Renderer;
import com.github.L_Ender.cataclysm.entity.projectile.Death_Laser_Beam_Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.client.render.PhotoBeamRenderFlag;
import com.plumejade.lensouls.util.PhotoProjMarker;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 先驱者「死亡激光」照片弹幕：与 {@link AnnihilationBeamCrossRenderMixin} 同一套口径
 * （那边有完整证据链与踩坑记录）——<b>保持原作两张面片，只把退化的滚转角钉成 ±45°</b>。
 * <p>
 * 灾变这份的实情（3.32 实机 jar 字节码，≠ 3.33 源码树）：
 * <ul>
 *   <li>{@code renderBeam} 里 {@code drawBeam} 恰好 2 处（offset 101 / 167）⇒ 两张面片；</li>
 *   <li>两个滚转走 {@code new Quaternionf().rotationY(float)}，在 {@code renderBeam} 里恰好 2 处
 *       ⇒ ordinal 0/1，被改的参数就是唯一那个 float（index 0）；</li>
 *   <li>滚转单位是<b>弧度</b>：第 2 张正确乘了 {@code 0.017453292f}，第 1 张
 *       {@code rotationY(相机俯仰 + 90)} <b>忘了乘</b>（原作 bug，90 rad ≈ 5156°）——
 *       我们把两处都钉成 ±π/4，顺带绕开这个单位 bug；</li>
 *   <li>{@code render()} 不调用 {@code renderStart}（离线核对为 0 处），无「起手光斑贴脸」问题；
 *       光束上的红黄闪电（{@code renderLighting}）与方管/十字叠加，照旧。</li>
 * </ul>
 * 判定只认 {@link PhotoProjMarker#isBarrage}：BOSS 本体的死亡激光角度原样放行。
 */
@Mixin(targets = "com.github.L_Ender.cataclysm.client.render.entity.Death_Laser_beam_Renderer", remap = false)
public class DeathLaserCrossRenderMixin {

    /** 正交十字的滚转角（<b>弧度</b>：{@code Quaternionf.rotationY} 收弧度）。 */
    private static final float CROSS_ROLL_RAD = (float) (Math.PI / 4.0);

    /** 渲染器自己的「第一人称施法者简化视图」标志（私有字段，靠 @Shadow 读写） */
    @Shadow
    private boolean clearerView;

    @Inject(method = "render", require = 1, at = @At(value = "INVOKE",
            target = "Lcom/github/L_Ender/cataclysm/client/render/entity/Death_Laser_beam_Renderer;renderBeam(FFFILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V"))
    private void lensouls$markAndForceFullView(Death_Laser_Beam_Entity beam, float entityYaw, float partialTick,
                                                PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                                CallbackInfo ci) {
        boolean ours = PhotoProjMarker.isBarrage(beam);
        PhotoBeamRenderFlag.set(ours);
        if (ours) {
            // 走「非简化视图」分支：renderBeam 的两次 drawBeam 才会都执行（两张面片都画）
            this.clearerView = false;
            // 同一帧内 renderEnd 随后执行，这里先把爆点该用的帧号算好
            PhotoBeamRenderFlag.setEndFrame(
                    PhotoBeamRenderFlag.impactCapFrame(beam.tickCount, partialTick));
        }
    }

    /**
     * 爆点（射到地面/目标那一头的收尾特效）：与湮灭同源——原作与光束体共用饱和型 {@code appear}
     * {@code ControlledAnimation}，只在前几 tick 变一次、之后整条命都是同一帧。
     * 这里只替换 {@code renderEnd} 的帧号参数，改由 {@code tickCount} 驱动 0→5→0 往返
     * （两张贴图上半区的火花帧都是 0~5，逐像素一致）。
     */
    @ModifyArg(method = "render", require = 1, at = @At(value = "INVOKE",
            target = "Lcom/github/L_Ender/cataclysm/client/render/entity/Death_Laser_beam_Renderer;renderEnd(ILnet/minecraft/core/Direction;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V"),
            index = 0)
    private int lensouls$animateImpactFrame(int frame) {
        return PhotoBeamRenderFlag.get() ? PhotoBeamRenderFlag.endFrame() : frame;
    }

    /** 第 1 张面片的滚转（原作 {@code 俯仰+90}，误当弧度）→ 钉成 +π/4 */
    @ModifyArg(method = "renderBeam", require = 1, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lorg/joml/Quaternionf;rotationY(F)Lorg/joml/Quaternionf;"), index = 0)
    private float lensouls$pinFirstQuadRoll(float radians) {
        return PhotoBeamRenderFlag.get() ? CROSS_ROLL_RAD : radians;
    }

    /** 第 2 张面片的滚转（原作 {@code −(俯仰+90)} 弧度）→ 钉成 −π/4，与第 1 张正交互补 */
    @ModifyArg(method = "renderBeam", require = 1, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lorg/joml/Quaternionf;rotationY(F)Lorg/joml/Quaternionf;"), index = 0)
    private float lensouls$pinSecondQuadRoll(float radians) {
        return PhotoBeamRenderFlag.get() ? -CROSS_ROLL_RAD : radians;
    }
}
