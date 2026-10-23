package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import com.plumejade.lensouls.ability.client.sky.TimeFreezeSky;
import com.plumejade.lensouls.boss.StunPauseHelper;
import com.mojang.blaze3d.vertex.PoseStack;
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
 * 破韧渲染修复 + 时停云端遮挡。
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
 * 时停天空不再在这里画：{@link TimeFreezeSky} 改成 {@code RenderLevelStageEvent}
 * 的深度掩码全屏 pass（AFTER_LEVEL），这样原版 / Sodium / Iris 光影下都能正常显示。
 * 这里只保留"跳过云层"——云会写深度，会挡住我们画在天空区域的时计。
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
     * 时停：跳过云（云层会写深度，把自定义天穹切成一块一块）。
     * <p>
     * 只有"能看到冻结天穹的那个客户端"才需要跳过——其他玩家只是世界被定住，
     * 天空照旧，云不该凭空消失。
     */
    @Inject(method = "renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void lensouls$skipClouds(PoseStack poseStack, Matrix4f matrix,
                                     Matrix4f projection, float partialTick,
                                     double camX, double camY, double camZ,
                                     CallbackInfo ci) {
        if (TimeFreezeSky.isSkyActive()) {
            ci.cancel();
        }
    }
}
