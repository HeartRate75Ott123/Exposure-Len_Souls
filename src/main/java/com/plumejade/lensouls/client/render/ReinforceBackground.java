package com.plumejade.lensouls.client.render;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 次元强化界面的全屏背景 —— 直接铺一张预渲染好的柔焦黑白玻璃花贴图。
 * <p>
 * 贴图：{@code assets/lensouls/textures/gui/reinforce_background.png}（1920x1080，不透明）。
 * 想换背景只替换这张 PNG 即可，代码不用动；尺寸变了把 {@link #TEX_W}/{@link #TEX_H} 一起改。
 * <p>
 * 铺法用 <b>cover</b>（等比放大到刚好盖满窗口，多出来的部分左右/上下对称裁掉）：
 * 窗口比例与贴图不一致时不会拉伸变形，也不会露出黑边。
 * <p>
 * 贴图之上再压一层 40% 黑的柔纱{@link #VEIL}：原图中心偏亮（均亮 137），
 * 不压纱时白色文字对比度不足；压纱后中心降到 ~85，文字可读且画面仍然清楚。
 * 资源缺失时 {@link #render} 返回 false，调用方回退纯色背景。
 */
public final class ReinforceBackground {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "textures/gui/reinforce_background.png");

    /** 贴图原始像素尺寸（cover 裁剪用） */
    private static final int TEX_W = 1920;
    private static final int TEX_H = 1080;

    /** 覆盖在背景上的柔纱（ARGB，40% 黑），保证 UI 文字对比度 */
    private static final int VEIL = 0x66000000;

    /** 贴图是否可用的缓存（null = 尚未检测） */
    private static Boolean available;

    private ReinforceBackground() {
    }

    /**
     * 铺满整个界面。
     *
     * @param minecraft   用于查一次资源是否存在
     * @param partialTick 未使用（保留签名，调用方两处一致）
     * @param guiWidth    界面宽（GUI 像素）
     * @param guiHeight   界面高
     * @return 是否真的画了（false = 贴图缺失，调用方应回退纯色）
     */
    public static boolean render(GuiGraphics graphics, Minecraft minecraft, float partialTick,
                                 int guiWidth, int guiHeight) {
        if (minecraft == null || graphics == null || guiWidth <= 0 || guiHeight <= 0) return false;

        if (available == null) {
            boolean ok;
            try {
                ok = minecraft.getResourceManager().getResource(TEXTURE).isPresent();
            } catch (Exception e) {
                ok = false;
            }
            available = ok;
        }
        if (!available) return false;

        // cover：等比缩放到刚好盖满窗口
        float scale = Math.max((float) guiWidth / TEX_W, (float) guiHeight / TEX_H);
        int srcW = Math.min(TEX_W, Math.max(1, Math.round(guiWidth / scale)));
        int srcH = Math.min(TEX_H, Math.max(1, Math.round(guiHeight / scale)));
        int u = (TEX_W - srcW) / 2;
        int v = (TEX_H - srcH) / 2;

        graphics.blit(TEXTURE, 0, 0, guiWidth, guiHeight,
                (float) u, (float) v, srcW, srcH, TEX_W, TEX_H);
        if ((VEIL >>> 24) != 0) {
            graphics.fill(0, 0, guiWidth, guiHeight, VEIL);
        }
        return true;
    }
}
