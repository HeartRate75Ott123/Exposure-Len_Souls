package com.plumejade.lensouls.mixin.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.client.render.PhotoBeamRenderFlag;
import com.plumejade.lensouls.util.PhotoProjMarker;
import net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.AnnihilationBeamEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 湮灭构造体「湮灭激光」照片弹幕：<b>保持原作的两张面片</b>，只把「十字只在特定俯仰角才成立」的退化修掉。
 *
 * <h3>原作的几何（实机 jar legendary_monsters-2.1.20 字节码逐条核对，不是读源码猜）</h3>
 * <ul>
 *   <li>{@code renderBeam} 里 {@code drawBeam} 恰好 2 处 ⇒ 光束本体就是 <b>2 张面片</b>
 *       （每张 {@code drawBeam} 只发 4 顶点）；</li>
 *   <li>两张面片的滚转角分别是 {@code +(相机俯仰+90°)} 与 {@code −(相机俯仰+90°)}
 *       （{@code MathUtils.quatFromRotationXYZ} 在 {@code renderBeam} 里共 5 处调用：前 3 处是基础位姿，
 *       第 4/5 处即这两个滚转 = ordinal 3/4，角度都是 <b>第 2 个参数（index 1）</b>）；</li>
 *   <li>两面片夹角 = 2×滚转角：俯仰 0° → <b>0°（完全重合，即「纸片」）</b>、−45° → 90°、
 *       90° → 0°（又重合）⇒ <b>原作这套角度本身就是退化的</b>，平视/俯视看都是一张；
 *   <li>{@code clearerView}（= 施法者是本地玩家且第一人称）为 true 时：第 1 张不滚转、第 2 张整段跳过
 *       ⇒ 只剩 1 张。</li>
 * </ul>
 *
 * <h3>我们的口径（用户定案：两片，但修掉平视退化）</h3>
 * <ol>
 *   <li>{@code @Inject} 在 {@code render} 调 {@code renderBeam} 之前：打标（{@link PhotoBeamRenderFlag}，
 *       {@code @ModifyArg} 那边拿不到实体）并给我们的弹幕把 {@code clearerView} 置 false —— 两张面片
 *       都要画出来；位置放在这里而不是 {@code render} 开头，是因为 {@code renderStart} 已带着原值跑过
 *       ⇒ 起手那个贴脸光斑照旧跳过；</li>
 *   <li>用 {@code @ModifyArg} 把两个滚转角<b>钉成 ±45°</b>：这正是原作在「相机俯仰 −45°」时
 *       才成立的理想夹角，钉死后平视/抬头/低头都是正交十字，且<b>只改角度、不改面片数量</b>。</li>
 * </ol>
 * 判定只认 {@link PhotoProjMarker#isBarrage}：BOSS 本体与官方玩家武器（AtomSplitter）的光束角度原样放行。
 *
 * <h3>踩坑备忘</h3>
 * <ul>
 *   <li>{@code @At} target 的方法选择器是 {@code Lowner;name(args)ret}，<b>没有冒号</b>
 *       （javap 输出里那个冒号照抄会让整个 mixin apply 失败——1.5.6 事故）；</li>
 *   <li>{@code @ModifyArg} 的处理器签名<b>只有被改的那个参数</b>（不像 {@code @Redirect} 要加接收者）；</li>
 *   <li>ordinal 按<b>运行 jar 字节码</b>数，源码树版本可能与 jar 不一致。</li>
 * </ul>
 */
@Mixin(targets = "net.miauczel.legendary_monsters.entity.client.Render.AnnihilationBeamRenderer", remap = false)
public class AnnihilationBeamCrossRenderMixin {

    /** 正交十字的滚转角：两张面片各 ±45° ⇒ 夹角恰好 90°（原文只在相机俯仰 −45° 时才是这个值）。 */
    private static final float CROSS_ROLL_DEG = 45.0F;

    /** 渲染器自己的「第一人称施法者简化视图」标志（私有字段，靠 @Shadow 读写） */
    @Shadow
    private boolean clearerView;

    @Inject(method = "render", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/miauczel/legendary_monsters/entity/client/Render/AnnihilationBeamRenderer;renderBeam(FFFILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V"))
    private void lensouls$markAndForceFullView(AnnihilationBeamEntity beam, float entityYaw, float partialTick,
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
     * 爆点（射到地面/目标那一头的收尾特效）：原作与光束体共用 {@code appear} 这个饱和型
     * {@code ControlledAnim}，只在前 3 tick 变一次、之后整条命都是同一帧（用户：「只有消失时有动画，很丑」）。
     * 这里只替换 {@code renderEnd} 的帧号参数，改由 {@code tickCount} 驱动 0→5→0 往返。
     */
    @ModifyArg(method = "render", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/miauczel/legendary_monsters/entity/client/Render/AnnihilationBeamRenderer;renderEnd(ILnet/minecraft/core/Direction;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V"),
            index = 0)
    private int lensouls$animateImpactFrame(int frame) {
        return PhotoBeamRenderFlag.get() ? PhotoBeamRenderFlag.endFrame() : frame;
    }

    /** 第 1 张面片的滚转（原作 {@code +(俯仰+90°)}）→ 钉成 +45° */
    @ModifyArg(method = "renderBeam", require = 1, at = @At(value = "INVOKE", ordinal = 3,
            target = "Lnet/miauczel/legendary_monsters/util/MathUtils;quatFromRotationXYZ(FFFZ)Lorg/joml/Quaternionf;"),
            index = 1)
    private float lensouls$pinFirstQuadRoll(float angle) {
        return PhotoBeamRenderFlag.get() ? CROSS_ROLL_DEG : angle;
    }

    /** 第 2 张面片的滚转（原作 {@code −(俯仰+90°)}）→ 钉成 −45°，与第 1 张正交互补 */
    @ModifyArg(method = "renderBeam", require = 1, at = @At(value = "INVOKE", ordinal = 4,
            target = "Lnet/miauczel/legendary_monsters/util/MathUtils;quatFromRotationXYZ(FFFZ)Lorg/joml/Quaternionf;"),
            index = 1)
    private float lensouls$pinSecondQuadRoll(float angle) {
        return PhotoBeamRenderFlag.get() ? -CROSS_ROLL_DEG : angle;
    }
}
