package com.plumejade.lensouls.client.outline;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

public record ScissorState(boolean enabled, int x, int y, int width, int height) {
    public static ScissorState capture() {
        boolean enabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (!enabled) return new ScissorState(false, 0, 0, 0, 0);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer box = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
            return new ScissorState(true, box.get(0), box.get(1), box.get(2), box.get(3));
        }
    }

    public ScreenRect intersect(ScreenRect rect) {
        if (!enabled) return rect;
        int x0 = Math.max(x, rect.minX());
        int y0 = Math.max(y, rect.minY());
        int x1 = Math.min(x + width, rect.maxX());
        int y1 = Math.min(y + height, rect.maxY());
        if (x1 <= x0 || y1 <= y0) return ScreenRect.empty();
        return new ScreenRect(x0, y0, x1, y1);
    }

    public void restore() {
        if (!enabled || width <= 0 || height <= 0) {
            RenderSystem.disableScissor();
            return;
        }
        RenderSystem.enableScissor(x, y, width, height);
    }
}