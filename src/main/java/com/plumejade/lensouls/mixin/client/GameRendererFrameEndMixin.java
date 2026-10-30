package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererFrameEndMixin {

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void lensouls$beginFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        FrozenOutlineManager.resetFrame();
        com.plumejade.lensouls.client.fx.ScreenFxManager.beginFrame();
    }

    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void lensouls$compositeAtFrameEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        var mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null) return;

        FrozenOutlineManager.compositeIfNeeded(minecraft, mainTarget);
    }

    /**
     * HUD 之上的叠加层（相机冷却禁止符 / 取景器提示文字）。
     * <p>
     * 注入点 = vanilla 把 HUD / 屏幕 / toast 全部画完之后那一次 {@code GuiGraphics.flush()} 之后：
     * 此时 GUI 正交矩阵还没弹出（{@code RoundedRect} 依赖它），画的东西必然压在所有 GUI 之上。
     * <b>不能用 {@code RenderGuiEvent}</b>：它由 {@code GuiLayerManager} 在 {@code Gui.render} 内部派发，
     * 而 Exposure 打开取景器时会 cancel 掉整个 {@code Gui.render}，事件根本不会发。
     */
    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V",
                    shift = At.Shift.AFTER),
            require = 1)
    private void lensouls$hudTopOverlay(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        com.plumejade.lensouls.client.hud.ViewfinderOverlay.render();
        // 要害打击白霸体失败的故障叉号：同一注入点，但它是独立叠加层，不依赖取景器状态
        com.plumejade.lensouls.client.hud.VitalDenyOverlay.render();
        // 同一件事的默认形态（没装机械眼时）：屏幕上方正中一枚原版虚弱小图标，0.3s
        com.plumejade.lensouls.client.hud.HyperArmorDenyIcon.render();
    }

    /**
     * 屏幕后处理（时空回溯 / 夺魂索命 / 削韧镜面炸碎）必须在<b>第一人称手部之后</b>绘制。
     * <p>
     * {@code GameRenderer.renderLevel} 的顺序是：{@code LevelRenderer.renderLevel}（世界，末尾发
     * {@code AFTER_LEVEL}）→ 清深度 → {@code renderItemInHand}（手部 / 手持物）。挂在 {@code AFTER_LEVEL}
     * 的后处理只盖住世界，手里的相机/手还是完整的 —— 实机表现就是"镜面没叠在最上层"。
     * 这里改在 {@code renderLevel} 的 RETURN（世界 + 手部都画完、HUD 之前）驱动。
     */
    @Inject(method = "renderLevel", at = @At("RETURN"), require = 1)
    private void lensouls$screenFxAfterHand(DeltaTracker deltaTracker, CallbackInfo ci) {
        com.plumejade.lensouls.client.fx.ScreenFxManager.renderFrame();
    }
}
