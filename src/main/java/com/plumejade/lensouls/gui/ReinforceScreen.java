package com.plumejade.lensouls.gui;

import com.plumejade.lensouls.reinforce.ReinforceClientData;
import com.plumejade.lensouls.reinforce.ReinforceClientPrefs;
import com.plumejade.lensouls.reinforce.ReinforceHelper;
import com.plumejade.lensouls.reinforce.ReinforceMaterial;
import com.plumejade.lensouls.reinforce.ReinforceSearchContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 次元强化主界面（次元锤右键打开）。版式按参考图实测比例还原：
 * <pre>
 * 参考图 1063×807 实测：  格子 74 / 列距 84（间距 10）/ 行距 91（间距 17）
 *   大方框 158×156（≈ 2.14 格），左下与网格左列对齐，底边与搜索条底边对齐
 *   搜索条 596×49，从「方框右 + 77」延伸到网格右边缘
 *   两个开关行各 28 高，右对齐到网格右缘附近（行距 50）
 *   滚动条宽 24，位于网格右侧 26 处，高度与网格一致
 *   页面底色 #1E1E1E，模块底色 #0F0F0F，滑块 #A7A7A7
 * </pre>
 * 全部比例以「格子边长」为单位换算，随窗口自适应并整体居中。
 */
public class ReinforceScreen extends Screen implements MenuAccess<ReinforceMenu> {

    // ---- 参考图比例（单位：格子边长） ----
    // 参考图列距 84/74 ≈ 1.135 由 GRID_W_R 与列数推导（见 cellX），不再单独作为乘数列距使用
    private static final float ROW_PITCH = 91f / 74f;
    private static final float BOX_R = 158f / 74f;
    private static final float BOX_H_R = 156f / 74f;
    private static final float SEARCH_X_R = 3.176f;
    private static final float SEARCH_TOP_R = 1.432f;
    private static final float SEARCH_H_R = 49f / 74f;
    private static final float GRID_TOP_R = 2.919f;
    private static final float GRID_W_R = 11.216f;
    private static final float GRID_H_R = 5.919f;
    private static final float TOGGLE_TOP_R = -0.324f;
    private static final float TOGGLE_PITCH_R = 50f / 74f;
    private static final float TOGGLE_H_R = 28f / 74f;
    private static final float SCROLL_W_R = 24f / 74f;
    private static final float SCROLL_GAP_R = 26f / 74f;
    private static final float CONTENT_W_R = GRID_W_R + SCROLL_GAP_R + SCROLL_W_R;
    private static final float CONTENT_H_R = GRID_TOP_R + GRID_H_R - TOGGLE_TOP_R;

    // ---- 配色（参考图取色；面板留一点透明度，让背景枝条像"液态玻璃"一样透出来） ----
    private static final int PAGE_BG = 0xFF1E1E1E;
    private static final int MODULE_BG = 0xD80F0F0F;
    private static final int MODULE_BG_HOVER = 0xE62A2A2A;
    private static final int BORDER = 0xFF3C3C3C;
    private static final int BORDER_HOVER = 0xFF9A9A9A;
    private static final int TEXT = 0xFFFDFEFD;
    private static final int TEXT_DIM = 0xFFB0B0B0;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int THUMB = 0xFFA7A7A7;
    private static final int CELL_OVERLAY = 0xB3000000;

    private static final ResourceLocation STAR_FULL =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/gui/staricon.png");
    private static final ResourceLocation STAR_EMPTY =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/gui/emptystaricon.png");

    private static final int COLS = 10;
    private static final int ROWS = 5;

    private final ReinforceMenu reinforceMenu;
    private Layout layout;
    private EditBox searchBox;
    private String lastQuery = "\u0000";

    private float scrollOffset;
    private boolean scrollDragging;
    private float dragStartOffset;
    private double dragStartY;

    private final List<Entry> visible = new ArrayList<>();
    private int lastSelectedSlot = Integer.MIN_VALUE;
    private int lastCountsStamp = -1;

    private Entry hoveredEntry;
    private boolean hoveringStar;

    /** 一帧内固定下来的版式（单位：GUI 像素）。 */
    private record Layout(int cell, int originX, int originY,
                          int boxX, int boxY, int boxW, int boxH,
                          int searchX, int searchY, int searchW, int searchH,
                          int toggleX, int toggleTop, int toggleW, int toggleH,
                          int gridX, int gridY, int gridW, int gridH,
                          int scrollX, int scrollY, int scrollW, int scrollH) {
    }

    private record Entry(int index, ResourceLocation id, ItemStack stack, int count,
                         boolean favorite, boolean used) {
    }

    public ReinforceScreen(ReinforceMenu menu, net.minecraft.world.entity.player.Inventory inventory, Component title) {
        super(title);
        this.reinforceMenu = menu;
    }

    /**
     * 只是为了让菜单能通过 {@code MenuScreens.register} 绑到这个屏幕上。
     * <p>
     * 屏幕本身是<b>普通 {@link Screen}</b>（不是 {@code AbstractContainerScreen}）——
     * JEI 的所有 GUI 接入（物品列表覆盖层、点击查配方/用途、作弊、GUI 区域、配方转移按钮）
     * 都以 {@code screen instanceof AbstractContainerScreen} 为判据，做成普通 Screen 后
     * JEI 完全不会介入（这正是参考项目 gytrinket 的 H 面板的做法）。
     */
    @Override
    public ReinforceMenu getMenu() {
        return this.reinforceMenu;
    }

    // ========== 版式计算 ==========

    private Layout computeLayout() {
        int margin = 6;
        float cellW = (this.width - margin * 2) / CONTENT_W_R;
        float cellH = (this.height - margin * 2) / CONTENT_H_R;
        int cell = Math.max(14, Math.min(40, (int) Math.floor(Math.min(cellW, cellH))));

        int contentW = Math.round(CONTENT_W_R * cell);
        int contentH = Math.round(CONTENT_H_R * cell);
        int originX = (this.width - contentW) / 2;
        int originY = (this.height - contentH) / 2;

        int gridX = originX;
        int gridW = Math.round(GRID_W_R * cell);
        int gridH = Math.round(GRID_H_R * cell);
        int gridY = originY + Math.round((GRID_TOP_R - TOGGLE_TOP_R) * cell);

        int boxY = originY + Math.round(-TOGGLE_TOP_R * cell);
        int boxW = Math.round(BOX_R * cell);
        int boxH = Math.round(BOX_H_R * cell);

        int searchX = originX + Math.round(SEARCH_X_R * cell);
        int searchY = boxY + Math.round(SEARCH_TOP_R * cell);
        int searchW = Math.max(60, originX + gridW - searchX);
        int searchH = Math.max(12, Math.round(SEARCH_H_R * cell));

        int toggleW = Math.round(cell * 5.5f);
        int toggleH = Math.max(10, Math.round(TOGGLE_H_R * cell));
        int toggleX = originX + gridW - Math.round(cell * 0.27f) - toggleW;

        int scrollX = originX + gridW + Math.round(SCROLL_GAP_R * cell);
        int scrollW = Math.max(4, Math.round(SCROLL_W_R * cell));

        return new Layout(cell, originX, originY,
                gridX, boxY, boxW, boxH,
                searchX, searchY, searchW, searchH,
                toggleX, originY, toggleW, toggleH,
                gridX, gridY, gridW, gridH,
                scrollX, gridY, scrollW, gridH);
    }

    private int rowPitch() {
        return Math.round(ROW_PITCH * this.layout.cell());
    }

    /**
     * 第 col 列的左边缘。
     * <p>
     * 列距<b>不能</b>写成 {@code round(COL_PITCH * cell)} 再乘列号：那样 9 个列距各自取整后的累积误差
     * 会让 {@code 9*pitch + cell} 超出 {@code gridW}（cell=20 时 9*23+20=227 &gt; gridW=224，
     * cell=19/21/26~29/34~36 同样溢出），而滚动区裁剪矩形正是 {@code [gridX, gridX+gridW)}，
     * 于是最右一列被切掉一条边 —— 表现就是"最后一列不是方形"。
     * <p>
     * 这里改为把可分配宽度 {@code gridW - cell} 平均摊到 9 个间隔上，最后一列的右边缘精确落在网格右边界：
     * 既保证格子恒为 {@code cell × cell} 正方形，也不改动由 {@code gridW} 推导的搜索框/滚动条对位。
     */
    private int cellX(int col) {
        int span = this.layout.gridW() - this.layout.cell();
        return this.layout.gridX() + Math.round(col * span / (float) (COLS - 1));
    }

    private int cellY(int row) {
        return this.layout.gridY() + row * rowPitch() - (int) this.scrollOffset;
    }

    private int totalRows() {
        return (this.visible.size() + COLS - 1) / COLS;
    }

    private float maxScroll() {
        int rows = totalRows();
        if (rows <= ROWS) return 0;
        int gridH = this.layout.gridH();
        int contentH = (rows - 1) * rowPitch() + this.layout.cell();
        return Math.max(0, contentH - gridH);
    }

    private boolean isOverCell(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + this.layout.cell() && mouseY >= y && mouseY < y + this.layout.cell();
    }

    private boolean isOverStar(double mouseX, double mouseY, int x, int y) {
        int size = starSize();
        int starX = x + this.layout.cell() - size - starPad();
        int starY = y + starPad();
        return mouseX >= starX - size * 0.35 && mouseX < starX + size * 1.35
                && mouseY >= starY - size * 0.35 && mouseY < starY + size * 1.35;
    }

    private int starSize() {
        return Math.max(7, Math.round(this.layout.cell() * 0.22f));
    }

    private int starPad() {
        return Math.max(1, Math.round(this.layout.cell() * 0.06f));
    }

    // ========== 初始化 ==========

    @Override
    protected void init() {
        super.init();
        this.layout = computeLayout();
        this.scrollOffset = 0;

        String restored = ReinforceClientPrefs.lastSearch();
        this.searchBox = new EditBox(this.font,
                this.layout.searchX() + 4, this.layout.searchY() + (this.layout.searchH() - 8) / 2,
                this.layout.searchW() - 8, 8, Component.translatable("screen.lensouls.reinforce.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(TEXT);
        this.searchBox.setTextColorUneditable(TEXT_DIM);
        this.searchBox.setMaxLength(64);
        this.searchBox.setValue(restored);
        this.searchBox.setResponder(text -> {
            ReinforceClientPrefs.setLastSearch(text);
            this.lastQuery = "\u0000";   // 下一帧重建列表
        });
        // 打开界面自动聚焦 + 上次内容全选（输入即覆盖）
        this.searchBox.setFocused(true);
        this.searchBox.setCursorPosition(restored.length());
        this.searchBox.setHighlightPos(0);

        ReinforceSearchContext.ensureLoadedAsync();
        rebuildVisible();
    }

    // ========== 列表 ==========

    private void rebuildVisible() {
        this.visible.clear();
        Set<String> query = ReinforceSearchContext.matches(this.searchBox == null ? "" : this.searchBox.getValue());
        ItemStack target = this.reinforceMenu.getSelectedStack();
        List<ReinforceMaterial> materials = ReinforceClientData.materials();

        for (int i = 0; i < materials.size(); i++) {
            ReinforceMaterial material = materials.get(i);
            if (query != null && !query.contains(material.id().toString())) continue;
            Item item = ReinforceClientData.itemOf(material.id());
            if (item == null) continue;   // 数据包里有、当前环境没注册的物品不显示
            boolean used = !target.isEmpty() && ReinforceHelper.isUsed(target, material.id());
            if (used && !ReinforceClientPrefs.showReinforced()) continue;
            boolean favorite = ReinforceClientPrefs.isFavorite(material.id());
            if (ReinforceClientPrefs.favoritesOnly() && !favorite) continue;
            this.visible.add(new Entry(i, material.id(), new ItemStack(item),
                    ReinforceClientData.countAt(i), favorite, used));
        }
        // 收藏置顶；stable sort 保证取消收藏后回到默认排列位置
        this.visible.sort(Comparator.comparingInt(entry -> entry.favorite() ? 0 : 1));
        this.scrollOffset = Math.max(0, Math.min(maxScroll(), this.scrollOffset));
    }

    private void refreshListIfNeeded() {
        int selected = this.reinforceMenu.getSelectedSlot();
        int stamp = ReinforceClientData.stamp();
        String query = this.searchBox == null ? "" : this.searchBox.getValue();
        if (selected != this.lastSelectedSlot || stamp != this.lastCountsStamp || !query.equals(this.lastQuery)) {
            this.lastSelectedSlot = selected;
            this.lastCountsStamp = stamp;
            this.lastQuery = query;
            rebuildVisible();
        }
    }

    // ========== 渲染 ==========

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshListIfNeeded();
        this.hoveredEntry = null;
        this.hoveringStar = false;

        // 全屏背景：银白液态玻璃枝条着色器；加载失败时回退纯色
        if (!com.plumejade.lensouls.client.render.ReinforceBackground.render(
                graphics, this.minecraft, partialTick, this.width, this.height)) {
            renderTransparentBackground(graphics);
            graphics.fill(0, 0, this.width, this.height, PAGE_BG);
        }

        renderTargetBox(graphics, mouseX, mouseY);
        renderSearchBox(graphics, mouseX, mouseY);
        renderToggles(graphics, mouseX, mouseY);
        renderGrid(graphics, mouseX, mouseY);
        renderScrollbar(graphics);

        if (this.hoveredEntry != null) {
            renderEntryTooltip(graphics, this.hoveredEntry, mouseX, mouseY);
        } else if (isOverTargetBox(mouseX, mouseY)) {
            ItemStack target = this.reinforceMenu.getSelectedStack();
            if (!target.isEmpty()) graphics.renderTooltip(this.font, target, mouseX, mouseY);
        }
    }

    /** 大方框：选中物品（放大居中）+ 右下角数量，未选中时显示提示文字。 */
    private void renderTargetBox(GuiGraphics graphics, int mouseX, int mouseY) {
        Layout l = this.layout;
        int x = l.boxX();
        int y = l.boxY();
        int w = l.boxW();
        int h = l.boxH();
        boolean hovered = isOverTargetBox(mouseX, mouseY);
        graphics.fill(x, y, x + w, y + h, hovered ? MODULE_BG_HOVER : MODULE_BG);
        outline(graphics, x, y, w, h, hovered ? BORDER_HOVER : BORDER);

        ItemStack target = this.reinforceMenu.getSelectedStack();
        if (target.isEmpty()) {
            drawWrappedCentered(graphics, Component.translatable("screen.lensouls.reinforce.empty_slot"),
                    x + 4, y + 4, w - 8, h - 8, TEXT_DIM);
            return;
        }
        // 物品本体只画一次（居中、按方框尺寸放大）
        float scale = Math.min(w, h) * 0.5f / 16f;
        graphics.pose().pushPose();
        graphics.pose().translate(x + w / 2f, y + h / 2f, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(target, -8, -8);
        graphics.pose().popPose();
        // 数量/耐久：1 倍尺寸画在方框右下角（renderItemDecorations 只画角标，不重复画物品）
        graphics.renderItemDecorations(this.font, target, x + w - 18, y + h - 11);
    }

    private void renderSearchBox(GuiGraphics graphics, int mouseX, int mouseY) {
        Layout l = this.layout;
        boolean focused = this.searchBox != null && this.searchBox.isFocused();
        graphics.fill(l.searchX(), l.searchY(), l.searchX() + l.searchW(), l.searchY() + l.searchH(), MODULE_BG);
        outline(graphics, l.searchX(), l.searchY(), l.searchW(), l.searchH(), focused ? BORDER_HOVER : BORDER);
        if (this.searchBox != null) {
            if (this.searchBox.getValue().isEmpty()) {
                graphics.drawString(this.font, Component.translatable("screen.lensouls.reinforce.search"),
                        l.searchX() + 5, l.searchY() + (l.searchH() - 8) / 2, TEXT_DIM, false);
            }
            this.searchBox.render(graphics, mouseX, mouseY, 0.0F);
        }
    }

    private void renderToggles(GuiGraphics graphics, int mouseX, int mouseY) {
        renderToggle(graphics, mouseX, mouseY, true);
        renderToggle(graphics, mouseX, mouseY, false);
    }

    private void renderToggle(GuiGraphics graphics, int mouseX, int mouseY, boolean showReinforced) {
        Layout l = this.layout;
        int row = showReinforced ? 0 : 1;
        int y = l.toggleTop() + Math.round(row * TOGGLE_PITCH_R * l.cell());
        int square = Math.max(8, Math.round(l.cell() * 0.23f));
        int squareX = l.toggleX() + l.toggleW() - square;
        int squareY = y + (l.toggleH() - square) / 2;
        boolean on = showReinforced ? ReinforceClientPrefs.showReinforced() : ReinforceClientPrefs.favoritesOnly();
        boolean hovered = isOverToggle(mouseX, mouseY, showReinforced);

        Component label = Component.translatable(showReinforced
                ? "gui.lensouls.reinforce.show_reinforced" : "gui.lensouls.reinforce.favorites_only");
        int labelX = l.toggleX() + l.toggleW() - square - Math.round(l.cell() * 0.2f) - this.font.width(label);
        graphics.drawString(this.font, label, labelX, y + (l.toggleH() - 8) / 2,
                hovered ? TEXT : TEXT_DIM, false);

        graphics.fill(squareX, squareY, squareX + square, squareY + square, on ? GREEN : MODULE_BG);
        outline(graphics, squareX, squareY, square, on ? GREEN : BORDER);
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        Layout l = this.layout;
        int cell = l.cell();
        graphics.pose().pushPose();
        graphics.enableScissor(l.gridX(), l.gridY(), l.gridX() + l.gridW(), l.gridY() + l.gridH());

        int rows = totalRows();
        outer:
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                int x = cellX(col);
                int y = cellY(row);
                if (y + cell < l.gridY()) continue;
                if (y > l.gridY() + l.gridH()) break outer;

                Entry entry = index < this.visible.size() ? this.visible.get(index) : null;
                boolean hovered = entry != null && isOverCell(mouseX, mouseY, x, y);
                graphics.fill(x, y, x + cell, y + cell, hovered ? MODULE_BG_HOVER : MODULE_BG);
                if (hovered) outline(graphics, x, y, cell, BORDER_HOVER);

                if (entry == null) continue;
                renderCellContents(graphics, entry, x, y, cell);
                if (hovered) {
                    this.hoveredEntry = entry;
                    this.hoveringStar = isOverStar(mouseX, mouseY, x, y);
                }
            }
        }

        graphics.disableScissor();
        graphics.pose().popPose();

        if (this.visible.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.lensouls.reinforce.none_found"),
                    l.gridX() + l.gridW() / 2, l.gridY() + l.gridH() / 2 - 4, TEXT_DIM);
        }
    }

    private void renderCellContents(GuiGraphics graphics, Entry entry, int x, int y, int cell) {
        // 物品：按格子大小放大（不小于 1 倍，避免小窗口下比原版图标还小），保持居中
        float scale = Math.max(1.0F, cell * 0.54f / 16f);
        graphics.pose().pushPose();
        graphics.pose().translate(x + cell / 2f, y + cell / 2f, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(entry.stack(), -8, -8);
        graphics.pose().popPose();

        // 角标/「已强化」必须在物品之上：物品在 z≈100~150 绘制并写深度，
        // 同在 z=0 的 fill/drawString 会被物品盖住，因此整体抬到 z=200。
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        if (entry.used()) {
            graphics.fill(x + 1, y + 1, x + cell - 1, y + cell - 1, CELL_OVERLAY);
            drawScaledCentered(graphics, Component.translatable("gui.lensouls.reinforce.already"),
                    x + 1, y + 1, cell - 2, cell - 2, GREEN);
        } else {
            drawCount(graphics, x, y, cell, entry.count());
        }

        // 收藏星：格子右上角（同样抬到物品之上）
        int size = starSize();
        int starX = x + cell - size - starPad();
        int starY = y + starPad();
        graphics.blit(entry.favorite() ? STAR_FULL : STAR_EMPTY, starX, starY, 0, 0, size, size, size, size);
        graphics.pose().popPose();
        // 悬停收藏星不再画绿框：只靠 tooltip 文案变化（点击收藏 / 点击取消收藏）提示
    }

    private void drawCount(GuiGraphics graphics, int x, int y, int cell, int count) {
        String text = count > 999 ? "999+" : String.valueOf(count);
        int textWidth = this.font.width(text);
        float maxWidth = cell - 3f;
        float scale = textWidth <= 0 ? 1.0F : Math.min(1.0F, Math.max(0.5F, maxWidth / textWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(x + cell - 2, y + cell - 9, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, -textWidth, 0, count > 0 ? GREEN : RED, true);
        graphics.pose().popPose();
    }

    private void renderScrollbar(GuiGraphics graphics) {
        Layout l = this.layout;
        float max = maxScroll();
        if (max <= 0) return;
        int x = l.scrollX();
        int w = l.scrollW();
        graphics.fill(x, l.scrollY(), x + w, l.scrollY() + l.scrollH(), MODULE_BG);
        float viewport = l.gridH();
        float content = (totalRows() - 1) * rowPitch() + (float) l.cell();
        float thumbH = Math.max(l.cell() * 0.6f, viewport * viewport / content);
        float thumbY = l.scrollY() + (this.scrollOffset / max) * (viewport - thumbH);
        graphics.fill(x, (int) thumbY, x + w, (int) (thumbY + thumbH), THUMB);
    }

    private void renderEntryTooltip(GuiGraphics graphics, Entry entry, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        if (this.hoveringStar) {
            lines.add(Component.translatable(entry.favorite()
                    ? "gui.lensouls.reinforce.unfavorite" : "gui.lensouls.reinforce.favorite"));
        } else {
            lines.addAll(entry.stack().getTooltipLines(tooltipContext(), tooltipPlayer(), TooltipFlag.Default.NORMAL));
            lines.add(Component.translatable("gui.lensouls.reinforce.count", entry.count())
                    .withStyle(entry.count() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (entry.used()) {
                lines.add(Component.translatable("gui.lensouls.reinforce.already").withStyle(ChatFormatting.GREEN));
            }
        }
        graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    // ========== 命中判定 ==========

    private boolean isOverTargetBox(double mouseX, double mouseY) {
        Layout l = this.layout;
        return mouseX >= l.boxX() && mouseX < l.boxX() + l.boxW()
                && mouseY >= l.boxY() && mouseY < l.boxY() + l.boxH();
    }

    private boolean isOverSearchBox(double mouseX, double mouseY) {
        Layout l = this.layout;
        return mouseX >= l.searchX() && mouseX < l.searchX() + l.searchW()
                && mouseY >= l.searchY() && mouseY < l.searchY() + l.searchH();
    }

    private boolean isOverToggle(double mouseX, double mouseY, boolean showReinforced) {
        Layout l = this.layout;
        int y = l.toggleTop() + (showReinforced ? 0 : Math.round(TOGGLE_PITCH_R * l.cell()));
        return mouseX >= l.toggleX() && mouseX < l.toggleX() + l.toggleW()
                && mouseY >= y && mouseY < y + l.toggleH();
    }

    private boolean isOverScrollbar(double mouseX, double mouseY) {
        Layout l = this.layout;
        return mouseX >= l.scrollX() && mouseX < l.scrollX() + l.scrollW()
                && mouseY >= l.scrollY() && mouseY < l.scrollY() + l.scrollH();
    }

    private Entry entryAt(double mouseX, double mouseY) {
        for (int row = 0; row < totalRows(); row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                if (index >= this.visible.size()) return null;
                if (isOverCell(mouseX, mouseY, cellX(col), cellY(row))) return this.visible.get(index);
            }
        }
        return null;
    }

    private int[] cellPosOf(Entry entry) {
        int index = this.visible.indexOf(entry);
        if (index < 0) return null;
        return new int[]{cellX(index % COLS), cellY(index / COLS)};
    }

    // ========== 鼠标 / 键盘 ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 搜索框：左键聚焦，右键清空
        if (isOverSearchBox(mouseX, mouseY)) {
            if (button == 1) {
                this.searchBox.setValue("");
                this.searchBox.setHighlightPos(this.searchBox.getCursorPosition());
                return true;
            }
            this.searchBox.setFocused(true);
            this.searchBox.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (this.searchBox != null) this.searchBox.setFocused(false);

        if (isOverToggle(mouseX, mouseY, true)) {
            ReinforceClientPrefs.setShowReinforced(!ReinforceClientPrefs.showReinforced());
            rebuildVisible();
            return true;
        }
        if (isOverToggle(mouseX, mouseY, false)) {
            ReinforceClientPrefs.setFavoritesOnly(!ReinforceClientPrefs.favoritesOnly());
            rebuildVisible();
            return true;
        }

        // 大方框 → 单独打开选物界面
        if (isOverTargetBox(mouseX, mouseY)) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ReinforceSelectScreen(this, this.reinforceMenu));
            }
            return true;
        }

        // 滚动条
        if (maxScroll() > 0 && isOverScrollbar(mouseX, mouseY)) {
            float thumbY = thumbY();
            float thumbH = thumbHeight();
            if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                this.scrollDragging = true;
                this.dragStartOffset = this.scrollOffset;
                this.dragStartY = mouseY;
            } else {
                float viewport = this.layout.gridH();
                float max = maxScroll();
                float target = (float) (mouseY - this.layout.scrollY() - thumbH / 2) / (viewport - thumbH) * max;
                this.scrollOffset = Math.max(0, Math.min(max, target));
            }
            return true;
        }

        // 材料格：星标切换收藏，其余请求强化
        Entry entry = entryAt(mouseX, mouseY);
        if (entry != null) {
            int[] pos = cellPosOf(entry);
            if (pos != null && isOverStar(mouseX, mouseY, pos[0], pos[1])) {
                ReinforceClientPrefs.toggleFavorite(entry.id());
                rebuildVisible();
            } else if (!entry.used()) {
                PacketDistributor.sendToServer(
                        new com.plumejade.lensouls.network.ReinforceApplyPacket(entry.id()));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private float thumbHeight() {
        float viewport = this.layout.gridH();
        float content = (totalRows() - 1) * rowPitch() + (float) this.layout.cell();
        return Math.max(this.layout.cell() * 0.6f, viewport * viewport / content);
    }

    private float thumbY() {
        float viewport = this.layout.gridH();
        float max = maxScroll();
        if (max <= 0) return this.layout.scrollY();
        return this.layout.scrollY() + (this.scrollOffset / max) * (viewport - thumbHeight());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            this.scrollOffset = Math.max(0, Math.min(maxScroll(),
                    this.scrollOffset - (float) scrollY * this.layout.cell()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrollDragging) {
            float viewport = this.layout.gridH();
            float thumbH = thumbHeight();
            float max = maxScroll();
            if (viewport - thumbH <= 0) return true;
            float delta = (float) (mouseY - this.dragStartY);
            this.scrollOffset = Math.max(0, Math.min(max,
                    this.dragStartOffset + delta * (max / (viewport - thumbH))));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.scrollDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (keyCode == 257 || keyCode == 335) {   // Enter：收起焦点
                this.searchBox.setFocused(false);
                return true;
            }
        }
        // 普通 Screen 不会自动处理背包键（原版只对 AbstractContainerScreen 生效），手动关界面
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()
                && this.searchBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (this.searchBox != null) ReinforceClientPrefs.setLastSearch(this.searchBox.getValue());
        ReinforceClientPrefs.persist();
        // 普通 Screen 的 onClose 不会关闭容器菜单，必须显式关闭（否则服务端菜单一直挂着）
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.closeContainer();
        }
        super.onClose();
    }

    /** 菜单在别处被关掉（死亡 / 服务端关闭）时主动退屏，避免停在空屏上。 */
    @Override
    public void tick() {
        super.tick();
        if (this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.containerMenu != this.reinforceMenu) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========== 绘制辅助 ==========

    private void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void outline(GuiGraphics graphics, int x, int y, int size, int color) {
        outline(graphics, x, y, size, size, color);
    }

    private void drawScaledCentered(GuiGraphics graphics, Component text, int x, int y, int w, int h, int color) {
        int textWidth = this.font.width(text);
        float scale = textWidth <= 0 ? 1.0F : Math.min(1.0F, Math.max(0.5F, (w - 1f) / textWidth));
        graphics.pose().pushPose();
        graphics.pose().translate(x + w / 2f, y + h / 2f, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, -textWidth / 2, -4, color, false);
        graphics.pose().popPose();
    }

    private void drawWrappedCentered(GuiGraphics graphics, Component text, int x, int y, int w, int h, int color) {
        List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(text, w);
        int startY = y + (h - lines.size() * 10) / 2;
        for (int i = 0; i < lines.size(); i++) {
            net.minecraft.util.FormattedCharSequence line = lines.get(i);
            graphics.drawString(this.font, line, x + (w - this.font.width(line)) / 2, startY + i * 10, color, false);
        }
    }

    private TooltipContext tooltipContext() {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        return level == null ? TooltipContext.EMPTY : TooltipContext.of(level);
    }

    private Player tooltipPlayer() {
        return Minecraft.getInstance().player;
    }
}
