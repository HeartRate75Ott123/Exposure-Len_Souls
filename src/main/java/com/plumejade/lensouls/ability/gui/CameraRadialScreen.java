package com.plumejade.lensouls.ability.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.plumejade.lensouls.ability.CameraEquipHandler;
import com.plumejade.lensouls.ability.network.CameraDrawPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 相机轮盘（身上装备了多台相机时，点按 Z 呼出）。
 * <p>
 * 视觉与交互全部沿用能力轮盘 {@link AbilityRadialScreen} 那一套（同样的环、同样的突出/填充缓动、
 * 同样顺时针展开），只把数据源从「能力」换成「饰品槽里的相机」，点击后把槽位/下标发给服务端取出。
 * <p>
 * 关闭：E / ESC / Z（能力轮盘是松手关闭，这里按用户要求做成点按开关）。
 */
public class CameraRadialScreen extends Screen {

    private static final double BASE_OUTER = 76.0;
    private static final double OUTER_PER_ITEM = 3.0;
    private static final int SCALE_START = 4;
    private static final double RING_THICKNESS = 30.0;
    private static final double DEADZONE = 22.0;
    private static final double SLICE_GAP_DEG = 2.0;
    private static final double HOVER_GROW = 0.08;
    private static final long OPEN_MS = 190L;
    private static final float GROW_MS = 100f;
    private static final float SWEEP_MS = 180f;

    private static final int RING_COLOR = 0xAA000000;
    private static final int BORDER_COLOR = 0x33FFFFFF;
    private static final int HOVER_FILL = 0x55FFFFFF;
    private static final double BORDER_W = 1.5;

    private final List<CameraEquipHandler.Entry> items;
    private final long openedAtMs = Util.getMillis();
    private final SliceAnim anim = new SliceAnim();

    private int hovered = -1;

    public CameraRadialScreen(List<CameraEquipHandler.Entry> cameras) {
        super(Component.translatable("gui.lensouls.camera.wheel_title"));
        this.items = cameras;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x40000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int count = items.size();
        if (count <= 0) {
            g.drawCenteredString(this.font, Component.translatable("camera.lensouls.none"), cx, cy - 4, 0xFFFFAA55);
            return;
        }

        long now = Util.getMillis();
        double progress = Math.min(1.0, (now - openedAtMs) / (double) OPEN_MS);
        double outer = BASE_OUTER + Math.max(0, count - SCALE_START) * OUTER_PER_ITEM;
        double inner = Math.max(10.0, outer - RING_THICKNESS);

        hovered = pickSector(mouseX, mouseY, cx, cy, count);
        anim.tick(now, hovered, count);

        double step = Math.PI * 2.0 / count;
        double openSweep = progress * Math.PI * 2.0;
        float fade = (float) progress;

        for (int i = 0; i < count; i++) {
            double a0 = -Math.PI / 2.0 + i * step;
            double a1 = a0 + step;
            double clip0 = a0;
            double clip1 = Math.min(a1, -Math.PI / 2.0 + openSweep);
            if (clip1 <= clip0) continue;
            a0 = clip0;
            a1 = clip1;

            double localGap = Math.min(Math.toRadians(SLICE_GAP_DEG), (a1 - a0) * 0.5);
            a0 += localGap * 0.5;
            a1 -= localGap * 0.5;
            if (a1 <= a0) continue;

            boolean isHovered = i == hovered;
            double rOuter = outer * (1.0 + HOVER_GROW * anim.growOf(i));

            fillRingSector(g, cx, cy, inner, rOuter, a0, a1, scaleAlpha(RING_COLOR, fade));
            fillRingSector(g, cx, cy, rOuter - BORDER_W, rOuter, a0, a1, scaleAlpha(BORDER_COLOR, fade));
            if (isHovered) {
                float sweep = anim.sweepOf(i);
                if (sweep > 0f) {
                    double filledOuter = inner + (rOuter - inner) * sweep;
                    fillRingSector(g, cx, cy, inner, filledOuter, a0, a1, scaleAlpha(HOVER_FILL, fade));
                }
            }
        }

        double rMidBase = (inner + outer) * 0.5;
        for (int i = 0; i < count; i++) {
            double ang = -Math.PI / 2.0 + (i + 0.5) * step;
            double rMid = rMidBase * (1.0 + HOVER_GROW * 0.5 * anim.growOf(i));
            int ix = cx + (int) Math.round(Math.cos(ang) * rMid);
            int iy = cy + (int) Math.round(Math.sin(ang) * rMid);
            g.setColor(1f, 1f, 1f, fade);
            g.renderItem(items.get(i).stack(), ix - 8, iy - 8);
            g.setColor(1f, 1f, 1f, 1f);
        }

        renderCenterLabel(g, cx, cy, fade);
    }

    private void renderCenterLabel(GuiGraphics g, int cx, int cy, float fade) {
        Component text;
        int color;
        if (hovered >= 0 && hovered < items.size()) {
            text = items.get(hovered).stack().getHoverName();
            color = 0xFFFFFFFF;
        } else {
            text = Component.translatable("gui.lensouls.camera.wheel_title");
            color = 0xFFDDDDDD;
        }
        int alpha = (int) (Math.min(1f, fade) * 255f);
        g.drawCenteredString(this.font, text, cx, cy - 6, (alpha << 24) | (color & 0x00FFFFFF));

        int hintAlpha = (int) (Math.min(1f, fade) * 170f);
        g.drawCenteredString(this.font, Component.translatable("gui.lensouls.camera.wheel_hint"),
                cx, cy + 5, (hintAlpha << 24) | 0x00AAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered >= 0 && hovered < items.size()) {
            CameraEquipHandler.Entry entry = items.get(hovered);
            PacketDistributor.sendToServer(new CameraDrawPacket(entry.slotId(), entry.index()));
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == this) mc.setScreen(null);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** E / ESC / Z 都能关（用户要求）。 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_E
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (mc.screen == this) mc.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == this) mc.setScreen(null);
    }

    // ========== 几何 ==========

    private int pickSector(int mouseX, int mouseY, int cx, int cy, int count) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        if (dx * dx + dy * dy < DEADZONE * DEADZONE) return -1;
        double ang = Math.atan2(dy, dx) + Math.PI / 2.0;
        double full = Math.PI * 2.0;
        while (ang < 0) ang += full;
        while (ang >= full) ang -= full;
        int idx = (int) Math.floor(ang / (full / count));
        return (idx < 0 || idx >= count) ? -1 : idx;
    }

    private static final class SliceAnim {
        private long lastMs = 0L;
        private int count = 0;
        private float[] grow = new float[0];
        private float[] sweep = new float[0];

        void tick(long nowMs, int hovered, int n) {
            if (n != count) {
                count = Math.max(0, n);
                grow = new float[count];
                sweep = new float[count];
            }
            long dt = lastMs == 0L ? 16L : Math.max(1L, nowMs - lastMs);
            lastMs = nowMs;
            float kGrow = Math.min(1f, dt / GROW_MS);
            float kSweep = Math.min(1f, dt / SWEEP_MS);
            for (int i = 0; i < count; i++) {
                float target = i == hovered ? 1f : 0f;
                grow[i] += (target - grow[i]) * kGrow;
                sweep[i] += (target - sweep[i]) * kSweep;
                if (Math.abs(grow[i] - target) < 0.001f) grow[i] = target;
                if (Math.abs(sweep[i] - target) < 0.001f) sweep[i] = target;
            }
        }

        float growOf(int i) {
            return i >= 0 && i < grow.length ? grow[i] : 0f;
        }

        float sweepOf(int i) {
            return i >= 0 && i < sweep.length ? sweep[i] : 0f;
        }
    }

    // ========== 绘制 ==========

    private static int scaleAlpha(int argb, float scale) {
        int a = (argb >>> 24) & 0xFF;
        int na = Math.max(0, Math.min(255, Math.round(a * Math.max(0f, Math.min(1f, scale)))));
        return (argb & 0x00FFFFFF) | (na << 24);
    }

    private static void fillRingSector(GuiGraphics g, int cx, int cy, double rInner, double rOuter,
                                       double a0, double a1, int argb) {
        if (a1 <= a0 || rOuter <= rInner) return;
        int segs = Math.max(12, (int) Math.ceil((a1 - a0) * 48));

        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >>> 16) & 0xFF) / 255f;
        float gn = ((argb >>> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segs; i++) {
            double t = (double) i / (double) segs;
            double ang = a0 + (a1 - a0) * t;
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            buf.addVertex(pose, (float) (cx + cos * rOuter), (float) (cy + sin * rOuter), 0).setColor(r, gn, b, a);
            buf.addVertex(pose, (float) (cx + cos * rInner), (float) (cy + sin * rInner), 0).setColor(r, gn, b, a);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }
}
