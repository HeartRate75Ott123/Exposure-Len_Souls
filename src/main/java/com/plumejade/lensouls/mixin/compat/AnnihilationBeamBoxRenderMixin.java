package com.plumejade.lensouls.mixin.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.plumejade.lensouls.client.render.PhotoBeamRenderFlag;
import com.plumejade.lensouls.util.PhotoProjMarker;
import net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.AnnihilationBeamEntity;
import net.miauczel.legendary_monsters.entity.client.Render.AnnihilationBeamRenderer;
import net.miauczel.legendary_monsters.util.MathUtils;
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
 * 湮灭构造体「湮灭激光」照片弹幕：把光束本体画成<b>信标同款的四面包围方管</b>。
 *
 * <h3>证据链（全部对着实机 jar legendary_monsters-2.1.20 的字节码，源码树只能当参考）</h3>
 * <ul>
 *   <li>光束体 = 若干张「包含光束轴」的面片：{@code drawBeam} 只发 4 个顶点（x∈[-1,1]、y∈[offset,length]、z=0）
 *       = <b>1 张面</b>，{@code renderBeam} 里调它 <b>2 次</b>（offset 94 / 151）。</li>
 *   <li>原版<b>信标</b>光束的「四面包围立方体」是 {@code BeaconRenderer.renderPart} 连画 <b>4 张面</b>
 *       （4 个 renderQuad 围成一圈方管）——这才是用户要的观感；本模组没有这个几何。</li>
 *   <li>两张面（{@code NO_CULL} 双面可见 + 白芯渐变贴图）远看是实心柱，但近看/斜看仍是「十」字截面，
 *       不是封闭方管。</li>
 *   <li>LM 的三个光束渲染器（{@code AnnihilationBeamRenderer} / {@code EnergyBeamRender2d} / …）结构完全相同，
 *       都是 2 张面，别指望在别处找到四面画法。</li>
 * </ul>
 * 所以：给我们标记的弹幕把那 2 张面<b>替换</b>成绕轴 0°/90°/180°/270° 的 <b>4 张面</b>（信标同款方管）；
 * BOSS 本体与官方玩家武器（AtomSplitter）的光束<b>原样放行</b>（判定只认 {@link PhotoProjMarker#isBarrage}）。
 *
 * <h3>实现要点（每个坑都实测踩过）</h3>
 * <ul>
 *   <li><b>{@code @At} target 语法</b>：方法选择器 {@code Lowner;name(args)ret}，<b>无冒号</b>
 *       （javap 显示格式 {@code name:(desc)ret} 带冒号，照抄会整个 mixin apply 失败——1.5.6 事故）。</li>
 *   <li><b>{@code @Redirect} 处理器签名</b>：按 Mixin javadoc 必须在参数最前面<b>加接收者类型</b>
 *       （{@code (AnnihilationBeamRenderer self, float length, int frame, ...)}</b>）——官方例子
 *       {@code barProxy(Foo someObject, int abc, int def)}。</li>
 *   <li><b>{@code @Shadow} 私有方法</b>：带个 {@code throw new AssertionError()} 方法体即可
 *       （shadow 方法根本不会被拷贝进目标，只会重映射调用点；{@code conformVisibility} 只要求
 *       可见性<b>不低于</b>目标，private 对 private 正好）。</li>
 *   <li><b>ordinal 按运行 jar 字节码数</b>：{@code renderBeam} 里 {@code drawBeam} 恰好 2 处
 *       （{@code tools/MixinSelfCheck} 离线核对），ordinal 0 = 第一张面、ordinal 1 = 第二张面。</li>
 *   <li>先抵消调用方刚施加的滚转 {@code ±(相机俯仰+90°)}（LM 用<b>度</b>、灾变第一张是<b>弧度</b>且忘了乘
 *       π/180，是原作 bug），否则方管会随玩家俯仰绕轴自转。</li>
 * </ul>
 */
@Mixin(targets = "net.miauczel.legendary_monsters.entity.client.Render.AnnihilationBeamRenderer", remap = false)
public class AnnihilationBeamBoxRenderMixin {

    /** 渲染器自己的「第一人称施法者简化视图」标志（私有字段，靠 @Shadow 读写） */
    @Shadow
    private boolean clearerView;

    /** 目标私有方法，@Redirect 里重画时调用（方法体不会被拷贝，仅占位） */
    @Shadow
    private void drawBeam(float length, int frame, PoseStack poseStack, VertexConsumer consumer, int packedLight) {
        throw new AssertionError();
    }

    @Inject(method = "render", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/miauczel/legendary_monsters/entity/client/Render/AnnihilationBeamRenderer;renderBeam(FFFILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V"))
    private void lensouls$markOurBeam(AnnihilationBeamEntity beam, float entityYaw, float partialTick,
                                      PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                      CallbackInfo ci) {
        boolean ours = PhotoProjMarker.isBarrage(beam);
        PhotoBeamRenderFlag.set(ours);
        if (ours) {
            // 走「非简化视图」分支：drawBeam 的两次调用才会执行（renderStart 已带着原值跑过 → 起手光斑照旧跳过）
            this.clearerView = false;
        }
    }

    /** 第一张面 → 我们的光束画成绕轴 4 面方管；别人的光束原样放行 */
    @Redirect(method = "renderBeam", require = 1, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/miauczel/legendary_monsters/entity/client/Render/AnnihilationBeamRenderer;drawBeam(FILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V"))
    private void lensouls$drawFourFaceBox(AnnihilationBeamRenderer self, float length, int frame,
                                          PoseStack poseStack, VertexConsumer consumer, int packedLight) {
        if (!PhotoBeamRenderFlag.get()) {
            this.drawBeam(length, frame, poseStack, consumer, packedLight);
            return;
        }
        poseStack.pushPose();
        // 抵消调用方刚施加的滚转（相机俯仰 + 90°，单位=度），方管四面贴合光束自身的上下左右
        poseStack.mulPose(MathUtils.quatFromRotationXYZ(0.0F,
                -(Minecraft.getInstance().gameRenderer.getMainCamera().getXRot() + 90.0F), 0.0F, true));
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

    /** 第二张面 → 我们的光束已经画了完整方管，跳过；别人的光束原样放行 */
    @Redirect(method = "renderBeam", require = 1, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/miauczel/legendary_monsters/entity/client/Render/AnnihilationBeamRenderer;drawBeam(FILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V"))
    private void lensouls$skipSecondQuad(AnnihilationBeamRenderer self, float length, int frame,
                                         PoseStack poseStack, VertexConsumer consumer, int packedLight) {
        if (!PhotoBeamRenderFlag.get()) {
            this.drawBeam(length, frame, poseStack, consumer, packedLight);
        }
    }
}
