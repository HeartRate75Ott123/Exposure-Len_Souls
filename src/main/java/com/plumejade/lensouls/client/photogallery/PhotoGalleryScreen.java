package com.plumejade.lensouls.client.photogallery;

import com.plumejade.lensouls.client.PhotoBadgeIcon;
import com.plumejade.lensouls.client.feather.CloseButton;
import com.plumejade.lensouls.client.feather.CursorKeeper;
import com.plumejade.lensouls.client.feather.FeatherToast;
import com.plumejade.lensouls.client.feather.GlassPanel;
import com.plumejade.lensouls.client.feather.RoundedRect;
import com.plumejade.lensouls.gui.PhotoGalleryMenu;
import com.plumejade.lensouls.item.EntityPhotographItem;
import com.plumejade.lensouls.network.PhotoGalleryActionPacket;
import com.plumejade.lensouls.network.PhotoGalleryShortcutPacket;
import com.plumejade.lensouls.photogallery.PhotoGalleryData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 照片图鉴界面（{@code /lensouls photos}）。
 *
 * <h2>视觉与层级（与羽毛装配页同一套材质）</h2>
 * <pre>
 * cover 背景 → graphics.flush() → 磨砂玻璃面板 → 描边 / 模块 / 格子底
 *   → 悬停光带（在模型**之下**）→ 生物模型（每格 scissor）→ 清深度 + 关深度测试
 *   → 遮罩 / 名称条 / 收藏星 / 套装进度 / 滚动条 / 补全面板 / 快捷功能面板 / tooltip
 *   → FeatherToast → CloseButton
 * </pre>
 * 深度清理由是必须的：{@code InventoryScreen.renderEntityInInventory} 会把模型写进深度缓冲，
 * 不清理的话模型之外的 GUI 文字会被实体深度遮挡而消失（Remorphed 已实测）。
 *
 * <h2>版式（2026-10 调参：上一版「网格过小 + 文字出框」）</h2>
 * 唯一缩放单位 {@code k}（格子边长），一切尺寸由 k 推导（见 {@link #layout()}）。
 * 相比首版：压缩了非网格区（开关行 0.46k、行距 0.11k、搜索条 0.60k、上下内边距 0.34/0.40k），
 * 把省下来的高度全部让给格子 ⇒ 640×360 下 k 从 39 → 48，960×540 下 56 → 74。
 * 格子内所有文字一律走 {@link #drawFitted} / {@link #ellipsize}，保证**永不出框**。
 */
public class PhotoGalleryScreen extends Screen implements MenuAccess<PhotoGalleryMenu> {

    // ===== 版式常数（单位 k；与 docs/照片图鉴界面设计.md §6.1 的实测值成比例）=====
    /**
     * 面板占窗口的比例 —— **这是「区域设定」的硬约束，不再动**。
     * <p>
     * 直接来自参考图实测：面板 1179×860 / 画布 1920×1080 = **0.614 × 0.796**。
     * 之前为了「把格子放大」去抬 k 的上限，结果把面板撑到接近满屏、背景几乎全被磨砂糊掉 ——
     * 正确做法是**面板区域固定在参考图比例**，格子大小由「面板区域 ÷ 版式常数」推导；
     * 想放大格子只能压非网格区（按钮/搜索条/行距），不能动面板。
     */
    private static final float PANEL_W_RATIO = 0.614F;
    private static final float PANEL_H_RATIO = 0.796F;

    /**
     * 版式常数（单位 k）。面板宽 = `0.70 + 6.92 + 0.70` = **8.32k**；
     * 面板高 = `0.30 + 0.40×2 + 0.10×2 + 0.40 + 0.14 + 4.311 + 0.32` = **6.47k**（可见 4 行）。
     * <p>
     * 所有「按钮类」模块（两行开关 / 快捷功能按钮 / 搜索条 / 页滑钮）**统一同高 0.40k** ——
     * 上一版搜索条 0.50k 而快捷功能 0.34k，页滑钮看着比按钮大一圈（反馈原文：为什么切换页面的按钮那么大）。
     */
    private static final float K_W = 8.32F;
    private static final float K_H = 6.47F;
    private static final float CONTENT_W = 6.92F;
    private static final float PAD_L = 0.70F;
    private static final float PAD_R = 0.70F;
    private static final float PAD_T = 0.30F;
    private static final float PAD_B = 0.32F;
    private static final float ROW_H = 0.40F;
    private static final float ROW_GAP = 0.10F;
    private static final float SEARCH_W = 5.85F;
    private static final float SEARCH_H = 0.40F;
    // 页滑钮不写死宽度：用 PhotoGalleryPageSlider.widthFor(rowH) 按「两个静止位各占一半」推导，
    // 右缘与快捷功能按钮对齐、左侧收窄（用户口径），高度与按钮同为 ROW_H。
    private static final float GRID_INSET = 0.098F;
    private static final float SEARCH_TO_GRID = 0.14F;
    private static final float CELL_H = 0.918F;
    private static final float COL_PITCH = 1.164F;
    private static final float ROW_PITCH = 1.131F;
    private static final float SCROLLBAR_W = 0.26F;
    private static final float SCROLLBAR_GAP = 0.30F;
    private static final float QUICK_W = 1.24F;
    private static final float CHECK_W = 0.26F;
    private static final float CHECK_H = 0.26F;
    private static final float CHECK_GAP = 0.14F;
    private static final float SHORTCUT_ROW_H = 0.34F;
    /** 搜索框文字的内边距：左右固定 6px，上下按 k 缩放（见 init()，保证文字在条内垂直居中） */
    private static final int TEXT_PAD_X = 6;
    private static final int TEXT_PAD_Y_MIN = 2;
    /** 格子底部名称条高度（模型可用高度 = 格子高 − 名称条 − 上下各 2px 余量） */
    private static final int NAME_STRIP_H = 10;
    private static final int COLS = 5;
    private static final int VISIBLE_ROWS = 4;
    private static final float K_MIN = 20.0F;
    private static final float K_MAX = 96.0F;

    private static final ResourceLocation STAR = ResourceLocation.fromNamespaceAndPath(
            "lensouls", "textures/gui/staricon.png");
    private static final ResourceLocation EMPTY_STAR = ResourceLocation.fromNamespaceAndPath(
            "lensouls", "textures/gui/emptystaricon.png");
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "lensouls", "textures/gui/photo_gallery_background.png");
    private static final int BG_W = 1920;
    private static final int BG_H = 1080;
    private static final int STAR_SIZE = 9;

    private final PhotoGalleryMenu menu;
    private final Player player;

    // ===== 状态 =====
    /** 0 = 能力窃取照片，1 = 照片套装 */
    private int page;
    /** 开关行 1：是否显示未解锁（**默认勾选**，两页各自一份） */
    private final boolean[] showLocked = {true, true};
    private final boolean[] favoritesOnly = {false, false};
    private final String[] searchText = {"", ""};
    private List<GalleryEntry> entries = List.of();
    private float scroll;
    private boolean scrollDragging;
    private double scrollGrabOffset;
    private int hoveredIndex = -1;
    private final Set<String> crashedModels = new HashSet<>();
    private int lastSeenRevision = Integer.MIN_VALUE;
    private long lastFrameNanos;
    private int toggleHover = -1;

    // ===== 版式计算出来的矩形 =====
    private float k;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentX;
    private int contentY;
    private int row1Y;
    private int row2Y;
    private int rowH;
    private int checkboxX;
    private int checkboxY1;
    private int checkboxY2;
    private int checkboxW;
    private int checkboxH;
    private int searchX;
    private int searchY;
    private int searchW;
    private int searchH;
    private int gridX;
    private int gridY;
    private int cellW;
    private int cellH;
    private int colPitch;
    private int rowPitch;
    private int gridW;
    private int gridH;
    private int scrollbarX;
    private int scrollbarW;
    private int quickX;
    private int quickY;
    private int quickW;
    private int quickH;

    private PhotoGallerySearchWidget search;
    private PhotoGalleryPageSlider slider;
    private PhotoGalleryShortcutPanel shortcuts;

    public PhotoGalleryScreen(PhotoGalleryMenu menu, Inventory inventory, Component title) {
        super(title);
        this.menu = menu;
        this.player = inventory.player;
    }

    @Override
    public PhotoGalleryMenu getMenu() {
        return this.menu;
    }

    @Override
    protected void init() {
        super.init();
        layout();

        // 搜索框 widget 四边内缩：EditBox 无边框时文字**贴 widget 左缘**、且按 widget 高度垂直居中，
        // 所以把 widget 内缩出内边距，文字自然落在条内且居中（补全面板锚点另行显式指定，见下）
        int padY = Math.max(TEXT_PAD_Y_MIN, (searchH - 10) / 2);
        this.search = new PhotoGallerySearchWidget(searchX + TEXT_PAD_X, searchY + padY,
                searchW - TEXT_PAD_X * 2, searchH - padY * 2);
        this.search.setSuggestionAnchor(searchX, searchY + searchH + 2, searchW);
        this.search.setCornerRadius(radiusModule());
        this.search.setTextColor(0xFFF2F6FF);
        this.search.setTextColorUneditable(0xFFAEB6C4);
        this.search.setValue(searchText[page]);
        this.search.setResponder(text -> {
            searchText[page] = text;
            repopulate();
            // 应用补全时不要再重算候选（否则面板会被立刻重开）
            if (!this.search.isApplyingSuggestion()) refreshSuggestions();
        });
        this.search.setOnAccept(suggestion -> {
            // 列表里已无「动作项」（仅显示收藏夹改由开关行控制），这里只负责应用文本/前缀
            repopulate();
            if (suggestion.keepOpen()) refreshSuggestions();
        });
        updateSearchHint();
        // 刻意**不**自动聚焦：补全弹窗只在「点进搜索框 / 开始打字」后才展开，
        // 否则一开屏弹窗就压住网格第一行（反馈：展开的列表不该盖住/影响下方网格）。


        // 页滑钮：高度与快捷功能按钮一致、**右缘对齐**，宽度按「两个静止位各占一半」推导 ⇒ 左侧收窄
        int sliderW = PhotoGalleryPageSlider.widthFor(rowH);
        this.slider = new PhotoGalleryPageSlider(contentX + round(CONTENT_W * k) - sliderW, row2Y,
                sliderW, rowH, radiusModule());
        this.slider.snapTo(page);

        this.quickW = round(QUICK_W * k);
        this.quickH = rowH;
        this.quickX = contentX + round(CONTENT_W * k) - quickW;
        this.quickY = row1Y;
        // ★ 宽度**铺满整个内容区**（右缘仍与快捷功能按钮对齐）。
        //   早期按「最长规则文案」定宽：小窗口（k ≲ 55）下面板左缘会切进下方那两行文字的中段 ——
        //   搜索条 hint / 开关行 2 的文字被盖掉右半截、左半截露在外面，看着就是「列表与下方文字重叠」。
        //   铺满内容区后，面板横向覆盖了这两行的全部内容（左缘 = contentX），不可能再露出半截。
        int shortcutW = round(CONTENT_W * k);
        int shortcutH = Math.max(10, round(SHORTCUT_ROW_H * k));
        this.shortcuts = new PhotoGalleryShortcutPanel(
                contentX + round(CONTENT_W * k) - shortcutW, quickY + quickH + 2, shortcutW, shortcutH,
                radiusModule());

        // ★ 打开时**按原版把光标重定位到屏幕中心**（原版 MouseHandler.releaseMouse/grabMouse 就是这么做的）。
        //   刻意不用 CursorKeeper.restore()：那是羽毛页「切屏时不挪光标」的口径，图鉴要的是原版手感。
        CursorKeeper.center();

        // 开屏即拉一次全量（兜底登录时序 / 断线重连），并预热索引与模型
        PacketDistributor.sendToServer(PhotoGalleryActionPacket.requestSync());
        PhotoGalleryCatalog.warmUp();
        repopulate();
        this.lastSeenRevision = PhotoGalleryClientCache.revision();
    }

    // ========== 版式 ==========

    private void layout() {
        // ① 面板区域 = 参考图比例 × 窗口（0.614 × 0.796）
        int targetW = Math.max(160, Math.round(this.width * PANEL_W_RATIO));
        int targetH = Math.max(120, Math.round(this.height * PANEL_H_RATIO));
        // ② k = 「面板区域 ÷ 版式常数」取小值 ⇒ 所有模块必然落在面板区域内；
        //    想放大格子只能压非网格区，绝不能撑面板（那会把背景糊掉、脱离区域设定）
        float raw = Math.min(targetW / K_W, targetH / K_H);
        this.k = Math.max(K_MIN, Math.min(K_MAX, (float) Math.floor(raw)));

        int padL = round(PAD_L * k);
        int padR = round(PAD_R * k);
        int padT = round(PAD_T * k);
        int padB = round(PAD_B * k);
        int contentW = round(CONTENT_W * k);
        this.rowH = round(ROW_H * k);
        int rowGap = round(ROW_GAP * k);
        this.searchW = round(SEARCH_W * k);
        this.searchH = round(SEARCH_H * k);
        this.cellW = round(k);
        this.cellH = round(CELL_H * k);
        this.colPitch = round(COL_PITCH * k);
        this.rowPitch = round(ROW_PITCH * k);
        this.gridW = cellW + (COLS - 1) * colPitch;
        this.gridH = cellH + (VISIBLE_ROWS - 1) * rowPitch;
        this.scrollbarW = Math.max(4, round(SCROLLBAR_W * k));

        int contentH = rowH * 2 + rowGap * 2 + searchH + round(SEARCH_TO_GRID * k) + gridH;
        this.panelW = padL + contentW + padR;
        this.panelH = padT + contentH + padB;
        this.panelX = (this.width - panelW) / 2;
        this.panelY = (this.height - panelH) / 2;

        this.contentX = panelX + padL;
        this.contentY = panelY + padT;
        this.row1Y = contentY;
        this.row2Y = row1Y + rowH + rowGap;

        this.searchX = contentX;
        this.searchY = row2Y + rowH + rowGap;

        this.gridX = contentX + round(GRID_INSET * k);
        this.gridY = searchY + searchH + round(SEARCH_TO_GRID * k);
        this.scrollbarX = gridX + gridW + round(SCROLLBAR_GAP * k);

        this.checkboxW = Math.max(6, round(CHECK_W * k));
        this.checkboxH = Math.max(6, round(CHECK_H * k));
        // 两行开关的方块右缘对齐：取两页四行文案里最宽的一条来定位
        int widest = 0;
        for (Component label : allToggleLabels()) {
            widest = Math.max(widest, this.font.width(label));
        }
        this.checkboxX = contentX + widest + round(CHECK_GAP * k);
        this.checkboxY1 = row1Y + (rowH - checkboxH) / 2;
        this.checkboxY2 = row2Y + (rowH - checkboxH) / 2;
    }

    private static int round(float value) {
        return Math.round(value);
    }

    /** 圆角半径随 k 缩放：格子最圆（参考图里格子是明显的圆角方块），模块次之，勾选方块最小 */
    private int radiusCell() {
        return Math.max(4, Math.round(0.18F * k));
    }

    /** **模块类统一半径**：开关方块 / 快捷按钮 / 搜索条 / 页滑钮 / 滚动条 / 弹窗及其行高亮，全用它 */
    private int radiusModule() {
        return Math.max(3, Math.round(0.12F * k));
    }

    /** 面板圆角（磨砂玻璃与描边必须同一值，否则边缘会露出错位） */
    private int radiusPanel() {
        return Math.max(8, Math.round(0.22F * k));
    }

    /**
     * 大面板（磨砂玻璃 + 描边）**额外外扩**的像素：只放大面板矩形本身，
     * 网格 / 搜索框 / 开关行 / 关闭按钮等**一律不动**（`panelY`/`panelH` 仍是内容盒，
     * 外扩只走 {@link #panelRectY()} / {@link #panelRectH()}）。
     * <p>
     * 1.6.62 口径：**上方不外扩（0）、下方外扩 11px** —— 面板只向下多长 11px，内容仍留在原位。
     */
    private static final int PANEL_EXTEND_TOP = 0;
    private static final int PANEL_EXTEND_BOTTOM = 11;

    private int panelRectY() {
        return panelY - PANEL_EXTEND_TOP;
    }

    private int panelRectH() {
        return panelH + PANEL_EXTEND_TOP + PANEL_EXTEND_BOTTOM;
    }

    private List<Component> toggleLabels(int pageIndex) {
        if (pageIndex == 0) {
            return List.of(Component.translatable("gui.lensouls.photos.show_unstolen"),
                    Component.translatable("gui.lensouls.photos.favorites_only"));
        }
        return List.of(Component.translatable("gui.lensouls.photos.show_locked_set"),
                Component.translatable("gui.lensouls.photos.favorites_only"));
    }

    private List<Component> allToggleLabels() {
        List<Component> out = new ArrayList<>(toggleLabels(0));
        out.addAll(toggleLabels(1));
        return out;
    }

    private void updateSearchHint() {
        this.search.setHint(Component.translatable(page == 0
                ? "gui.lensouls.photos.search"
                : "gui.lensouls.photos.search.set"));
    }

    // ========== 数据 ==========

    private void repopulate() {
        String query = this.search == null ? searchText[page] : this.search.getValue();
        searchText[page] = query;
        if (page == 0) {
            entries = PhotoGalleryCatalog.photoEntries(query, showLocked[0], favoritesOnly[0]);
        } else {
            entries = PhotoGalleryCatalog.setEntries(player, query, showLocked[1], favoritesOnly[1]);
        }
        float max = maxScroll();
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
        hoveredIndex = -1;
    }

    private void refreshSuggestions() {
        List<PhotoGallerySearchWidget.Suggestion> out = new ArrayList<>();
        if (page == 0) {
            // ★ 照片页补全列表**不再放「仅显示收藏夹」动作项**（用户口径）：聚焦搜索时列表只保留
            //   `名称:` / `tooltip:` 两个模式前缀 + 名称候选；收藏过滤只由开关行（是否仅显示收藏）控制。
            out.add(PhotoGallerySearchWidget.Suggestion.prefix(
                    Component.translatable("gui.lensouls.photos.search.mode.name").getString() + ":", "名称:"));
            out.add(PhotoGallerySearchWidget.Suggestion.prefix("tooltip:", "tooltip:"));
            int added = 0;
            for (GalleryEntry entry : PhotoGalleryCatalog.photoEntries("", true, false)) {
                out.add(PhotoGallerySearchWidget.Suggestion.text(entry.name().getString(), entry.name().getString()));
                if (++added >= 40) break;
            }
        } else {
            out.add(PhotoGallerySearchWidget.Suggestion.prefix(
                    Component.translatable("gui.lensouls.photos.search.mode.set").getString() + ":", "套装:"));
            out.add(PhotoGallerySearchWidget.Suggestion.prefix("tooltip:", "tooltip:"));
            int added = 0;
            for (PhotoGalleryNameIndex.SetEntry set : PhotoGalleryCatalog.setEntries()) {
                out.add(PhotoGallerySearchWidget.Suggestion.text(set.name(), set.name()));
                if (++added >= 40) break;
            }
        }
        this.search.setSuggestions(out);
    }

    private float maxScroll() {
        return Math.max(0.0F, contentHeight() - gridH);
    }

    private int totalRows() {
        return (entries.size() + COLS - 1) / COLS;
    }

    private float contentHeight() {
        return totalRows() <= 0 ? 0.0F : (totalRows() - 1) * (float) rowPitch + cellH;
    }

    // ========== 渲染 ==========

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float dt = frameDelta();

        // 服务端同步到达 → 重算列表（用 revision 比对，避免每帧重建）
        if (PhotoGalleryClientCache.revision() != lastSeenRevision) {
            lastSeenRevision = PhotoGalleryClientCache.revision();
            repopulate();
        }
        // 模型还在后台建（首屏常见）：每帧催一次，建完自然就有模型
        if (PhotoGalleryEntityCache.isLoading() || PhotoGalleryEntityCache.readyCount() == 0) {
            PhotoGalleryCatalog.warmUp();
        }

        // 每帧重算悬停：补全弹窗/快捷功能面板可能在鼠标不动时展开或收起，
        // 只靠 mouseMoved 会漏掉「展开后鼠标正压在网格上」的情况（反馈：列表展开时仍触发下方 tooltip）
        updateHover(mouseX, mouseY);

        this.slider.tick(dt);

        renderCoverBackground(graphics);
        graphics.flush();
        if (GlassPanel.available()) {
            GlassPanel.renderPanelGlass(panelX, panelRectY(), panelW, panelRectH(), radiusPanel(), 0x8C121A24);
        }
        RoundedRect.roundedOutline(graphics, panelX, panelRectY(), panelW, panelRectH(), radiusPanel(), 0x40FFFFFF, 1.0F);

        renderToggleRow(graphics, 0, mouseX, mouseY);
        renderToggleRow(graphics, 1, mouseX, mouseY);
        renderSearchBar(graphics, mouseX, mouseY);
        renderQuickButton(graphics, mouseX, mouseY);
        renderScrollbar(graphics, mouseX, mouseY);
        // ★ 网格区整体 scissor（反馈「滚动时网格会滑到搜索框下方」）：
        //   滚动到一半的首行格子，其**圆角底/遮罩/名称条**会画到 gridY 以上、盖住搜索条。
        //   GuiGraphics 的 scissor 是**栈式并自动求交**（javap 核实：enableScissor push / disableScissor pop），
        //   所以这里套一层网格 scissor，里面每格自己的 scissor 再求交即可。
        graphics.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        renderGridBackgrounds(graphics, mouseX, mouseY);

        // 格子底走立即模式（RoundedRect），模型渲染内部会 flush：这里先冲一次，
        // 保证「格子底 → 模型」的先后关系不受延迟批处理影响。
        graphics.flush();

        // 模型（每格 scissor 与网格 scissor 求交，并做崩溃兜底）
        for (int i = 0; i < entries.size(); i++) {
            int[] cell = cellRect(i);
            if (cell == null) continue;
            renderCellModel(graphics, entries.get(i), cell[0], cell[1]);
        }
        graphics.disableScissor();

        // ★ 模型写深度 → 之后的 overlay 全被遮挡，必须清深度并关深度测试
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // 遮罩/名称条同样限制在网格区内（半露格子不会盖到搜索条）
        graphics.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        renderCellOverlays(graphics, mouseX, mouseY);
        graphics.disableScissor();
        renderEmptyHint(graphics);

        // 菜单展开时不画搜索框文字：菜单本来就把搜索条整段盖住了，
        // 而文字是延迟批处理、最容易在顺序上出意外 —— 直接不提交，就不可能压在菜单上。
        if (!this.shortcuts.isOpen()) {
            this.search.render(graphics, mouseX, mouseY, partialTick);
        }
        this.slider.render(graphics, mouseX, mouseY);
        renderTooltips(graphics, mouseX, mouseY);
        FeatherToast.render(graphics, this.width, this.height);

        // ★★ 浮层**必须最后画**（补全弹窗 / 快捷功能面板）：
        //   本界面的浮层底板走立即模式（RoundedRect → Tesselator + BufferUploader），而搜索框文字、
        //   格子名称、开关文字、tooltip 全是**延迟批处理**（GuiGraphics.bufferSource）。
        //   只要浮层之后还有任何延迟绘制没落盘，那些文字就会盖在浮层上 —— 实机反馈的
        //   「列表被搜索框文字挡着」正是这种顺序问题（先前只在浮层前 flush 仍不够，因为浮层之后
        //   还有 slider/tooltip/toast 等延迟提交把旧文字带到了后面）。
        //   现在：先 flush 落盘一切，再按「补全弹窗 → 快捷功能面板」的顺序画浮层。
        graphics.flush();
        this.search.renderSuggestions(graphics, mouseX, mouseY);
        // 再冲一次：把补全弹窗的**行文字**（延迟）落盘，快捷功能面板的立即底板才能稳稳压在它上面
        graphics.flush();
        this.shortcuts.render(graphics, mouseX, mouseY, ruleLabel());

        RenderSystem.enableDepthTest();

        CloseButton.render(graphics, CloseButton.x(panelX + panelW), CloseButton.y(panelY), mouseX, mouseY);
    }

    private float frameDelta() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.016F : (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        return Math.max(0.001F, Math.min(0.1F, dt));
    }

    private void renderCoverBackground(GuiGraphics graphics) {
        if (this.width <= 0 || this.height <= 0) return;
        // cover 等比铺满（超出裁掉）；素材尺寸与路径写在一处，换图不会漏改
        float scale = Math.max((float) this.width / BG_W, (float) this.height / BG_H);
        int srcW = Math.max(1, (int) (this.width / scale));
        int srcH = Math.max(1, (int) (this.height / scale));
        int u = (BG_W - srcW) / 2;
        int v = (BG_H - srcH) / 2;
        graphics.blit(BACKGROUND, 0, 0, this.width, this.height, u, v, srcW, srcH, BG_W, BG_H);
    }

    private void renderToggleRow(GuiGraphics graphics, int row, int mouseX, int mouseY) {
        List<Component> labels = toggleLabels(page);
        Component label = labels.get(row);
        int y = row == 0 ? row1Y : row2Y;
        int boxY = row == 0 ? checkboxY1 : checkboxY2;
        boolean on = row == 0 ? showLocked[page] : favoritesOnly[page];
        boolean hover = isOver(mouseX, mouseY, contentX, y, checkboxX + checkboxW - contentX, rowH);
        if (hover) toggleHover = row;
        else if (toggleHover == row) toggleHover = -1;

        int textY = y + (rowH - 8) / 2;
        graphics.drawString(this.font, label, contentX, textY, on ? 0xFFF2F6FF : 0xFFAEB6C4, false);

        RoundedRect.roundedRect(graphics, checkboxX, boxY, checkboxW, checkboxH, radiusModule(),
                on ? 0xFF22FF46 : 0xCC0E141C,
                on ? 0xFF1ACB38 : 0xCC0A0F16,
                hover ? 0x66FFFFFF : 0x33FFFFFF, 1.0F);
    }

    private void renderSearchBar(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean focused = this.search != null && this.search.isFocused();
        RoundedRect.roundedRect(graphics, searchX, searchY, searchW, searchH, radiusModule(),
                0xCC0E141C, 0xCC0A0F16, focused ? 0x66FFFFFF : 0x33FFFFFF, 1.0F);
    }

    private void renderQuickButton(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hover = isOver(mouseX, mouseY, quickX, quickY, quickW, quickH) || this.shortcuts.isOpen();
        RoundedRect.roundedRect(graphics, quickX, quickY, quickW, quickH, radiusModule(),
                hover ? 0xE61B2330 : 0xCC0E141C,
                hover ? 0xE6151C27 : 0xCC0A0F16,
                hover ? 0x66FFFFFF : 0x33FFFFFF, 1.0F);
        Component label = Component.translatable("gui.lensouls.photos.shortcut");
        graphics.drawString(this.font, label,
                quickX + (quickW - this.font.width(label)) / 2, quickY + (quickH - 8) / 2, 0xFFF2F6FF, false);
    }

    private boolean isOver(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ========== 网格 ==========

    private int[] cellRect(int index) {
        int row = index / COLS;
        int col = index % COLS;
        int x = gridX + col * colPitch;
        int y = Math.round(gridY + row * (float) rowPitch - scroll);
        if (y + cellH <= gridY || y >= gridY + gridH) return null;   // 不在可视区
        return new int[]{x, y};
    }

    private int indexAt(int mouseX, int mouseY) {
        if (!isOver(mouseX, mouseY, gridX, gridY, gridW, gridH)) return -1;
        int col = (mouseX - gridX) / colPitch;
        if (col < 0 || col >= COLS) return -1;
        float local = mouseY - gridY + scroll;
        int row = (int) (local / rowPitch);
        if (row < 0) return -1;
        int index = row * COLS + col;
        if (index < 0 || index >= entries.size()) return -1;
        int[] cell = cellRect(index);
        if (cell == null) return -1;
        int withinX = mouseX - cell[0];
        int withinY = mouseY - cell[1];
        if (withinX < 0 || withinX >= cellW || withinY < 0 || withinY >= cellH) return -1;
        return index;
    }

    private void renderGridBackgrounds(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < entries.size(); i++) {
            int[] cell = cellRect(i);
            if (cell == null) continue;
            boolean hover = i == hoveredIndex;
            RoundedRect.roundedRect(graphics, cell[0], cell[1], cellW, cellH, radiusCell(),
                    hover ? 0xE61B2330 : 0xCC0E141C,
                    hover ? 0xE6151C27 : 0xCC0A0F16,
                    hover ? 0x88FFFFFF : 0x33FFFFFF, 1.0F);
        }
    }

    /**
     * 格子里的立体模型。
     * <p>
     * <b>未解锁的也渲染</b>（外面再压一层暗罩）——首版只渲染已解锁的，结果玩家第一次打开图鉴
     * （还没解锁任何条目）看到的是「一片空框」，被当成模型没渲染。
     * 模型还没建好时兜底画刷怪蛋/代表物角标，保证任何时刻格子都有内容。
     */
    private void renderCellModel(GuiGraphics graphics, GalleryEntry entry, int x, int y) {
        if (entry.isSet()) return;   // 套装格是纯文字格

        Mob mob = crashedModels.contains(entry.id()) ? null : PhotoGalleryEntityCache.get(entry.id());
        if (mob == null) {
            renderBadgeIcon(graphics, entry, x, y);
            return;
        }

        // 裁剪矩形 = 格子矩形（外层已有网格 scissor，栈式求交会自动再夹一次）
        int sx = x + 1;
        int sy = y + 1;
        int ex = x + cellW - 1;
        int ey = y + cellH - 1;
        if (sx >= ex || sy >= ey) return;

        graphics.enableScissor(sx, sy, ex, ey);
        // 自己配一对 push/pop：Vanilla 的 renderEntityInInventory 内部也会 push，
        // 若它在 pop 之前抛异常，栈里会多留一层 —— 用**我们自己的 pop** 把那层一并收掉。
        // 刻意不用 PoseStack.clear()：1.21.1 里它只是「栈深是否为 1」的查询，并不清栈（javap 核实过）。
        var pose = graphics.pose();
        Matrix4f saved = new Matrix4f(pose.last().pose());
        pose.pushPose();
        try {
            // 缩放 = 「可用区域 ÷ 包围盒」取**两者较小值**：宽体不横向溢出、高个不纵向溢出。
            // 首版用 34/max(bb)（只归一化最大边）会让高个生物顶出格子被裁 —— 反馈「模型太大、露出部分过少」。
            float baselineY = y + cellH - NAME_STRIP_H - 2.0F;      // 脚底贴着名称条上沿
            float availH = Math.max(8.0F, baselineY - y - 2.0F);    // 上方仍留 2px 余量
            float availW = Math.max(8.0F, cellW - 6.0F);
            float bbH = Math.max(0.05F, mob.getBbHeight());
            float bbW = Math.max(0.05F, mob.getBbWidth());
            float size = Math.min(availW / bbW, availH / bbH);
            InventoryScreen.renderEntityInInventory(graphics,
                    x + cellW / 2.0F, baselineY, size,
                    new Vector3f(),
                    new Quaternionf().rotationXYZ(0.43633232F, (float) Math.PI, (float) Math.PI),
                    null, mob);
        } catch (Throwable t) {
            // 部分实体在 GUI 里渲染会抛异常：记下来不再重试，并把矩阵还原成「进入本格之前」的样子
            crashedModels.add(entry.id());
            com.plumejade.lensouls.LenSouls.LOGGER.error("[PhotoGallery] 渲染生物模型失败: {}", entry.id(), t);
            pose.last().pose().set(saved);
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            pose.popPose();
            // 异常路径若残留 scissor，整个界面后续渲染都会被裁掉
            graphics.disableScissor();
        }
    }

    /** 模型未就绪 / 渲染失败的兜底：刷怪蛋或代表物（16×16） */
    private void renderBadgeIcon(GuiGraphics graphics, GalleryEntry entry, int x, int y) {
        try {
            ResourceLocation id = ResourceLocation.tryParse(entry.id());
            ItemStack icon = PhotoBadgeIcon.getEggStack(id);
            if (icon == null || icon.isEmpty()) return;
            int areaH = Math.max(16, cellH - NAME_STRIP_H);
            graphics.renderItem(icon, x + (cellW - 16) / 2, y + (areaH - 16) / 2);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 补全弹窗矩形（未展开返回 null）。被它压住的格子**不再画名称/进度/收藏星**：
     * 弹窗是不透明的，整格被覆盖时跳过是无感的，但「只被压住一半」的格子会露出半截白字，
     * 看着就是用户反馈的「列表与网格名称文字重叠」。跳过后列表下沿干干净净。
     */
    private int[] popupRect() {
        return this.search != null && this.search.isSuggestionsVisible()
                ? this.search.suggestionPanelRect() : null;
    }

    private boolean coveredByPopup(int x, int y, int w, int h) {
        int[] p = popupRect();
        if (p == null) return false;
        return x < p[0] + p[2] && x + w > p[0] && y < p[1] + p[3] && y + h > p[1];
    }

    private void renderCellOverlays(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < entries.size(); i++) {
            int[] cell = cellRect(i);
            if (cell == null) continue;
            GalleryEntry entry = entries.get(i);
            int x = cell[0];
            int y = cell[1];
            if (coveredByPopup(x, y, cellW, cellH)) continue;   // 被补全列表压住 → 整格浮层跳过

            if (entry.isSet()) {
                renderSetCell(graphics, entry, x, y);
            } else {
                if (!entry.unlocked()) {
                    // 未解锁：暗罩 + 名称条（暗色文字）。
                    // ★ 暗罩必须**圆角**：原来用 graphics.fill 直角矩形，会在圆角格子的四个角上
                    //   多盖出一块方形黑角（反馈「未解锁时的灰框覆盖没带圆角」）。
                    RoundedRect.roundedRect(graphics, x, y, cellW, cellH, radiusCell(),
                            0x88000000, 0x88000000, 0x33FFFFFF, 1.0F);
                }
                // 名称条：底衬 + 单行省略号，**绝不出框**
                drawNameStrip(graphics, entry, x, y, entry.unlocked() ? 0xFFF2F6FF : 0xFF9A9A9A);
            }

            renderFavoriteStar(graphics, entry, x, y);
        }
    }

    /** 格子底部的名称条（半透明底衬 + 居中省略号文本） */
    /**
     * 格子底部的名称：**不画黑色底衬**（用户口径），只给文字加 1px 投影保证在亮模型上也读得清。
     */
    private void drawNameStrip(GuiGraphics graphics, GalleryEntry entry, int x, int y, int color) {
        int stripH = NAME_STRIP_H;
        int stripY = y + cellH - stripH;
        Component name = ellipsize(entry.name().getString(), cellW - 6);
        graphics.drawString(this.font, name, x + (cellW - this.font.width(name)) / 2, stripY + 1, color, true);
    }

    /**
     * 套装格：**只放 套装名 + 已收集 x/x**（2026-10 反馈「信息过于冗余」后砍掉「集齐 x/x/x」那一行）。
     * 「集齐 x/x/x」与逐条效果只在**悬停 tooltip** 里给出（见 {@link PhotoGalleryCatalog#setTooltip}）。
     */
    private void renderSetCell(GuiGraphics graphics, GalleryEntry entry, int x, int y) {
        if (!entry.unlocked()) {
            // 同照片格：暗罩走圆角，别在圆角格四角多盖出方形黑角
            RoundedRect.roundedRect(graphics, x, y, cellW, cellH, radiusCell(),
                    0x88000000, 0x88000000, 0x33FFFFFF, 1.0F);
        }
        int nameColor = entry.unlocked() ? 0xFFF2F6FF : 0xFF9A9A9A;
        drawFitted(graphics, entry.name(), x, y + Math.round(cellH * 0.28F), nameColor);

        Component collected = Component.translatable("gui.lensouls.photos.set.prog.collected",
                entry.collected(), entry.collectedTotal());
        drawFitted(graphics, collected, x, y + Math.round(cellH * 0.55F),
                entry.complete() ? 0xFF22FF46 : 0xFFAEB6C4);

        if (entry.complete()) {
            RoundedRect.roundedOutline(graphics, x, y, cellW, cellH, radiusCell(), 0xE622FF46, 1.5F);
            graphics.fill(x + 3, y + 3, x + 9, y + 9, 0xFF22FF46);
        }
    }

    /**
     * 居中绘制一行文本，宽度超出格子时**等比缩小字号**（下限 0.55），保证永远落在框内。
     * 首版这里直接用原字号居中，长名字会溢出到相邻格（玩家实测「名称被折叠/出框」）。
     */
    private void drawFitted(GuiGraphics graphics, Component text, int cellX, int y, int color) {
        String s = text.getString();
        int width = this.font.width(s);
        int maxWidth = cellW - 6;
        if (width <= maxWidth) {
            graphics.drawString(this.font, s, cellX + (cellW - width) / 2, y, color, false);
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        float scale = Math.max(0.55F, maxWidth / (float) width);
        float centerX = cellX + cellW / 2.0F;
        pose.translate(centerX, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-centerX, -y, 0.0F);
        graphics.drawString(this.font, s, cellX + (cellW - width) / 2, y, color, false);
        pose.popPose();
    }

    private Component ellipsize(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return Component.literal(text);
        String s = text;
        while (s.length() > 1 && this.font.width(s + "…") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return Component.literal(s + "…");
    }

    private void renderFavoriteStar(GuiGraphics graphics, GalleryEntry entry, int x, int y) {
        ResourceLocation icon = entry.favorite() ? STAR : EMPTY_STAR;
        graphics.blit(icon, x + cellW - STAR_SIZE - 2, y + 2, 0, 0, STAR_SIZE, STAR_SIZE, STAR_SIZE, STAR_SIZE);
    }

    private boolean isOverStar(double mouseX, double mouseY, int cellX, int cellY) {
        double starX = cellX + cellW - STAR_SIZE - 2;
        double starY = cellY + 2;
        return mouseX >= starX - 3 && mouseX < starX + STAR_SIZE + 7
                && mouseY >= starY - 3 && mouseY < starY + STAR_SIZE + 7;
    }

    private void renderEmptyHint(GuiGraphics graphics) {
        if (!entries.isEmpty()) return;
        Component hint = Component.translatable("gui.lensouls.photos.empty");
        graphics.drawString(this.font, hint,
                gridX + (gridW - this.font.width(hint)) / 2, gridY + gridH / 2, 0xFFAEB6C4, false);
    }

    // ========== 悬停光带 ==========

    // ========== 滚动条 ==========

    private int scrollbarThumbHeight() {
        float content = contentHeight();
        if (content <= gridH) return gridH;
        return Math.max(20, Math.round(gridH * (gridH / content)));
    }

    private int scrollbarThumbY() {
        float max = maxScroll();
        if (max <= 0) return gridY;
        int thumb = scrollbarThumbHeight();
        return gridY + Math.round((scroll / max) * (gridH - thumb));
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        if (maxScroll() <= 0) return;
        RoundedRect.roundedRect(graphics, scrollbarX, gridY, scrollbarW, gridH, radiusModule(),
                0x40000000, 0x40000000, 0x33FFFFFF, 1.0F);
        int thumbY = scrollbarThumbY();
        int thumbH = scrollbarThumbHeight();
        boolean hover = isOver(mouseX, mouseY, scrollbarX, gridY, scrollbarW, gridH);
        RoundedRect.roundedRect(graphics, scrollbarX, thumbY, scrollbarW, thumbH, radiusModule(),
                hover || scrollDragging ? 0xFFC9C9C9 : 0xFFA7A7A7, 0xFF8E8E8E, 0x40000000, 1.0F);
    }

    private void scrollBy(float delta) {
        scroll = Math.max(0.0F, Math.min(maxScroll(), scroll + delta));
    }

    // ========== tooltip ==========

    private void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        // 展开的列表（补全弹窗 / 快捷功能）覆盖区域内，不显示下方网格的 tooltip
        if (mouseOverPopup(mouseX, mouseY)) return;

        if (this.slider.isOver(mouseX, mouseY)) {
            graphics.renderTooltip(this.font, Component.translatable(page == 0
                    ? "gui.lensouls.photos.tab.steal"
                    : "gui.lensouls.photos.tab.set"), mouseX, mouseY);
            return;
        }
        if (toggleHover >= 0) {
            graphics.renderTooltip(this.font, Component.translatable("gui.lensouls.photos.toggle.hint",
                    hiddenCount()), mouseX, mouseY);
            return;
        }
        if (hoveredIndex >= 0 && hoveredIndex < entries.size()) {
            GalleryEntry entry = entries.get(hoveredIndex);
            if (entry.isSet()) {
                graphics.renderTooltip(this.font, PhotoGalleryCatalog.setTooltip(entry),
                        java.util.Optional.empty(), mouseX, mouseY);
            } else if (entry.unlocked()) {
                ItemStack stack = EntityPhotographItem.create(entry.id());
                List<Component> lines = getTooltipFromItem(this.minecraft, stack);
                graphics.renderTooltip(this.font, lines, stack.getTooltipImage(), stack, mouseX, mouseY);
            } else {
                graphics.renderTooltip(this.font, List.of(entry.name(),
                                Component.translatable("gui.lensouls.photos.locked.entity"),
                                Component.translatable("gui.lensouls.photos.locked.hint")),
                        java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private int hiddenCount() {
        int visible = entries.size();
        int all;
        if (page == 0) {
            all = PhotoGalleryCatalog.photoEntries("", true, favoritesOnly[0]).size();
        } else {
            all = PhotoGalleryCatalog.setEntries(player, "", true, favoritesOnly[1]).size();
        }
        return Math.max(0, all - visible);
    }

    private Component ruleLabel() {
        PhotoGalleryData.ClickRule rule = this.menu == null ? PhotoGalleryData.ClickRule.GRANT : this.menu.clickRule();
        return Component.translatable(rule == PhotoGalleryData.ClickRule.GRANT
                ? "gui.lensouls.photos.rule.grant"
                : "gui.lensouls.photos.rule.equip");
    }

    // ========== 交互 ==========

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        updateHover((int) mouseX, (int) mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    /** 鼠标是否落在「展开的列表」上（补全弹窗 / 快捷功能面板）：这些区域不该触发下方网格的悬停 */
    private boolean mouseOverPopup(int mouseX, int mouseY) {
        if (this.shortcuts != null && this.shortcuts.isOpen() && this.shortcuts.isOver(mouseX, mouseY)) {
            return true;
        }
        return this.search != null && this.search.mouseOverSuggestions(mouseX, mouseY);
    }

    private void updateHover(int mouseX, int mouseY) {
        int index = mouseOverPopup(mouseX, mouseY) ? -1 : indexAt(mouseX, mouseY);
        if (index == hoveredIndex) return;
        hoveredIndex = index;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateHover((int) mouseX, (int) mouseY);

        // 关闭按钮优先
        if (CloseButton.isOver(mouseX, mouseY, CloseButton.x(panelX + panelW), CloseButton.y(panelY))) {
            this.onClose();
            return true;
        }

        // ★ 聚焦时右键 = 清空搜索框（用户口径）；放在最前面，避免被后面的分支吞掉
        if (button == 1 && this.search.isFocused()) {
            this.search.setValue("");
            updateSearchHint();
            scroll = 0.0F;
            repopulate();
            return true;
        }

        // 快捷功能按钮 / 面板
        if (isOver((int) mouseX, (int) mouseY, quickX, quickY, quickW, quickH)) {
            this.shortcuts.toggle();
            if (this.shortcuts.isOpen()) {
                // ★ 两个浮层**互斥**：菜单展开时收起搜索框（补全列表随之关闭），
                //   否则「补全列表」与「快捷功能菜单」会同时挂在屏幕上、边界正好相撞（反馈的「重叠」）。
                this.search.setFocused(false);
            }
            return true;
        }
        if (this.shortcuts.mouseClicked(mouseX, mouseY, this::onShortcutRow)) {
            return true;
        }
        if (this.shortcuts.isOpen()) {
            this.shortcuts.close();
        }

        // 补全弹窗（仅聚焦时有意义）
        if (this.search.isFocused() && this.search.mouseClickedSuggestions(mouseX, mouseY)) {
            return true;
        }

        // 搜索框（命中区用整条搜索条的矩形，比 EditBox 自身矩形宽，左右留白也能点中）
        if (isOver((int) mouseX, (int) mouseY, searchX, searchY, searchW, searchH)) {
            this.shortcuts.close();          // 与菜单互斥（同上）
            this.setFocused(this.search);
            this.search.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        this.search.setFocused(false);

        // 页滑钮
        if (this.slider.mouseClicked(mouseX, mouseY)) {
            return true;
        }

        // 开关两行
        for (int row = 0; row < 2; row++) {
            int y = row == 0 ? row1Y : row2Y;
            if (isOver((int) mouseX, (int) mouseY, contentX, y, checkboxX + checkboxW - contentX, rowH)) {
                if (row == 0) showLocked[page] = !showLocked[page];
                else favoritesOnly[page] = !favoritesOnly[page];
                scroll = 0.0F;
                repopulate();
                return true;
            }
        }

        // 滚动条：命中滑块拖动，否则点轨道跳转
        if (isOver((int) mouseX, (int) mouseY, scrollbarX, gridY, scrollbarW, gridH) && maxScroll() > 0) {
            int thumbY = scrollbarThumbY();
            int thumbH = scrollbarThumbHeight();
            if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                scrollDragging = true;
                scrollGrabOffset = mouseY - thumbY;
            } else {
                float ratio = (float) ((mouseY - gridY - thumbH / 2.0) / Math.max(1, gridH - thumbH));
                scroll = Math.max(0.0F, Math.min(maxScroll(), ratio * maxScroll()));
            }
            return true;
        }

        // 网格格子
        int index = indexAt((int) mouseX, (int) mouseY);
        if (index >= 0) {
            int[] cell = cellRect(index);
            GalleryEntry entry = entries.get(index);
            if (cell != null && isOverStar(mouseX, mouseY, cell[0], cell[1])) {
                PacketDistributor.sendToServer(PhotoGalleryActionPacket.toggleFavorite(entry.favoriteKey()));
                return true;
            }
            if (!entry.isSet()) {
                PacketDistributor.sendToServer(PhotoGalleryActionPacket.clickCell(entry.id()));
            }
            return true;
        }

        // ★ 面板外点击**不关闭**界面（用户口径）：关闭只走右上 × 或 Esc
        return true;
    }

    private void onShortcutRow(int row) {
        if (row == PhotoGalleryShortcutPanel.ROW_CLEAR) {
            PacketDistributor.sendToServer(
                    new PhotoGalleryShortcutPacket(PhotoGalleryShortcutPacket.ACTION_CLEAR_EQUIPPED));
        } else if (row == PhotoGalleryShortcutPanel.ROW_DEDUPE) {
            PacketDistributor.sendToServer(
                    new PhotoGalleryShortcutPacket(PhotoGalleryShortcutPacket.ACTION_DEDUPE));
        } else if (row == PhotoGalleryShortcutPanel.ROW_RULE) {
            PhotoGalleryData.ClickRule next = (this.menu == null
                    ? PhotoGalleryData.ClickRule.GRANT : this.menu.clickRule()).next();
            PacketDistributor.sendToServer(PhotoGalleryActionPacket.setRule(next));
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrollDragging) {
            float max = maxScroll();
            int thumb = scrollbarThumbHeight();
            float ratio = (float) ((mouseY - scrollGrabOffset - gridY) / Math.max(1, gridH - thumb));
            scroll = Math.max(0.0F, Math.min(max, ratio * max));
            return true;
        }
        if (this.slider.isDragging()) {
            this.slider.mouseDragged(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.scrollDragging = false;
        int changed = this.slider.mouseReleased();
        if (changed != 0) {
            switchPage(this.slider.page());
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void switchPage(int newPage) {
        if (newPage == page) return;
        page = newPage;
        this.search.setValue(searchText[page]);
        updateSearchHint();
        this.search.setSuggestions(List.of());
        this.slider.setPage(page);
        scroll = 0.0F;
        hoveredIndex = -1;
        repopulate();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.search.isFocused() && this.search.mouseScrolledSuggestions(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (isOver((int) mouseX, (int) mouseY, gridX, gridY, gridW, gridH)
                || isOver((int) mouseX, (int) mouseY, scrollbarX, gridY, scrollbarW, gridH)) {
            scrollBy((float) (-scrollY * rowPitch * 0.6F));
            updateHover((int) mouseX, (int) mouseY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || keyCode == 69) {   // ESC / E
            this.onClose();
            return true;
        }
        if (this.search.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.search.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 关屏：显式 {@code closeContainer()} 把服务端容器收回去（与羽毛装配页同一坑：
     * 只调 {@code super.onClose()} 会让服务端菜单一直挂着，表现就是「一关就直接回游戏」且状态不同步）。
     */
    @Override
    public void onClose() {
        Minecraft minecraft = this.minecraft;
        if (minecraft == null || minecraft.player == null) {
            super.onClose();
            return;
        }
        CursorKeeper.remember();
        minecraft.player.closeContainer();
        super.onClose();
        CursorKeeper.restore();
    }
}
