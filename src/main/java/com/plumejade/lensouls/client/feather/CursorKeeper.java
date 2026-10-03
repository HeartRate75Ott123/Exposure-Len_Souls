package com.plumejade.lensouls.client.feather;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

/**
 * 切屏时保持光标位置（不让它被刷到屏幕中央）。
 * <p>
 * 做法来自参考项目 {@code Sus-InstantSwap}（{@code InstantSwapClient#positionCursorToUIBottomRight}）：
 * Minecraft 的 {@code MouseHandler.xpos/ypos} 是<b>私有字段</b>，而且它同时被外部 OS 光标状态影响，
 * 所以**两件事都要做**：
 * <ol>
 *   <li>反射写入 {@code xpos}/{@code ypos}（否则下一次鼠标事件会按旧值算，光标会跳）；</li>
 *   <li>{@code GLFW.glfwSetCursorPos(window, x, y)} 真正移动 OS 光标。</li>
 * </ol>
 * 坐标单位是**窗口物理像素**（与 {@code MouseHandler} 内部存的一致，不是 GUI 缩放后的坐标）。
 * <p>
 * 用法：切屏前 {@link #remember()}、切屏后 {@link #restore()}。
 * 全部失败都静默吞掉——光标位置只是手感，绝不能因此把界面搞崩。
 */
public final class CursorKeeper {

    private static double x = Double.NaN;
    private static double y = Double.NaN;

    private CursorKeeper() {
    }

    /** 记住当前光标位置（切屏前调用） */
    public static void remember() {
        try {
            MouseHandler handler = Minecraft.getInstance().mouseHandler;
            x = read(handler, "xpos");
            y = read(handler, "ypos");
        } catch (Throwable t) {
            x = Double.NaN;
            y = Double.NaN;
        }
    }

    /** 把光标放回记住的位置（切屏后调用，须晚于新屏幕的 init） */
    public static void restore() {
        if (Double.isNaN(x) || Double.isNaN(y)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        try {
            MouseHandler handler = mc.mouseHandler;
            write(handler, "xpos", x);
            write(handler, "ypos", y);
            long handle = mc.getWindow().getWindow();
            if (handle != 0L) {
                GLFW.glfwSetCursorPos(handle, x, y);
            }
        } catch (Throwable t) {
            // 非致命：位置复原失败就保持原版行为（居中）
        }
    }

    private static double read(MouseHandler handler, String name) throws Exception {
        Field field = MouseHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(handler);
    }

    private static void write(MouseHandler handler, String name, double value) throws Exception {
        Field field = MouseHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(handler, value);
    }
}
