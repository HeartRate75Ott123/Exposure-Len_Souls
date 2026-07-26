package com.plumejade.lensouls.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 屏幕震屏渲染应用器——通过 RenderLevelStageEvent 偏移世界渲染矩阵。
 */
public class ScreenShakeApplier {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (!ScreenShakeHandler.isShaking()) return;

        // 每帧生成新的随机偏移
        float ox = (float) (Math.random() - 0.5) * 2f * ScreenShakeHandler.getIntensity();
        float oy = (float) (Math.random() - 0.5) * 2f * ScreenShakeHandler.getIntensity();

        PoseStack poseStack = event.getPoseStack();
        poseStack.translate(ox * 0.001, oy * 0.001, 0);
    }
}
