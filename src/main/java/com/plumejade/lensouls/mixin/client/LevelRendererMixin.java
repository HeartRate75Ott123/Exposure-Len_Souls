package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import com.plumejade.lensouls.ability.client.GrayOutManager;
import com.plumejade.lensouls.boss.StunPauseHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 破韧渲染修复 + 时停场景视觉。
 * <p>
 * 破韧渲染修复：渲染层复刻原版全局 freeze 语义。
 * <p>
 * 原版 {@code /tick freeze} 时客户端 {@code DeltaTracker.Timer.getGameTimeDeltaPartialTick}
 * 恒返回 1.0F → 所有 {@code lerp(partialTick, O值, 当前值)} 取当前值 → 画面完全静止。
 * 破韧（韧性清空）只定格单个实体，但渲染插值仍随 partialTicks 每帧波动
 * （tickCount 冻结后 partialTicks 波动 → 模型动画/插值抖动，即抽搐）。
 * <p>
 * 本 mixin 对破韧实体把 {@code renderEntity} 的 partialTicks 固定为 1.0F：
 * 一个注入点覆盖所有渲染器/部件/mod 模型（HydraHeadModel 的 mouthOpen、
 * MazeSlime.oSquish、BirdRenderer flap、Adherent bounce 等），
 * 无需逐字段堵插值。
 * <p>
 * 时停场景视觉：
 * <ul>
 *   <li>{@code renderSky} HEAD：时停期间画黑洞星空球并取消原版天空盒
 *       （星空在 renderSky 阶段绘制：主目标深度此时为 clear 值，星空先铺满，
 *       地形随后渲染覆盖——天然只覆盖天空区域，不盖地形/实体）；</li>
 *   <li>{@code renderClouds} HEAD：时停期间跳过云。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @ModifyVariable(method = "renderEntity",
            at = @At("HEAD"), argsOnly = true)
    private float lensouls$freezePartialTicks(float modified, Entity entity,
                                              double camX, double camY, double camZ,
                                              float partialTicks,
                                              PoseStack poseStack,
                                              net.minecraft.client.renderer.MultiBufferSource buffer) {
        // 破韧定身或时停定身：partialTicks 固定 1.0（插值取当前值，画面完全静止）
        if (StunPauseHelper.isToughnessBroken(entity) || ClientFreezeCache.isFrozen(entity.getId())) {
            return 1.0F;
        }
        return partialTicks;
    }

    /**
     * 时停：黑洞星空替代原版天空盒（星空铺满后由地形覆盖，只显示天空区域）。
     * <p>
     * 注入点选在 {@code FogRenderer.levelFogColor()} 调用之后（原版天空绘制前）。
     */
    @Inject(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/FogRenderer;levelFogColor()V",
                    shift = At.Shift.AFTER),
            cancellable = true, require = 1)
    private void lensouls$blackHoleSky(Matrix4f projectionMatrix, Matrix4f modelViewMatrix,
                                       float partialTicks, Camera camera, boolean bl,
                                       Runnable runnable, CallbackInfo ci) {
        if (!ClientFreezeCache.isTimeFrozen()) return;
        GrayOutManager.renderBlackHoleSky(modelViewMatrix);
        ci.cancel();
    }

    /** 时停：跳过云（黑洞天空完全替代）。 */
    @Inject(method = "renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void lensouls$skipClouds(PoseStack poseStack, Matrix4f matrix,
                                     Matrix4f projection, float partialTick,
                                     double camX, double camY, double camZ,
                                     CallbackInfo ci) {
        if (ClientFreezeCache.isTimeFrozen()) {
            ci.cancel();
        }
    }
}
