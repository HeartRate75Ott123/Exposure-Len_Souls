package com.plumejade.lensouls.client.feather;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 浮动提示（如「该槽位已永久锁定」「胶卷用光了」）。
 * <p>
 * <b>为什么不用动作栏消息</b>：GUI 渲染在消息层之上，玩家在界面里根本看不到聊天/动作栏提示。
 * 所以提示必须**画在当前 GUI 里**，并带不透明度淡入淡出，几秒后自动消失。
 * <p>
 * 时间轴：淡入 0.25s → 停留 2s → 淡出 0.5s。
 *
 * <h3>多条叠加（新提示顶掉旧提示）</h3>
 * 以前只有一条：新消息直接把旧消息覆盖掉，实机看起来就是"上一句突然消失"。
 * 现在是**一个小队列**：
 * <ul>
 *   <li>新提示出现时，仍在播的旧提示立刻进入 {@value #RETIRE_MS}ms 的**快速淡出**（不是硬切）；</li>
 *   <li>渲染按「旧 → 新」的次序，并给每条加一个递进的 <b>z 偏移</b>（{@code poseStack.translate(0,0,z)}）：
 *       两条弹窗位置相同、会重叠，靠 z 保证新的压在旧的上面，不会互相插穿/闪烁；</li>
 *   <li>同一条文字还在播时不叠新的，只重置计时（连续触发同一句不会糊成一坨）；</li>
 *   <li>最多同时存在 {@value #MAX_VISIBLE} 条，再多就把最旧的丢掉。</li>
 * </ul>
 * <p>
 * <b>透明度必须统一走 {@link GuiGraphics#setColor}</b>（= RenderSystem 的 ColorModulator）：
 * 底板是 `rrect` 着色器按色值里的 alpha 画的，而文字走的是另一条渲染通路——
 * 之前只把 alpha 烘进颜色，实机表现就是「底板已消失、文字还满不透明几帧后突兀消失」。
 * 用 ColorModulator 一次性乘住「底板 + 文字」，两者才会同步渐隐。
 */
public final class FeatherToast {

    private static final float FADE_IN_MS = 250.0F;
    private static final float HOLD_MS = 2000.0F;
    private static final float FADE_OUT_MS = 500.0F;
    /** 被新提示顶掉时的快速淡出时长：要比自身淡出短，新提示淡入期间旧的刚好退干净 */
    private static final float RETIRE_MS = 180.0F;

    /** 峰值不透明度（不完全盖死背景） */
    private static final float PEAK_ALPHA = 0.95F;
    /** 同时最多几条（再多就丢最旧的） */
    private static final int MAX_VISIBLE = 3;
    /** 每条弹窗之间的 z 间隔（GUI 正交空间里的深度差） */
    private static final float Z_STEP = 2.0F;

    private static final class Entry {
        private final Component text;
        private final long startedAt;
        /** >0 表示已被新提示顶掉，从这个时刻开始快速淡出 */
        private long retiredAt = -1L;

        private Entry(Component text, long startedAt) {
            this.text = text;
            this.startedAt = startedAt;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private FeatherToast() {
    }

    /** 弹出一条提示：旧提示快速淡出，新提示淡入并压在它上面 */
    public static void show(Component message) {
        if (message == null) return;
        long now = System.currentTimeMillis();

        // ★ 先清掉已经播完的（包括自然淡出结束的）：它们只是"还挂在列表里等裁剪"，
        //   若在这里被当成 active 去 retire，就会被 retiredAt 重置成 alpha=1 重新亮一下
        //   ——实机观感是"上一条已经消失的文字又被压在下面强行过渡几秒"。
        prune(now);

        // 同一句还在播 → 只重置计时，不再叠一条（否则连续触发会糊成一片）
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry e = ENTRIES.get(i);
            if (e.retiredAt < 0L && e.text.equals(message)) {
                ENTRIES.set(i, new Entry(message, now));
                return;
            }
        }

        // 只让"此刻真的还看得见"的旧提示退场；已经淡完的压根不进这一轮
        for (Entry e : ENTRIES) {
            if (e.retiredAt < 0L && alphaOf(e, now) > 0.01F) e.retiredAt = now;
        }
        ENTRIES.add(new Entry(message, now));
        while (ENTRIES.size() > MAX_VISIBLE) ENTRIES.remove(0);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static boolean isVisible() {
        long now = System.currentTimeMillis();
        prune(now);
        for (Entry e : ENTRIES) {
            if (alphaOf(e, now) > 0.01F) return true;
        }
        return false;
    }

    /** 真正播完了：自然淡出走完，或退场淡出走完。这种条目不能再参与任何过渡。 */
    private static boolean isDead(Entry e, long now) {
        if (e.retiredAt >= 0L) return now - e.retiredAt >= (long) RETIRE_MS;
        return now - e.startedAt >= (long) (FADE_IN_MS + HOLD_MS + FADE_OUT_MS);
    }

    private static void prune(long now) {
        if (ENTRIES.isEmpty()) return;
        ENTRIES.removeIf(e -> isDead(e, now));
    }

    /** 在 GUI 里渲染；由屏幕的 render 末尾 / 取景器叠加层调用（这样它压在所有内容之上） */
    public static void render(GuiGraphics graphics, int width, int height) {
        if (ENTRIES.isEmpty()) return;
        long now = System.currentTimeMillis();
        prune(now);
        if (ENTRIES.isEmpty()) return;

        // 旧 → 新：顺序上新的后画，深度上新的 z 更大，双重保证"新的在最上面"
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry e = ENTRIES.get(i);
            float alpha = alphaOf(e, now);
            if (alpha <= 0.01F) continue;
            draw(graphics, width, height, e.text, alpha, (i + 1) * Z_STEP);
        }
    }

    /** 当前不透明度（含峰值系数）；已经被顶掉/播完返回 0 */
    private static float alphaOf(Entry e, long now) {
        if (e.retiredAt >= 0L) {
            float k = (now - e.retiredAt) / RETIRE_MS;
            return Mth.clamp(1.0F - k, 0.0F, 1.0F) * PEAK_ALPHA;
        }
        float elapsed = now - e.startedAt;
        float alpha;
        if (elapsed < FADE_IN_MS) {
            alpha = elapsed / FADE_IN_MS;
        } else if (elapsed < FADE_IN_MS + HOLD_MS) {
            alpha = 1.0F;
        } else if (elapsed < FADE_IN_MS + HOLD_MS + FADE_OUT_MS) {
            alpha = 1.0F - (elapsed - FADE_IN_MS - HOLD_MS) / FADE_OUT_MS;
        } else {
            alpha = 0.0F;
        }
        return Mth.clamp(alpha, 0.0F, 1.0F) * PEAK_ALPHA;
    }

    private static void draw(GuiGraphics graphics, int width, int height,
                             Component text, float alpha, float z) {
        var mc = Minecraft.getInstance();
        var font = mc.font;
        int textWidth = font.width(text);
        int boxW = textWidth + 16;
        int boxH = 16;
        int x = (width - boxW) / 2;
        int y = (int) (height * 0.62F);

        // ★ 两条通路必须**各自**承载 alpha，缺一不可：
        //   ① 底板走 `rrect` 着色器 —— 它的 uniform 是自己声明的（FillColor/Tint），
        //      **不读 ColorModulator**，所以 setColor 对底板无效，必须把 alpha 烘进色值；
        //   ② 文字走原版字体渲染 —— 色值里的 alpha 对它没产生预期效果（实测），要靠 setColor。
        int baseAlpha = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        graphics.pose().pushPose();
        // 重叠时的深度分层：新提示 z 更大，压在旧提示上面
        graphics.pose().translate(0.0F, 0.0F, z);
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        try {
            RoundedRect.roundedRect(graphics, x, y, boxW, boxH, 6.0F,
                    (baseAlpha * 3 / 4) << 24 | 0x14181F,
                    (baseAlpha * 3 / 4) << 24 | 0x0E1218,
                    (Math.round(alpha * 0.35F * 255.0F) << 24) | 0xFFFFFF, 1.0F);
            graphics.drawString(font, text, x + 8, y + 4, 0xF2F6FF, true);
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.pose().popPose();
        }
    }
}
