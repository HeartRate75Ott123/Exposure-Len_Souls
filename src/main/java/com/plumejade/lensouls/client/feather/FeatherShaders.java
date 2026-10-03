package com.plumejade.lensouls.client.feather;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/**
 * 羽毛界面的自定义 CoreShader 注册入口。
 * <p>
 * {@code rrect} —— <b>SDF 圆角矩形</b>：每像素解析计算有符号距离场，圆角是真正矢量的，
 * 不依赖贴图、不做网格细分，任意分辨率下都精确、边缘逐像素抗锯齿；一个 shader 就能画
 * 面板底板、条目、按钮、滚动条（外观参数全走 uniform）。
 * <p>
 * 移植来源：{@code plumestweaks} 的 {@code RiftShaders}（其 {@code rrect} 与玻璃管线
 * 血统为 ReGlass，MIT）。资源放在本模组自己的命名空间
 * {@code assets/lensouls/shaders/core/}，不覆盖原版 shader，因此不会与其他模组冲突。
 * <p>
 * <b>为什么走显式注册而不是 {@code @EventBusSubscriber}：</b>自动订阅失败时是<b>静默</b>的
 * （既不注册也不报错），排查极其困难；这里由 {@code LenSoulsClient} 显式挂上并打日志，
 * 注册成功与否一眼可见。
 */
public final class FeatherShaders {

    public static final ResourceLocation RRECT_ID =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "rrect");

    private static ShaderInstance rrect;
    private static boolean registerAttempted;

    private FeatherShaders() {
    }

    /** SDF 圆角矩形 shader；未成功加载时为 null（调用方必须回退，界面不能空白） */
    public static ShaderInstance rrect() {
        return rrect;
    }

    /** 由 {@code LenSoulsClient} 注册（仅客户端） */
    public static void register(IEventBus modEventBus) {
        if (registerAttempted) return;
        registerAttempted = true;
        modEventBus.addListener(FeatherShaders::onRegisterShaders);
    }

    private static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            // 顶点格式用原版 position_tex：Position(vec3) + UV0(vec2)
            // UV0 里传「矩形内局部像素坐标」，供片元侧求 SDF
            ShaderInstance shader = new ShaderInstance(
                    event.getResourceProvider(), RRECT_ID, DefaultVertexFormat.POSITION_TEX);
            event.registerShader(shader, loaded -> {
                rrect = loaded;
                LenSouls.LOGGER.info("[Feather] 圆角矩形 shader 加载完成：{}", RRECT_ID);
            });
        } catch (IOException e) {
            LenSouls.LOGGER.error("[Feather] 圆角矩形 shader 加载失败：{}", RRECT_ID, e);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Feather] 注册圆角矩形 shader 时异常", t);
        }
    }
}
