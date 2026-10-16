package com.plumejade.lensouls.client.photogallery;

import com.plumejade.lensouls.client.feather.RoundedRect;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 搜索框右侧的**分页滑钮**（能力窃取照片 ⇄ 照片套装）。
 *
 * <h2>三种操作都支持</h2>
 * <ol>
 *   <li><b>点击</b>（没拖动）→ 切到另一页；</li>
 *   <li><b>按住拖动</b> → 滑块跟手，松手按 {@code p > 0.5} 吸附到最近一侧并切页；</li>
 *   <li><b>位置自动变化</b> → 切页时把 {@code target} 设为 0/1，滑块平滑移动过去。</li>
 * </ol>
 *
 * <h2>平滑插值</h2>
 * {@code p += (target - p) · (1 - e^(-dt·14))} —— 指数平滑，帧率无关；拖动中 target 直接跟鼠标。
 * 这个写法与项目里其它「延迟跟位」（韧性条 {@code POS_DELAY}、引力枪弧线）同一个思路，只是这里在 UI 空间。
 */
public final class PhotoGalleryPageSlider {

    private static final float SMOOTH_RATE = 14.0F;
    private static final double CLICK_SLOP = 3.0D;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int knobWidth;
    private final int knobHeight;
    private final int padX;
    private final int padY;
    private final int radius;

    /** 0 = 能力窃取照片，1 = 照片套装 */
    private float p;
    private float target;
    private boolean dragging;
    private boolean dragMoved;
    private int pageAtDragStart;
    private double grabOffset;

    /**
     * 滑钮尺寸**只由自身宽高推导**（不再吃 k）：它现在与快捷功能按钮同高（0.40k），
     * 若还按 0.516k 算滑块，0.95k 宽的胶囊会被滑块吃掉、几乎没行程（反馈：滑钮比按钮大一圈）。
     */
    /** 滑块与轨道上下（左右）留的边距 */
    public static int padFor(int height) {
        return Math.max(2, Math.round(height * 0.09F));
    }

    /**
     * 宽度口径：**让滑块的两个静止位各占轨道一半**（行程 = 一个滑块直径）。
     * <p>
     * `width = 2·d + 2·pad` ⇒ 位 0 的滑块正好落在左半区、位 1 正好落在右半区，两者对称贴住中线。
     * 界面用这个口径算宽度，右缘与快捷功能按钮对齐、左侧自然收窄（用户口径）。
     */
    public static int widthFor(int height) {
        int pad = padFor(height);
        int d = Math.max(6, height - pad * 2);
        return 2 * d + pad * 2;
    }

    public PhotoGalleryPageSlider(int x, int y, int width, int height, int radius) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        // 胶囊圆角（height/2）会让它比上方同宽的快捷功能按钮「明显太圆」⇒ 与模块统一用界面传入的半径
        this.radius = Math.max(2, radius);
        int pad = padFor(height);
        this.padY = pad;
        this.padX = pad;
        this.knobHeight = Math.max(6, height - pad * 2);
        this.knobWidth = this.knobHeight;      // 圆形滑块
    }

    private int travel() {
        return Math.max(1, width - knobWidth - 2 * padX);
    }

    /** 当前页：0 = 照片，1 = 套装 */
    public int page() {
        return target < 0.5F ? 0 : 1;
    }

    public void setPage(int page) {
        target = page <= 0 ? 0.0F : 1.0F;
    }

    /** 用户直接拖动时调用（由切页逻辑之外的地方使用，例如初始化同步） */
    public void snapTo(int page) {
        setPage(page);
        p = target;
    }

    public void tick(float dt) {
        // 拖动中给更高的跟手速率：既跟得上鼠标，又保留一点平滑插值（不是硬跟）
        float rate = dragging ? 26.0F : SMOOTH_RATE;
        float smooth = 1.0F - (float) Math.exp(-dt * rate);
        p += (target - p) * smooth;
        if (Math.abs(target - p) < 0.0005F) p = target;
    }

    public boolean isOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * @return true = 事件已被滑钮消费
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!isOver(mouseX, mouseY)) return false;
        dragging = true;
        dragMoved = false;
        pageAtDragStart = page();
        grabOffset = mouseX - knobX();
        return true;
    }

    /** 拖动中：target 跟手，p 由 {@link #tick} 平滑追上去 */
    public void mouseDragged(double mouseX) {
        if (!dragging) return;
        if (Math.abs(mouseX - (knobX() + grabOffset)) > CLICK_SLOP) dragMoved = true;
        double offset = mouseX - grabOffset - (x + padX);
        target = (float) Math.max(0.0D, Math.min(1.0D, offset / travel()));
    }

    /**
     * 松手：点击（没拖动）→ 切到另一页；拖动 → 吸附到最近一侧。
     *
     * @return 0 = 页面没变，1 = 切到页 1（套装），-1 = 切到页 0（照片）
     */
    public int mouseReleased() {
        if (!dragging) return 0;
        dragging = false;
        int before = pageAtDragStart;
        if (!dragMoved) {
            target = before == 0 ? 1.0F : 0.0F;    // 点击 = 切页
        } else {
            target = p > 0.5F ? 1.0F : 0.0F;       // 拖动 = 按当前位置吸附
        }
        int after = page();
        if (after == before) return 0;
        return after == 1 ? 1 : -1;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hover = isOver(mouseX, mouseY);
        RoundedRect.roundedRect(graphics, x, y, width, height, radius,
                0xCC0E141C, 0xCC0A0F16, hover ? 0x66FFFFFF : 0x33FFFFFF, 1.0F);
        int knobX = knobX();
        RoundedRect.roundedRect(graphics, knobX, y + padY, knobWidth, knobHeight, knobHeight / 2.0F,
                hover || dragging ? 0xFFC9C9C9 : 0xFFA7A7A7, 0xFF8E8E8E, 0x40000000, 1.0F);   // 滑块保持圆形
    }

    private int knobX() {
        return x + padX + Math.round(Math.max(0.0F, Math.min(1.0F, p)) * travel());
    }
}
