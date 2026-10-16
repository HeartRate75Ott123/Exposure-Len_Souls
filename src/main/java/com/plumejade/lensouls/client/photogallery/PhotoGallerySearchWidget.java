package com.plumejade.lensouls.client.photogallery;

import com.plumejade.lensouls.client.feather.RoundedRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * 搜索框 + **补全弹窗**（补全部分参考 {@code searchable_ftbquests-1211} 的 {@code SuggestionPanel}）。
 *
 * <h2>参考项目里照抄的三件事</h2>
 * <ol>
 *   <li>行高 14、最多显示 6 行、超出部分内部滚动，且滚动跟随当前选中项（{@code cycleSelection}）；</li>
 *   <li>鼠标悬停即改选中项（不是「悬停只是高亮」），点击 = 应用；</li>
 *   <li>弹窗**只在搜索框聚焦时**渲染与响应 —— 否则它会吞掉网格第一行的点击（Remorphed 踩过）。</li>
 * </ol>
 *
 * <h2>两级补全</h2>
 * 照片页 = 首项「仅显示收藏夹」（动作项）+ 生物名称；套装页 = `套装:` / `tooltip:` 两个模式前缀 + 套装名。
 * 应用动作项 = 打开收藏开关并清空文本；应用普通项 = 把 payload 写进搜索框。
 */
public class PhotoGallerySearchWidget extends EditBox {

    private static final int ROW_HEIGHT = 14;
    private static final int MAX_ROWS = 6;
    /** 行高亮相对弹窗底板的内缩：足够离开 1px 描边 + SDF 抗锯齿（左右 3px、上下 2px） */
    private static final int ROW_INSET_X = 3;
    private static final int ROW_INSET_Y = 2;

    /** 补全项：{@code action = true} 表示动作项（payload 不用）；{@code keepOpen = true} 表示应用后继续给候选（模式前缀） */
    public record Suggestion(String label, String payload, boolean action, boolean keepOpen) {
        public static Suggestion text(String label, String payload) {
            return new Suggestion(label, payload, false, false);
        }

        /** 模式前缀（如 {@code 套装:}）：插入后继续显示候选，方便接着打字 */
        public static Suggestion prefix(String label, String payload) {
            return new Suggestion(label, payload, false, true);
        }

        public static Suggestion action(String label) {
            return new Suggestion(label, "", true, false);
        }
    }

    private List<Suggestion> suggestions = List.of();
    private int selected = -1;
    private int scroll;
    /** 应用补全期间为 true：调用方据此**跳过**「文本变化 → 重算候选」，否则面板会被立刻重开 */
    private boolean applyingSuggestion;
    /** 圆角半径（由界面按 k 传入，与搜索条/弹窗同一套 SDF 圆角；不传时用 3） */
    private int cornerRadius = 3;
    /** 补全面板的锚点（默认贴在 widget 底边；搜索框 widget 被内缩过，所以由界面显式给锚点） */
    private int panelAnchorX = Integer.MIN_VALUE;
    private int panelAnchorY = Integer.MIN_VALUE;
    private int panelAnchorW;
    private Consumer<Suggestion> onAccept = suggestion -> {
    };

    public PhotoGallerySearchWidget(int x, int y, int width, int height) {
        super(Minecraft.getInstance().font, x, y, width, height, Component.empty());
        setBordered(false);
        setMaxLength(48);
    }

    /**
     * 显式指定补全弹窗的位置与宽度。
     * <p>
     * 搜索框 widget 为了文字内边距被**内缩**过（左右各 6px、上下各 padV），
     * 若还按 {@code getY() + getHeight()} 当锚点，弹窗会压进搜索条底部留白里。
     */
    /** 圆角半径（与搜索条一致，弹窗底板与行高亮都用它） */
    public void setCornerRadius(int radius) {
        this.cornerRadius = Math.max(2, radius);
    }

    public void setSuggestionAnchor(int x, int y, int width) {
        this.panelAnchorX = x;
        this.panelAnchorY = y;
        this.panelAnchorW = width;
    }

    public void setOnAccept(Consumer<Suggestion> consumer) {
        this.onAccept = consumer == null ? suggestion -> {
        } : consumer;
    }

    public void setSuggestions(List<Suggestion> list) {
        this.suggestions = list == null ? List.of() : list;
        this.selected = this.suggestions.isEmpty() ? -1 : 0;
        this.scroll = 0;
    }

    public boolean hasSuggestions() {
        return !this.suggestions.isEmpty();
    }

    /** 弹窗矩形（仅聚焦时有意义） */
    private int panelX() {
        return panelAnchorX == Integer.MIN_VALUE ? getX() : panelAnchorX;
    }

    private int panelWidth() {
        return panelAnchorX == Integer.MIN_VALUE ? getWidth() : panelAnchorW;
    }

    private int panelY() {
        return panelAnchorY == Integer.MIN_VALUE ? getY() + getHeight() + 2 : panelAnchorY;
    }

    private int panelHeight() {
        return Math.min(suggestions.size(), MAX_ROWS) * ROW_HEIGHT;
    }

    private int indexAt(double mouseX, double mouseY) {
        if (!isFocused() || suggestions.isEmpty()) return -1;
        int x = panelX();
        int y = panelY();
        if (mouseX < x || mouseX >= x + panelWidth()) return -1;
        if (mouseY < y || mouseY >= y + panelHeight()) return -1;
        int row = (int) ((mouseY - y) / ROW_HEIGHT);
        int index = scroll + row;
        return index >= 0 && index < suggestions.size() ? index : -1;
    }

    /** 补全弹窗当前是否展开（聚焦且有条目） */
    public boolean isSuggestionsVisible() {
        return isFocused() && !suggestions.isEmpty();
    }

    /** 补全弹窗矩形 {x, y, w, h}；界面用它屏蔽被压住的格子浮层 */
    public int[] suggestionPanelRect() {
        return new int[]{panelX(), panelY(), panelWidth(), panelHeight()};
    }

    /**
     * 鼠标是否落在补全弹窗上 —— 界面据此**屏蔽下方的网格悬停/tooltip**
     * （反馈：列表展开时鼠标在列表区域内仍会触发下方格子的悬停 tooltip）。
     */
    public boolean mouseOverSuggestions(double mouseX, double mouseY) {
        if (!isFocused() || suggestions.isEmpty()) return false;
        return mouseX >= panelX() && mouseX < panelX() + panelWidth()
                && mouseY >= panelY() && mouseY < panelY() + panelHeight();
    }

    public void renderSuggestions(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isFocused() || suggestions.isEmpty()) return;

        int x = panelX();
        int y = panelY();
        int w = panelWidth();
        int h = panelHeight();
        // ★ 必须**不透明**：原来 0xE6（90%）会让下方网格的白字透上来，看着就是「列表和网格文字重叠」
        RoundedRect.roundedRect(graphics, x, y, w, h, cornerRadius, 0xFF10161E, 0xFF0A0F16, 0x40FFFFFF, 1.0F);

        int hovered = indexAt(mouseX, mouseY);
        if (hovered >= 0) selected = hovered;
        keepSelectionVisible();

        for (int row = 0; row < MAX_ROWS; row++) {
            int index = scroll + row;
            if (index >= suggestions.size()) break;
            int rowY = y + row * ROW_HEIGHT;
            if (index == selected) {
                // 行高亮：圆角 + **内缩**。原来只内缩 1px，SDF 边缘的抗锯齿会溢到弹窗那 1px 描边上
                // （反馈「悬停矩形稍微超出到两边底层矩形的描边上」）。左右 3px、上下 2px 内缩，
                // 并且内圆角 = 外圆角 − 内缩量（保证两层圆角是同心的）。
                RoundedRect.roundedRect(graphics, x + ROW_INSET_X, rowY + ROW_INSET_Y,
                        w - ROW_INSET_X * 2, ROW_HEIGHT - ROW_INSET_Y * 2,
                        Math.max(2, cornerRadius - ROW_INSET_X),
                        0x40FFFFFF, 0x40FFFFFF, 0, 0.0F);
            }
            Suggestion suggestion = suggestions.get(index);
            int color = suggestion.action() ? 0xFF7CFF9B : 0xFFE6ECF5;
            graphics.drawString(Minecraft.getInstance().font, suggestion.label(),
                    x + ROW_INSET_X + 3, rowY + 3, color, false);
        }
    }

    /** 点击补全：命中则应用并返回 true（调用方应据此停止后续分发） */
    public boolean mouseClickedSuggestions(double mouseX, double mouseY) {
        int index = indexAt(mouseX, mouseY);
        if (index < 0) return false;
        selected = index;
        accept(suggestions.get(index));
        return true;
    }

    public boolean mouseScrolledSuggestions(double mouseX, double mouseY, double delta) {
        if (!isFocused() || suggestions.size() <= MAX_ROWS) return false;
        if (mouseX < panelX() || mouseX >= panelX() + panelWidth()) return false;
        if (mouseY < panelY() || mouseY >= panelY() + panelHeight()) return false;
        int maxScroll = Math.max(0, suggestions.size() - MAX_ROWS);
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - Math.signum(delta)));
        return true;
    }

    /**
     * 「叠加搜索」口径（2026-10 反馈：点补全**不该清空**已输入的内容）：
     * 只处理**光标所在的这一段**（最后一个空格之后），前面已经叠好的条件原样保留。
     * <ul>
     *   <li><b>模式前缀</b>（{@code 名称:} / {@code tooltip:} / {@code 套装:} 这类以 {@code :} 结尾的）：
     *       当前这段还是半截 ⇒ 接在它前面（{@code 僵尸} → {@code 名称:僵尸}）；
     *       当前这段已是完整的「前缀:值」⇒ 新开一段（{@code 名称:僵尸} → {@code 名称:僵尸 tooltip:}）。</li>
     *   <li><b>具体条目</b>：补全当前这一段（带上已有的 {@code 名称:} 前缀），并在末尾留一个空格，
     *       于是还能接着点下一个条件继续叠加（{@code 名称:僵尸 tooltip:火}）。</li>
     * </ul>
     */
    private String compose(String payload) {
        String current = getValue() == null ? "" : getValue();
        String add = payload == null ? "" : payload;
        int tokenStart = current.lastIndexOf(' ') + 1;
        String head = current.substring(0, tokenStart);
        String token = current.substring(tokenStart);
        if (add.endsWith(":")) {
            int colon = token.indexOf(':');
            boolean tokenComplete = colon >= 0 && colon < token.length() - 1;
            return tokenComplete ? head + token + " " + add : head + add + token;
        }
        String prefix = "";
        int colon = token.indexOf(':');
        if (colon >= 0) prefix = token.substring(0, colon + 1);
        return head + prefix + add + " ";
    }

    private void keepSelectionVisible() {
        if (selected < 0) return;
        if (selected < scroll) scroll = selected;
        int maxScroll = Math.max(0, suggestions.size() - MAX_ROWS);
        if (selected >= scroll + MAX_ROWS) scroll = Math.min(maxScroll, selected - MAX_ROWS + 1);
    }

    private void accept(Suggestion suggestion) {
        applyingSuggestion = true;
        try {
            if (suggestion.action()) {
                setValue("");
                onAccept.accept(suggestion);
                setSuggestions(List.of());
                return;
            }
            setValue(compose(suggestion.payload()));
            setCursorPosition(getValue().length());
            onAccept.accept(suggestion);
            if (!suggestion.keepOpen()) setSuggestions(List.of());
        } finally {
            applyingSuggestion = false;
        }
    }

    /** 应用补全期间为 true（见字段注释） */
    public boolean isApplyingSuggestion() {
        return applyingSuggestion;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isFocused() && !suggestions.isEmpty()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_DOWN -> {
                    selected = selected < 0 ? 0 : (selected + 1) % suggestions.size();
                    keepSelectionVisible();
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    selected = selected < 0 ? suggestions.size() - 1
                            : (selected - 1 + suggestions.size()) % suggestions.size();
                    keepSelectionVisible();
                    return true;
                }
                case GLFW.GLFW_KEY_PAGE_DOWN -> {
                    selected = Math.min(suggestions.size() - 1, selected + MAX_ROWS);
                    keepSelectionVisible();
                    return true;
                }
                case GLFW.GLFW_KEY_PAGE_UP -> {
                    selected = Math.max(0, selected - MAX_ROWS);
                    keepSelectionVisible();
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (selected >= 0 && selected < suggestions.size()) {
                        // 注意：这里必须 setSuggestions 清空，否则回车后弹窗会立刻重开（responder 又填回候选）
                        accept(suggestions.get(selected));
                        return true;
                    }
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    if (isFocused()) {
                        setSuggestions(List.of());
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void setValue(@NotNull String text) {
        super.setValue(text);
        // 文本变化后旧的选中下标可能越界
        if (selected >= suggestions.size()) selected = suggestions.isEmpty() ? -1 : 0;
    }
}
