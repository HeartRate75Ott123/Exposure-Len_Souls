package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.plumejade.lensouls.ability.client.BossOutlineGradientRenderer;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 拦截原版 {@code LevelRenderer.doEntityOutline} 的 {@code entityTarget.blitToScreen}，
 * 玩家有 Boss 镜魂 effect 时用渐变 shader 替换原版单色 outline 上屏（四色渐变描边）。
 * 无 Boss effect 时走原版 blit（不影响其他 glowing 实体/队伍轮廓）。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererOutlineMixin {

    @Redirect(method = "doEntityOutline", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(IIZ)V"))
    private void lensouls$gradientOutline(RenderTarget target, int width, int height, boolean blit) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        BossOutlineColors colors = player != null ? BossOutlineColors.fromEntity(player) : null;
        if (colors == null) {
            target.blitToScreen(width, height, blit);
            return;
        }
        BossOutlineGradientRenderer.render(target, colors, mc);
    }
}
