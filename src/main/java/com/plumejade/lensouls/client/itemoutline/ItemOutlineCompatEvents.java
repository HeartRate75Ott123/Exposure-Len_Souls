package com.plumejade.lensouls.client.itemoutline;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 第一人称手持物描边合成时机：在 {@code RenderGuiEvent.Pre}（世界渲染之后、HUD 之前）绘制，
 * 使描边环绕手持物且位于 HUD 之下。
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class ItemOutlineCompatEvents {

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) return;
        ItemOutlinePostProcessor.composite(mc, main);
    }
}
