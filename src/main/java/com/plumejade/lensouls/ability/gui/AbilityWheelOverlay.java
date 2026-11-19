package com.plumejade.lensouls.ability.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 取景器打开时的能力轮盘（覆盖层形态）。
 *
 * <h2>为什么不能直接 setScreen</h2>
 * Exposure 开着取景器时会 <b>cancel 掉整个 {@code Gui.render}</b>（配置 hide_hud_while_in_viewfinder），
 * 屏幕类根本没机会画；而且开屏幕会顺手把鼠标交还给系统、把取景器状态搅乱。
 * 所以这里走「复用 {@link AbilityRadialScreen} 的绘制与命中，但不挂到 {@code Minecraft.screen} 上」：
 * <ul>
 *   <li>手动画在 {@code GuiGraphics.flush()} 之后（见 GameRendererFrameEndMixin）⇒ 必然压在取景框之上；</li>
 *   <li>鼠标手动 {@code releaseMouse()}，松手再 {@code grabMouse()} ⇒ 光标能移动、能指扇区；</li>
 *   <li>不碰取景器状态 ⇒ 切完能力仍停在取景器里，饰品栏相机同理（它本来就是虚拟手持）。</li>
 * </ul>
 */
public final class AbilityWheelOverlay {

    private static AbilityRadialScreen screen;
    private static boolean open;

    private AbilityWheelOverlay() {
    }

    public static boolean isOpen() {
        return open;
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (screen == null) {
            screen = new AbilityRadialScreen();
            screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
        open = true;
        mc.mouseHandler.releaseMouse();
    }

    /** 松手：先按当前指向把能力写进相机，再收起（顺序不能反，onWheelKeyReleased 读的是屏幕内的 hover 状态）。 */
    public static void release() {
        AbilityRadialScreen current = screen;
        open = false;
        screen = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.mouseHandler.grabMouse();
        if (current != null) current.onWheelKeyReleased();
    }

    /** 直接收起，不提交选择（取景器没了 / 开了别的界面时用）。 */
    public static void close() {
        open = false;
        screen = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) mc.mouseHandler.grabMouse();
    }

    public static void render() {
        if (!open) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.noRender || mc.screen != null) return;
        AbilityRadialScreen current = screen;
        if (current == null) return;

        var window = mc.getWindow();
        double guiX = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / (double) window.getScreenWidth();
        double guiY = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / (double) window.getScreenHeight();

        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            current.render(graphics, (int) guiX, (int) guiY, 1.0F);
        } finally {
            graphics.flush();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }
}
