package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 时停黑白后处理的 PostChain 封装（原版后处理管线）。
 * <p>
 * 链为空 JSON（{@code shaders/post/gray_chain.json}），pass 编程式
 * {@code addPass} 注入：黑白滤镜作用于最终画面。
 * <p>
 * 触发方式：{@link GameRendererFrameEndMixin}（帧末）把 {@code GameRenderer.postEffect}
 * 指向本链并置 {@code effectActive=true}，下一帧 render 中段由原版自动
 * {@code postEffect.process(deltaTicks)} —— 此时描边（doEntityOutline）已画完、
 * blitToScreen 尚未发生，黑白滤镜作用于最终画面，帧末描边合成画在其上。
 */
@OnlyIn(Dist.CLIENT)
public class GrayPostChain {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static PostChain chain;
    private static boolean failed;

    /** 获取（惰性创建）时停黑白后处理链；失败返回 null（降级为无滤镜）。 */
    public static PostChain getOrCreate() {
        if (chain != null) return chain;
        if (failed) return null;
        try {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main,
                    ResourceLocation.fromNamespaceAndPath("lensouls", "shaders/post/gray_chain.json"));
            PostPass grayPass = chain.addPass("lensouls:gray", main, main, false);
            LOGGER.info("[Lensouls][BW] 时停黑白 PostChain 初始化完成");
        } catch (Exception e) {
            failed = true;
            if (chain != null) {
                try {
                    chain.close();
                } catch (Exception ignored) {
                }
                chain = null;
            }
            LOGGER.error("[Lensouls][BW] PostChain 初始化失败，时停黑白滤镜不可用", e);
        }
        return chain;
    }

    private GrayPostChain() {
    }
}