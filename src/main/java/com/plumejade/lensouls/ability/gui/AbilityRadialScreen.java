package com.plumejade.lensouls.ability.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.client.ClientAbilityCache;
import com.plumejade.lensouls.ability.network.AbilitySelectPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 能力轮盘（长按呼出、松手确认）。
 *
 * <h2>交互</h2>
 * 手持相机时<b>长按</b>轮盘键打开，松手关闭；鼠标指向哪个扇区，松手就把它写进
 * <b>当前手持那台相机</b>的选中记录（走 {@link AbilitySelectPacket}，与服务端
 * {@code CameraAbilityStore} 同一套口径 ⇒ 每台相机各自记住自己的能力不变）。
 * 光标停在中心死区（不指任何扇区）时松手 = 什么都不改。
 *
 * <h2>顺序</h2>
 * 扇区顺序取存档级展示顺序里<b>已解锁</b>的那一段（与菜单、右侧滚动列表同源）。
 * 当前这台相机已选中的能力带一圈常亮绿边，方便确认「我现在的状态」。
 *
 * <h2>视觉（口径对齐 EZActions 的径向菜单）</h2>
 * <ul>
 *   <li><b>暗色环</b>：底色 {@code 0xAA000000}（近黑、半透），外沿一圈 {@code 0x33FFFFFF} 细描边；</li>
 *   <li><b>命中扇区向外突出</b>：外半径按 {@code rOuter * (1 + 8% * grow)} 撑开，{@code grow} 是
 *       每扇区独立、按 ~100ms 指数逼近的缓动量 —— 移过去是「顶出来」而不是瞬间跳变；</li>
 *   <li><b>命中填充由内向外推进</b>：{@code sweep} 按 ~180ms 逼近，填充从内半径铺到外半径；</li>
 *   <li>展开仍是顺时针擦除 + 整体淡入（形态推进与不透明度同时走，避免单通道一次性拉满）。</li>
 * </ul>
 * 几何与绘制手法沿用 EZActions（TRIANGLE_STRIP 逐扇区填充、扇区从 12 点方向起顺时针）。
 */
public class AbilityRadialScreen extends Screen {

    /** 环外半径（4 个以内扇区） */
    private static final double BASE_OUTER = 76.0;
    /** 每多一个扇区向外扩多少 */
    private static final double OUTER_PER_ITEM = 3.0;
    /** 超过多少个扇区才开始外扩 */
    private static final int SCALE_START = 4;
    /** 环厚度 */
    private static final double RING_THICKNESS = 30.0;
    /** 中心死区半径（光标在这里面 = 不选） */
    private static final double DEADZONE = 22.0;
    /** 扇区之间的缝隙（度） */
    private static final double SLICE_GAP_DEG = 2.0;
    /** 命中扇区向外突出的比例（外半径 × 该值） */
    private static final double HOVER_GROW = 0.08;
    /** 展开动画时长（毫秒） */
    private static final long OPEN_MS = 190L;
    /** 命中缓动时长（毫秒）：突出与填充各自逼近目标的时间常数 */
    private static final float GROW_MS = 100f;
    private static final float SWEEP_MS = 180f;

    /** 暗色环底色（ARGB） */
    private static final int RING_COLOR = 0xAA000000;
    /** 外沿细描边 */
    private static final int BORDER_COLOR = 0x33FFFFFF;
    /** 命中填充 */
    private static final int HOVER_FILL = 0x55FFFFFF;
    /** 当前手持相机已选中的能力：常亮绿边（用户点名 #54FC54） */
    private static final int ACTIVE_COLOR = 0xFF54FC54;
    /** 外沿描边宽度（像素）：命中填充推进到这条描边的内沿就停，不再压到描边上 */
    private static final double BORDER_W = 1.5;

    private final List<AbilityType> items = new ArrayList<>(ClientAbilityCache.getUnlockedInDisplayOrder());
    private final long openedAtMs = Util.getMillis();
    private final SliceAnim anim = new SliceAnim();

    private int hovered = -1;

    public AbilityRadialScreen() {
        super(Component.translatable("gui.lensouls.ability.wheel_title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 不画背景模糊：轮盘要能看清世界，只叠一层很淡的压暗 */
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
            g.drawCenteredString(this.font, Component.translatable("gui.lensouls.ability.wheel_empty"),
                    cx, cy - 4, 0xFFFFAA55);
            return;
        }

        long now = Util.getMillis();
        double progress = Math.min(1.0, (now - openedAtMs) / (double) OPEN_MS);
        double outer = BASE_OUTER + Math.max(0, count - SCALE_START) * OUTER_PER_ITEM;
        double inner = Math.max(10.0, outer - RING_THICKNESS);

        hovered = pickSector(mouseX, mouseY, cx, cy, count);
        anim.tick(now, hovered, count);

        AbilityType active = ClientAbilityCache.getEnabled();

        double step = Math.PI * 2.0 / count;
        double openSweep = progress * Math.PI * 2.0;
        float fade = (float) progress;

        for (int i = 0; i < count; i++) {
            double a0 = -Math.PI / 2.0 + i * step;
            double a1 = a0 + step;
            // 展开：顺时针擦除
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
            boolean isActive = active != null && items.get(i) == active;

            // 命中扇区向外撑开（缓动）
            double rOuter = outer * (1.0 + HOVER_GROW * anim.growOf(i));

            // 暗色环本体
            fillRingSector(g, cx, cy, inner, rOuter, a0, a1, scaleAlpha(RING_COLOR, fade));
            // 外沿细描边
            fillRingSector(g, cx, cy, rOuter - BORDER_W, rOuter, a0, a1, scaleAlpha(BORDER_COLOR, fade));
            // 当前相机已选中的能力：常亮绿边
            if (isActive) {
                fillRingSector(g, cx, cy, rOuter - BORDER_W - 0.5, rOuter, a0, a1, scaleAlpha(ACTIVE_COLOR, fade));
            }
            // 命中填充：由内向外推进（缓动）。
            // 已选中（带 #54FC54 描边）那张推到描边内沿就停，保住绿边；其余扇区推满到环外沿。
            if (isHovered) {
                float sweep = anim.sweepOf(i);
                if (sweep > 0f) {
                    double fillInner = inner;
                    double fillOuter = isActive ? rOuter - BORDER_W : rOuter;
                    double filledOuter = fillInner + (fillOuter - fillInner) * sweep;
                    fillRingSector(g, cx, cy, fillInner, filledOuter, a0, a1, scaleAlpha(HOVER_FILL, fade));
                }
            }
        }

        // 图标：位置随命中缓动略微外移（与环体同步，不单独跳）
        double rMidBase = (inner + outer) * 0.5;
        for (int i = 0; i < count; i++) {
            double ang = -Math.PI / 2.0 + (i + 0.5) * step;
            double rMid = rMidBase * (1.0 + HOVER_GROW * 0.5 * anim.growOf(i));
            int ix = cx + (int) Math.round(Math.cos(ang) * rMid);
            int iy = cy + (int) Math.round(Math.sin(ang) * rMid);
            g.setColor(1f, 1f, 1f, fade);
            g.renderItem(iconOf(items.get(i)), ix - 8, iy - 8);
            g.setColor(1f, 1f, 1f, 1f);
        }

        renderCenterLabel(g, cx, cy, fade);
    }

    private void renderCenterLabel(GuiGraphics g, int cx, int cy, float fade) {
        Component text;
        int color;
        if (hovered >= 0 && hovered < items.size()) {
            text = Component.translatable(items.get(hovered).getNameKey());
            color = 0xFFFFFFFF;
        } else {
            text = Component.translatable("gui.lensouls.ability.wheel_title");
            color = 0xFFDDDDDD;
        }
        int alpha = (int) (Math.min(1f, fade) * 255f);
        g.drawCenteredString(this.font, text, cx, cy - 6, (alpha << 24) | (color & 0x00FFFFFF));

        Component hint = Component.translatable("gui.lensouls.ability.wheel_hint");
        int hintAlpha = (int) (Math.min(1f, fade) * 170f);
        g.drawCenteredString(this.font, hint, cx, cy + 5, (hintAlpha << 24) | 0x00AAAAAA);
    }

    /** 轮盘键松开：把指向的能力写进当前手持相机，然后关屏 */
    public void onWheelKeyReleased() {
        if (hovered >= 0 && hovered < items.size()) {
            AbilityType type = items.get(hovered);
            AbilityType current = ClientAbilityCache.getEnabled();
            // 已经是它了就不重复发包（省一次无意义的同步）
            if (current != type) {
                PacketDistributor.sendToServer(new AbilitySelectPacket(type.ordinal()));
                ClientAbilityCache.setHeldCameraSelected(type.ordinal());
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == this) mc.setScreen(null);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == this) mc.setScreen(null);
    }

    // ========== 几何 ==========

    /** 返回光标命中的扇区下标；落在中心死区返回 -1 */
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

    private static ItemStack iconOf(AbilityType type) {
        return new ItemStack(BuiltInRegistries.ITEM.get(type.getIconItemId()));
    }

    // ========== 逐扇区缓动（对齐 EZActions 的 SliceHoverAnim） ==========

    /**
     * 每扇区两个缓动量：
     * <ul>
     *   <li>{@code grow}：0~1，命中后按 ~100ms 指数逼近 1，用来把外半径撑出去；</li>
     *   <li>{@code sweep}：0~1，命中后按 ~180ms 逼近 1，用来把命中填充从内半径铺到外半径。</li>
     * </ul>
     * 用真实时间差算步长，帧率变化不影响观感；扇区数量变化时整表重建，避免残留值串号。
     */
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

    /** 填充一段环形扇区（TRIANGLE_STRIP，段数按角度自适应） */
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
