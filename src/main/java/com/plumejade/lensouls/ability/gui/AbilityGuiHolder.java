package com.plumejade.lensouls.ability.gui;

import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityOrderData;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.CameraAbilityStore;
import com.plumejade.lensouls.ability.client.ClientAbilityCache;
import com.plumejade.lensouls.ability.handler.CameraInputHandler;
import com.plumejade.lensouls.ability.network.AbilityOrderSetPacket;
import com.plumejade.lensouls.ability.network.AbilitySelectPacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 能力选择 GUI（LDLib2 服务端菜单）。
 *
 * <h2>交互</h2>
 * <ul>
 *   <li><b>选择</b>：只有「选择」按钮会切换能力，走 C2S {@link AbilitySelectPacket}
 *       （服务端权威写进手持相机的 CustomData，每台相机各自记住自己的选中项）。
 *       卡片背景/图标/名称点了不再选中。</li>
 *   <li><b>整理</b>：<b>长按</b>卡片（300ms）→ 这张卡自己跟手抬起来（换成更亮的同款卡片贴图 +
 *       提到最上层），拖到别的格子附近时其它卡片<b>平滑让位</b>（每 tick 指数逼近目标格），
 *       松手后各卡从「当前视觉位置」平滑收进新格子。没有落点竖线：位置感由卡片自己让位表达。</li>
 *   <li>顺序写进<b>存档级</b> {@link AbilityOrderData}，菜单 / 轮盘 / 右侧滚动列表三处同源。</li>
 *   <li>拖拽不挑解锁状态：未解锁的卡片也能挪（纯排版，不影响能不能用）。</li>
 *   <li>点击一级菜单空白区域关闭整个菜单。</li>
 * </ul>
 *
 * <h2>为什么用「视觉位移」而不是重建元素</h2>
 * 重排只改 DOM 里卡片的先后（元素身份不变），动画全部走 {@code transform2D} 的平移偏移：
 * 布局位置由 taffy 算，视觉位置 = 布局位置 + 偏移。这样既不会触发整棵子树重建（按钮状态、
 * 滚轮位置都不丢），也不依赖布局在同一个 tick 内算完 —— 偏移量是纯算术算出来的，跟布局时机无关。
 */
public class AbilityGuiHolder {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "ability_gui");

    private static final int PANEL_WIDTH = 350;
    private static final int CARD_WIDTH = 140;
    /** 滚轮单次滚动占溢出量的比例（0.1 = 一格滚溢出量的 10%，约 28px） */
    private static final float SCROLL_DELTA = 0.1f;

    /** 滚动平滑的时间常数（毫秒）：滚轮/拖滚动条只改目标，视图在这么长的尺度内追上去 */
    private static final float SCROLL_TAU_MS = 60f;

    /** 长按多久进入拖拽（毫秒）：只比一次点击长一点点，尽量跟手；短促点击仍不会被当成拖拽 */
    private static final long DRAG_HOLD_MS = 160L;
    /** 让位缓动的时间常数（毫秒）：约 3τ 走完 95%，与帧率无关 */
    private static final float SHIFT_TAU_MS = 90f;
    /** 松手后残余偏移收敛的时间常数（毫秒）：比让位慢 1.5 倍，落位校准才够顺滑 */
    private static final float FLIP_TAU_MS = 112f;
    /**
     * 自动滚动期间，插入位要先「稳住」这么久（毫秒）邻居才开始让位。
     * 内容持续从光标下流过时位次会以几赫兹连续变化，每变一次邻居就要挪一整卡高，
     * 视觉上就是上下震颤；稳住门槛把它压成「停下来才让位」，松手时的残余缓动仍然把卡片平滑送进格子。
     */
    private static final long SLOT_HOLD_MS = 180L;
    /** 偏移小于这个像素就吸附到 0，避免永远抖 */
    private static final float EPSILON = 0.2f;
    /** 拖拽时距视口上/下边缘多少像素内开始自动滚动 */
    private static final float DRAG_EDGE_PX = 30f;
    /** 自动滚动的初速（像素/秒）：刚贴上边缘那一瞬的速度 */
    private static final float SCROLL_MIN_PX_PER_SEC = 90f;
    /** 自动滚动的满速（像素/秒）：滚完一屏（约 280px）≈1.4 秒 */
    private static final float SCROLL_MAX_PX_PER_SEC = 200f;
    /** 从进入边缘到跑满最终速度的时长（毫秒）：起步几乎不动，再逐渐加速 */
    private static final float SCROLL_RAMP_MS = 700f;

    // ---- 翻译键 ----
    private static final String KEY_TITLE = "gui.lensouls.ability.title";
    private static final String KEY_DRAG_HINT = "gui.lensouls.ability.drag_hint";
    private static final String KEY_SELECT = "gui.lensouls.ability.select";
    private static final String KEY_IN_USE = "gui.lensouls.ability.in_use";
    private static final String KEY_LOCKED = "gui.lensouls.ability.locked";
    private static final String KEY_DETAIL = "gui.lensouls.ability.detail";
    private static final String KEY_BACK = "gui.lensouls.ability.back";

    /** 单张卡片的状态引用（动态更新用） */
    private static class CardRef {
        final AbilityType type;
        final UIElement card;
        final UIElement selectArea;
        final Button selectBtn;
        final Button detailBtn;
        final boolean unlocked;
        boolean inUse;

        CardRef(AbilityType type, UIElement card, UIElement selectArea, Button selectBtn,
                Button detailBtn, boolean unlocked, boolean inUse) {
            this.type = type;
            this.card = card;
            this.selectArea = selectArea;
            this.selectBtn = selectBtn;
            this.detailBtn = detailBtn;
            this.unlocked = unlocked;
            this.inUse = inUse;
        }

        void setInUse(boolean value) {
            inUse = value;
            selectBtn.setText(Component.translatable(value ? KEY_IN_USE : KEY_SELECT));
            selectBtn.textStyle(style -> style.textColor(value ? 0xFF55FF55 : 0xFFFFFFFF));
            selectBtn.setActive(unlocked);
        }
    }

    /** 单张卡片的视觉位移状态（全部是「相对布局位置」的像素偏移） */
    private static final class CardVis {
        /** 让位偏移：目标格与当前格之差的缓动中间值 */
        float shiftX;
        float shiftY;
        /** 松手后残余偏移，逐 tick 收敛到 0 */
        float flipX;
        float flipY;
        /** 跟手偏移（只有被拖那张非零） */
        float dragX;
        float dragY;
    }

    /** 一屏卡片的运行时视图 */
    private static final class Grid {
        final UIElement root;
        final ScrollerView scroller;
        final Player player;
        final List<UIElement> rows = new ArrayList<>();
        final List<CardRef> cardRefs = new ArrayList<>();
        final Map<CardRef, CardVis> vis = new HashMap<>();
        /** 上一帧时间戳（毫秒）与本帧时间差，缓动按真实时间算 */
        long lastFrameMs = 0L;
        float dtMs = 16f;
        /** 滚动平滑：目标归一化值、我们实际写下去的值、是否已初始化 */
        float scrollTarget;
        float scrollValue;
        boolean scrollInit;
        /**
         * 本帧滚动让<b>内容</b>上移了多少像素（正 = 内容向上走）。
         * 布局要到渲染期才结算，所以拖拽跟手必须自己减掉这一帧的位移，
         * 否则被拖的卡片会跟着滚动速度抖动（速度越快抖得越大）。
         */
        float contentShiftPx;

        Grid(UIElement root, ScrollerView scroller, Player player) {
            this.root = root;
            this.scroller = scroller;
            this.player = player;
        }
    }

    /** 一个打开中的界面 = 网格 + 它的拖拽会话（客户端每帧驱动用） */
    private static final class Session {
        final Grid grid;
        final DragSession drag;

        Session(Grid grid, DragSession drag) {
            this.grid = grid;
            this.drag = drag;
        }
    }

    /** 客户端当前打开的能力界面（服务端构建的树不登记）。同一时刻只会有一个。 */
    private static final List<Session> SESSIONS = new ArrayList<>();

    /** 一次拖拽的会话状态 */
    private static final class DragSession {
        CardRef source;
        long pressAtMs;
        float worldX;
        float worldY;
        boolean dragging;
        /** 本次拖拽算出来的目标顺序（松手时据此落位） */
        List<CardRef> pendingOrder;
        /** 自动滚动：当前方向（-1 上 / 0 无 / 1 下）、加速进度 0~1 */
        int scrollDir;
        float scrollRamp;
        /** 让位用的已确认位次；候选位次与它「从何时开始一直成立」（毫秒） */
        int targetSlot;
        int candidateSlot;
        long candidateSinceMs;
    }

    public static void register() {
        PlayerUIMenuType.register(ID, player -> new PlayerUIMenuType.PlayerUIHolder() {
            @Override
            public ModularUI createUI(Player p) {
                ModularUI ui = new ModularUI(UI.of(buildRoot(p)), p);
                OPEN_GUIS.add(ui);
                return ui;
            }
        });
    }

    /** 客户端打开的 GUI 实例集合（弱引用，关闭后自动回收）。 */
    private static final java.util.Set<ModularUI> OPEN_GUIS =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** 判断某 ModularUI 是否为能力选择 GUI（打开状态）。 */
    public static boolean isGuiOpen(ModularUI ui) {
        return OPEN_GUIS.contains(ui);
    }

    // ========== 一级菜单 ==========

    private static UIElement buildRoot(Player player) {
        UIElement root = new UIElement()
                .layout(layout -> layout.widthPercent(100).heightPercent(100)
                        .flexDirection(FlexDirection.COLUMN)
                        .alignItems(AlignItems.CENTER)
                        .justifyContent(AlignContent.CENTER))
                .style(style -> style.backgroundTexture(new ColorRectTexture(0x50000000)));

        UIElement[] overlayRef = {null};

        root.addChild(buildMainPanel(player, root, overlayRef));

        root.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.target != root) return;
            if (overlayRef[0] != null) {
                overlayRef[0].removeSelf();
                overlayRef[0] = null;
            } else {
                player.closeContainer();
            }
        });
        return root;
    }

    private static UIElement buildMainPanel(Player player, UIElement root, UIElement[] overlayRef) {
        UIElement panel = new UIElement()
                .layout(layout -> layout.width(PANEL_WIDTH).heightPercent(85)
                        .paddingAll(12).gapAll(8)
                        .flexDirection(FlexDirection.COLUMN)
                        .alignItems(AlignItems.STRETCH))
                .style(style -> style.backgroundTexture(Sprites.RECT_RD));

        panel.addChild(new TextElement()
                .setText(Component.translatable(KEY_TITLE))
                .textStyle(style -> style.fontSize(13)
                        .textColor(0xFFFFFFFF)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .adaptiveHeight(true)));

        panel.addChild(new TextElement()
                .setText(Component.translatable(KEY_DRAG_HINT))
                .textStyle(style -> style.fontSize(9)
                        .textColor(0xFF9A9A9A)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .adaptiveHeight(true)));

        ScrollerView scroller = new ScrollerView();
        scroller.layout(layout -> layout.width(PANEL_WIDTH - 24).flex(1));
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .minScrollPixel(40).maxScrollPixel(40));
        scroller.verticalScroller.scrollerStyle(style -> style.scrollDelta(SCROLL_DELTA));
        scroller.viewContainer.layout(layout -> layout
                .flexDirection(FlexDirection.COLUMN)
                .paddingTop(1)
                .gapAll(8)
                .alignItems(AlignItems.CENTER));

        Grid grid = new Grid(root, scroller, player);
        DragSession drag = new DragSession();
        wireDrag(grid, drag);

        // 客户端登记「每帧驱动」会话（服务端也在建同一棵树，但那边没有渲染帧）
        if (!(player instanceof ServerPlayer)) {
            SESSIONS.clear();
            SESSIONS.add(new Session(grid, drag));
            root.addEventListener(UIEvents.REMOVED, event -> {
                for (Session session : new ArrayList<>(SESSIONS)) {
                    if (session.grid == grid) SESSIONS.remove(session);
                }
            });
        }

        List<AbilityType> ordered = resolveOrder(player);
        UIElement row = null;
        int col = 0;
        for (AbilityType type : ordered) {
            if (col == 0) {
                row = newCardRow();
                scroller.addScrollViewChild(row);
                grid.rows.add(row);
            }
            CardRef ref = buildCard(player, type, overlayRef);
            grid.cardRefs.add(ref);
            row.addChild(ref.card);
            bindCardDrag(grid, drag, ref);
            col = (col + 1) % 2;
        }

        for (CardRef ref : grid.cardRefs) {
            if (!ref.unlocked) continue;
            ref.selectBtn.setOnClick(event -> selectFromButton(ref, grid));
        }

        panel.addChild(scroller);
        return panel;
    }

    private static UIElement newCardRow() {
        return new UIElement()
                .layout(layout -> layout.width(PANEL_WIDTH - 24 - 10 - 20)
                        .flexDirection(FlexDirection.ROW)
                        .gapAll(8));
    }

    // ========== 选择（客户端发出，服务端权威写入相机） ==========

    private static void selectFromButton(CardRef ref, Grid grid) {
        if (!ref.unlocked) return;
        boolean cancel = ref.inUse;
        updateSelection(ref, grid.cardRefs);
        if (grid.player instanceof ServerPlayer) return;
        int ordinal = cancel ? -1 : ref.type.ordinal();
        PacketDistributor.sendToServer(new AbilitySelectPacket(ordinal));
        ClientAbilityCache.setHeldCameraSelected(ordinal);
    }

    private static void updateSelection(CardRef selected, List<CardRef> cardRefs) {
        if (selected.inUse) {
            selected.setInUse(false);
            return;
        }
        for (CardRef ref : cardRefs) {
            if (ref.inUse && ref != selected) {
                ref.setInUse(false);
            }
        }
        selected.setInUse(true);
    }

    // ========== 拖拽整理 ==========

    private static void wireDrag(Grid grid, DragSession drag) {
        grid.root.addEventListener(UIEvents.MOUSE_MOVE, event -> onMouseMove(grid, drag, event));
        grid.root.addEventListener(UIEvents.MOUSE_UP, event -> onMouseUp(grid, drag));
        // 长按计时、跟手、让位缓动、自动滚动、松手兜底全部走**每帧**驱动
        // （见 {@link #onFrame()}）——UI 自带的 TICK 只有 20Hz，拿它推动画会明显掉帧。
    }

    /**
     * 每帧驱动（由 {@code AbilityGuiFrameTicker} 在屏幕渲染前调用）。
     *
     * <h2>为什么必须每帧</h2>
     * {@code UIEvents.TICK} 是客户端 tick（20Hz，50ms 一步），用它推动画时让位与收敛
     * 都是 20fps 的台阶，肉眼看就是「平滑帧数不高」。这里改由渲染前逐帧推进，
     * 并且缓动全部按<b>真实时间差</b>算（时间常数 SHIFT_TAU_MS / FLIP_TAU_MS），
     * 帧率高时更顺、掉帧时也不会变慢。
     */
    public static void onFrame() {
        if (SESSIONS.isEmpty()) return;
        // 菜单已经关掉（屏幕上没界面了）就别再推：顺手把会话清干净，避免留着空转
        if (Minecraft.getInstance().screen == null) {
            SESSIONS.clear();
            return;
        }
        long now = Util.getMillis();
        for (Session session : new ArrayList<>(SESSIONS)) {
            Grid grid = session.grid;
            DragSession drag = session.drag;
            tickFrameClock(grid, now);
            smoothScroll(grid);

            if (drag.source == null) {
                settle(grid);
                continue;
            }
            boolean leftDown = isLeftMouseDown();
            if (!drag.dragging) {
                if (!leftDown) {
                    cleanupDrag(grid, drag);
                    continue;
                }
                if (now - drag.pressAtMs >= DRAG_HOLD_MS) beginDrag(grid, drag);
                settle(grid);
                continue;
            }
            if (!leftDown) {
                finishDrag(grid, drag);
                continue;
            }
            updateDragVisuals(grid, drag);
            autoScroll(grid, drag);
        }
    }

    /** 记下本帧时间差（同帧多次调用时 dt 会趋近 1ms，效果可以忽略，不会重复推进） */
    private static void tickFrameClock(Grid grid, long nowMs) {
        long last = grid.lastFrameMs;
        grid.lastFrameMs = nowMs;
        grid.dtMs = last == 0L ? 16f : Math.max(1f, Math.min(80f, nowMs - last));
    }

    /**
     * 滚动平滑（ModernUI 那套「目标 + 追赶」）：
     * <p>
     * 滚轮/点击滚动条时，库会把 {@code verticalScroller} 的值<b>瞬间</b>改掉一截。
     * 这里每帧对比「库里的值」与「我们上一帧写下去的值」：
     * <ul>
     *   <li>发现外部改动 → 把那个新值收编成<b>目标</b>，并把显示值<b>拉回原位</b>（这一帧不会跳）；</li>
     *   <li>然后按 1 - e^(-dt/τ) 让显示值追目标，于是滚轮变成「一段短动画」而不是瞬移。</li>
     * </ul>
     * 拖到边缘的自动滚动也往同一个目标上加量，两条路径共用一套缓动，不会互相打架。
     */
    private static void smoothScroll(Grid grid) {
        var scroller = grid.scroller.verticalScroller;
        grid.contentShiftPx = 0f;
        Float raw = scroller.getValue();
        float current = raw == null ? 0f : raw;
        if (!grid.scrollInit) {
            grid.scrollInit = true;
            grid.scrollValue = current;
            grid.scrollTarget = current;
            return;
        }
        if (Math.abs(current - grid.scrollValue) > 1e-4f) {
            // 外部改了值（滚轮一格 / 拖滚动条 / Home 键之类）：收编为目标，显示值先退回原位
            grid.scrollTarget = Math.max(0f, Math.min(1f, current));
            scroller.setValue(grid.scrollValue);
        }

        float diff = grid.scrollTarget - grid.scrollValue;
        if (Math.abs(diff) <= 1e-5f) return;
        // 存进去的值必须与写下去的值完全一致（否则下一帧会被当成「外部改动」，来回打架）
        float before = grid.scrollValue;
        grid.scrollValue = Math.max(0f, Math.min(1f, grid.scrollValue + diff * easeStep(grid, SCROLL_TAU_MS)));
        scroller.setValue(grid.scrollValue);

        // 这一帧布局会比我们读到的坐标再前进这么多像素（下方 updateDragVisuals 用它做跟手补偿）
        float scrollablePx = grid.scroller.getContainerHeight() - grid.scroller.viewPort.getContentHeight();
        if (scrollablePx > 1f) {
            grid.contentShiftPx = (grid.scrollValue - before) * scrollablePx;
        }
    }

    /** 指数缓动步长：1 - e^(-dt/τ)，与帧率无关 */
    private static float easeStep(Grid grid, float tauMs) {
        return 1f - (float) Math.exp(-grid.dtMs / tauMs);
    }

    private static void bindCardDrag(Grid grid, DragSession drag, CardRef ref) {
        ref.card.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (isWithin(event.target, ref.selectBtn)) return;
            if (isWithin(event.target, ref.detailBtn)) return;
            if (grid.player instanceof ServerPlayer) return;
            if (drag.source != null) return;
            var world = worldOf(event);
            drag.source = ref;
            drag.pressAtMs = Util.getMillis();
            drag.worldX = world.x;
            drag.worldY = world.y;
            drag.dragging = false;
            drag.pendingOrder = null;
        });
    }

    private static void onMouseMove(Grid grid, DragSession drag, UIEvent event) {
        if (drag.source == null) return;
        var world = worldOf(event);
        drag.worldX = world.x;
        drag.worldY = world.y;
        if (!drag.dragging) {
            if (Util.getMillis() - drag.pressAtMs < DRAG_HOLD_MS) return;
            beginDrag(grid, drag);
            return;
        }
        updateDragVisuals(grid, drag);
        autoScroll(grid, drag);
    }

    private static void onMouseUp(Grid grid, DragSession drag) {
        if (drag.source == null) return;
        if (drag.dragging) {
            finishDrag(grid, drag);
        } else {
            cleanupDrag(grid, drag);
        }
    }

    /** 物理左键是否按着（GLFW 直读：UI 事件可能被某个子元素吃掉，物理状态不会骗人） */
    private static boolean isLeftMouseDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    /**
     * 进入拖拽：这张卡自己抬起来。
     * <p>
     * 贴图换成同款但更亮的 {@code RECT_RD_LIGHT}——高亮<b>就是这张卡本身</b>，
     * 所以永远与卡片形态完全贴合，不会有「矩形盖在圆角卡上」的硬边。
     */
    private static void beginDrag(Grid grid, DragSession drag) {
        CardRef source = drag.source;
        if (source == null) return;
        drag.dragging = true;
        drag.pendingOrder = null;
        CardVis vis = vis(grid, source);
        vis.shiftX = 0f;
        vis.shiftY = 0f;
        vis.flipX = 0f;
        vis.flipY = 0f;
        source.card.style(style -> style.backgroundTexture(Sprites.RECT_RD_LIGHT).zIndex(50));
        drag.targetSlot = computeTargetSlot(grid.cardRefs, source, drag.worldX, drag.worldY);
        drag.candidateSlot = drag.targetSlot;
        drag.candidateSinceMs = Util.getMillis();
        updateDragVisuals(grid, drag);
    }

    /**
     * 拖拽中的每帧视觉：
     * <ol>
     *   <li>算出目标格 {@code T}（按「其余卡片」的顺序 + 光标位置）；</li>
     *   <li>每张卡的目标位移 = 目标格的坐标 − 自己当前格的坐标，逐 tick 指数逼近（平滑让位）；</li>
     *   <li>被拖那张不参与让位，直接跟手（偏移 = 光标 − 自己布局中心）。</li>
     * </ol>
     */
    private static void updateDragVisuals(Grid grid, DragSession drag) {
        CardRef source = drag.source;
        if (source == null) return;
        List<CardRef> refs = grid.cardRefs;
        int raw = computeTargetSlot(refs, source, drag.worldX, drag.worldY);
        // 落位按光标当下的真实位次：松手后卡片由残余缓动平滑飞进各自的格子
        drag.pendingOrder = orderWithDraggedAt(refs, source, raw);
        // 视觉让位用「稳住才认」的位次，避免自动滚动时邻居反复挪一整卡高
        int target = gatedTargetSlot(drag, raw);
        List<CardRef> order = orderWithDraggedAt(refs, source, target);

        for (CardRef ref : refs) {
            CardVis vis = vis(grid, ref);
            if (ref == source) {
                vis.shiftX = 0f;
                vis.shiftY = 0f;
                // 绝对跟手：卡片始终被摆在光标下（布局坐标含滚动偏移，滚动时它会变）。
                // 关键：布局要到渲染期才结算，我们此刻读到的是「本帧滚动之前」的坐标，
                // 而真正画的时候内容已经又上移了 contentShiftPx → 不减掉它，卡片就会比光标
                // 偏一个「本帧滚动量」，滚动速度越快偏得越多、且随帧时间抖动 → 看起来就是上下震颤。
                vis.dragX = drag.worldX - (ref.card.getPositionX() + ref.card.getSizeWidth() / 2f);
                vis.dragY = drag.worldY
                        - (ref.card.getPositionY() + ref.card.getSizeHeight() / 2f - grid.contentShiftPx);
            } else {
                int newIndex = order.indexOf(ref);
                if (newIndex < 0 || newIndex >= refs.size()) newIndex = refs.indexOf(ref);
                // 目标格坐标 = 当前占着那个格子的卡片的布局坐标
                UIElement slot = refs.get(newIndex).card;
                float targetX = slot.getPositionX() - ref.card.getPositionX();
                float targetY = slot.getPositionY() - ref.card.getPositionY();
                float step = easeStep(grid, SHIFT_TAU_MS);
                vis.shiftX += (targetX - vis.shiftX) * step;
                vis.shiftY += (targetY - vis.shiftY) * step;
                if (Math.abs(targetX - vis.shiftX) < EPSILON) vis.shiftX = targetX;
                if (Math.abs(targetY - vis.shiftY) < EPSILON) vis.shiftY = targetY;
            }
            applyTransform(ref, vis);
        }
    }

    /**
     * 让位位次的门槛：正常拖动（没在自动滚动）立刻跟手；自动滚动期间要求位次先稳定
     * {@link #SLOT_HOLD_MS} 才让邻居动，把「内容从光标下流过导致的连续换位」压成一次性让位。
     */
    private static int gatedTargetSlot(DragSession drag, int raw) {
        long now = Util.getMillis();
        if (raw != drag.candidateSlot) {
            drag.candidateSlot = raw;
            drag.candidateSinceMs = now;
        }
        long hold = drag.scrollDir != 0 ? SLOT_HOLD_MS : 0L;
        if (raw != drag.targetSlot && now - drag.candidateSinceMs >= hold) {
            drag.targetSlot = raw;
        }
        return drag.targetSlot;
    }

    /** 由光标算「插到其余卡片序列的第几位」（不含被拖那张） */
    private static int computeTargetSlot(List<CardRef> refs, CardRef dragged, float mouseX, float mouseY) {
        int slot = 0;
        for (CardRef ref : refs) {
            if (ref == dragged) continue;
            UIElement card = ref.card;
            float top = card.getPositionY();
            float bottom = top + card.getSizeHeight();
            if (mouseY < top) return slot;
            if (mouseY <= bottom && mouseX < card.getPositionX() + card.getSizeWidth() / 2f) return slot;
            slot++;
        }
        return slot;
    }

    private static List<CardRef> orderWithDraggedAt(List<CardRef> refs, CardRef dragged, int target) {
        List<CardRef> order = new ArrayList<>(refs.size());
        for (CardRef ref : refs) {
            if (ref != dragged) order.add(ref);
        }
        int index = Math.max(0, Math.min(order.size(), target));
        order.add(index, dragged);
        return order;
    }

    /**
     * 拖到视口上/下边缘时的自动滚动（逐帧、亚像素平滑）。
     * <p>
     * 速度曲线：<b>起步几乎不动</b>，在 {@link #SCROLL_RAMP_MS} 内按平方曲线加速，
     * 最终稳定在 {@link #SCROLL_MAX_PX_PER_SEC}（真·像素/秒）。
     * <p>
     * <b>单位换算（之前踩过的坑）</b>：{@code ScrollerView.verticalScroller} 是 0~1 的
     * <b>归一化</b>值，{@code getValue()} 拿到的也是 0~1，{@code onVerticalScroll(value)}
     * 里才乘回「内容高 − 视口高」变成像素。所以像素必须自己除回可滚动总距离，
     * 否则传进去的 {@code 4} 会被当成「滚 4 倍全程」。
     * <p>
     * 另外这里走 {@link com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller#setValue(Float)} 而
     * 不是 {@code scrollValue(...)}：后者会把增量钳到「最小一格」（{@code minScrollPixel}），
     * 慢速下每帧只有零点几像素，会被抬成一大格、反而更跳。
     * 直接设值则每帧的零头都写进去，布局偏移是 float，画出来就是连续爬行。
     */
    private static void autoScroll(Grid grid, DragSession drag) {
        UIElement viewport = grid.scroller.viewPort;
        float top = viewport.getPositionY();
        float bottom = top + viewport.getSizeHeight();
        int dir = 0;
        if (drag.worldY > bottom - DRAG_EDGE_PX) {
            dir = 1;
        } else if (drag.worldY < top + DRAG_EDGE_PX) {
            dir = -1;
        }
        // 换方向或离开边缘：加速进度归零，下次贴上边缘重新从「极慢」起步
        if (dir != drag.scrollDir) {
            drag.scrollDir = dir;
            drag.scrollRamp = 0f;
        }
        if (dir == 0) return;

        // 可滚动的像素总量：内容高 − 视口高（布局还没算出来时先不滚）
        float scrollablePx = grid.scroller.getContainerHeight() - viewport.getContentHeight();
        if (scrollablePx <= 1f) return;

        // 初速 SCROLL_MIN → 满速 SCROLL_MAX，中间按下凹曲线爬升（先慢后快，不会一贴边就窜）
        drag.scrollRamp = Math.min(1f, drag.scrollRamp + grid.dtMs / SCROLL_RAMP_MS);
        float ramp = drag.scrollRamp * drag.scrollRamp;
        float speed = SCROLL_MIN_PX_PER_SEC + (SCROLL_MAX_PX_PER_SEC - SCROLL_MIN_PX_PER_SEC) * ramp;
        float pixels = dir * speed * (grid.dtMs / 1000f);

        // 加在「目标」上，由 smoothScroll 的缓动去追：自动滚动和滚轮共用同一条平滑链路
        grid.scrollTarget = Math.max(0f, Math.min(1f, grid.scrollTarget + pixels / scrollablePx));
    }

    /**
     * 松手落位：先在当前视觉位置上算好「残余偏移」，再换 DOM 顺序，最后让残余偏移收敛到 0。
     * <p>
     * 这样无论松手瞬间让位缓动有没有走完，卡片都是从「它现在看起来的位置」继续滑进新格子，
     * 不会出现一帧跳位。
     */
    private static void finishDrag(Grid grid, DragSession drag) {
        CardRef source = drag.source;
        if (source == null) {
            cleanupDrag(grid, drag);
            return;
        }
        List<CardRef> refs = grid.cardRefs;
        List<CardRef> order = drag.pendingOrder != null ? drag.pendingOrder : new ArrayList<>(refs);
        boolean changed = !sameOrder(order, refs);

        // 残余偏移 = 当前视觉位置 − 换位后的布局位置；换位后的布局位置 = 目标格坐标
        Map<CardRef, float[]> residual = new HashMap<>();
        for (CardRef ref : refs) {
            CardVis vis = vis(grid, ref);
            int newIndex = order.indexOf(ref);
            if (newIndex < 0 || newIndex >= refs.size()) newIndex = refs.indexOf(ref);
            UIElement slot = refs.get(newIndex).card;
            float targetX = slot.getPositionX() - ref.card.getPositionX();
            float targetY = slot.getPositionY() - ref.card.getPositionY();
            float startX;
            float startY;
            if (ref == source) {
                startX = vis.dragX - targetX;
                startY = vis.dragY - targetY;
            } else {
                startX = vis.shiftX - targetX;
                startY = vis.shiftY - targetY;
            }
            residual.put(ref, new float[]{startX, startY});
            vis.shiftX = 0f;
            vis.shiftY = 0f;
            vis.dragX = 0f;
            vis.dragY = 0f;
        }

        // 抬起外观还原
        source.card.style(style -> style.backgroundTexture(Sprites.RECT_RD).zIndex(0));

        if (changed) reorderDom(grid, order);

        for (CardRef ref : refs) {
            CardVis vis = vis(grid, ref);
            float[] start = residual.get(ref);
            vis.flipX = start == null ? 0f : start[0];
            vis.flipY = start == null ? 0f : start[1];
            applyTransform(ref, vis);
        }

        drag.dragging = false;
        drag.source = null;
        drag.pendingOrder = null;

        if (changed) {
            int[] ordinals = new int[order.size()];
            for (int i = 0; i < ordinals.length; i++) ordinals[i] = order.get(i).type.ordinal();
            if (!(grid.player instanceof ServerPlayer)) {
                PacketDistributor.sendToServer(new AbilityOrderSetPacket(ordinals));
                // 本地先认这份顺序：玩家「松手就走 / 关掉再打开」时不会看到旧排列
                List<AbilityType> types = new ArrayList<>(order.size());
                for (CardRef ref : order) types.add(ref.type);
                ClientAbilityCache.setDisplayOrder(types);
            }
        }
    }

    private static boolean sameOrder(List<CardRef> a, List<CardRef> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) return false;
        }
        return true;
    }

    /** 只重排已有元素、不重建：元素身份不变，按钮状态与滚轮位置都不会丢 */
    private static void reorderDom(Grid grid, List<CardRef> order) {
        List<CardRef> refs = grid.cardRefs;
        if (sameOrder(order, refs)) return;

        for (CardRef ref : refs) ref.card.removeSelf();

        int needRows = (order.size() + 1) / 2;
        while (grid.rows.size() < needRows) {
            UIElement row = newCardRow();
            grid.scroller.addScrollViewChild(row);
            grid.rows.add(row);
        }
        while (grid.rows.size() > needRows) {
            grid.rows.remove(grid.rows.size() - 1).removeSelf();
        }
        for (int i = 0; i < order.size(); i++) {
            grid.rows.get(i / 2).addChild(order.get(i).card);
        }
        refs.clear();
        refs.addAll(order);
    }

    /** 松手后的残余偏移逐 tick 收敛；没有残余就什么都不做 */
    private static void settle(Grid grid) {
        for (CardRef ref : grid.cardRefs) {
            CardVis vis = grid.vis.get(ref);
            if (vis == null) continue;
            boolean easing = Math.abs(vis.flipX) > EPSILON || Math.abs(vis.flipY) > EPSILON;
            boolean residual = vis.shiftX != 0f || vis.shiftY != 0f || vis.dragX != 0f || vis.dragY != 0f;
            if (!easing && !residual) continue;
            if (easing) {
                float step = easeStep(grid, FLIP_TAU_MS);
                vis.flipX -= vis.flipX * step;
                vis.flipY -= vis.flipY * step;
            } else {
                vis.flipX = 0f;
                vis.flipY = 0f;
            }
            vis.shiftX = 0f;
            vis.shiftY = 0f;
            vis.dragX = 0f;
            vis.dragY = 0f;
            applyTransform(ref, vis);
        }
    }

    private static void cleanupDrag(Grid grid, DragSession drag) {
        CardRef source = drag.source;
        if (source != null) {
            CardVis vis = vis(grid, source);
            if (drag.dragging) {
                source.card.style(style -> style.backgroundTexture(Sprites.RECT_RD).zIndex(0));
            }
            vis.dragX = 0f;
            vis.dragY = 0f;
            applyTransform(source, vis);
        }
        drag.dragging = false;
        drag.source = null;
        drag.pendingOrder = null;
    }

    private static CardVis vis(Grid grid, CardRef ref) {
        return grid.vis.computeIfAbsent(ref, key -> new CardVis());
    }

    /** 视觉位移落到 transform2D 上（只平移，不缩放：避免与布局枢轴打架） */
    private static void applyTransform(CardRef ref, CardVis vis) {
        float tx = vis.shiftX + vis.flipX + vis.dragX;
        float ty = vis.shiftY + vis.flipY + vis.dragY;
        ref.card.transform(transform -> transform.setIdentity().translate(tx, ty));
    }

    /** 事件坐标 → UI 画布绝对坐标（与 {@code getPositionX/Y} 同一坐标系） */
    private static org.joml.Vector2f worldOf(UIEvent event) {
        UIElement element = event.currentElement != null ? event.currentElement : event.target;
        if (element == null) return new org.joml.Vector2f(event.x, event.y);
        return element.getWorldMouse(event.x, event.y);
    }

    // ========== 能力卡片 ==========

    private static CardRef buildCard(Player player, AbilityType type, UIElement[] overlayRef) {
        boolean unlocked = isUnlocked(player, type);
        boolean inUse = isEnabled(player, type);

        IGuiTexture cardTexture = Sprites.RECT_RD;
        UIElement card = new UIElement()
                .layout(layout -> layout.width(CARD_WIDTH)
                        .paddingAll(6).gapAll(3)
                        .flexDirection(FlexDirection.COLUMN)
                        .alignItems(AlignItems.CENTER))
                .style(style -> style.backgroundTexture(cardTexture))
                .style(style -> style.tooltips(Component.translatable(type.getNameKey())));

        UIElement selectArea = new UIElement()
                .layout(layout -> layout.width(CARD_WIDTH - 12)
                        .flexDirection(FlexDirection.COLUMN)
                        .alignItems(AlignItems.CENTER).gapAll(3));

        ItemStack iconStack = new ItemStack(
                BuiltInRegistries.ITEM.get(type.getIconItemId()));
        selectArea.addChild(new UIElement()
                .layout(layout -> layout.width(20).height(20))
                .style(style -> style.backgroundTexture(new ItemStackTexture(iconStack))));

        selectArea.addChild(new TextElement()
                .setText(Component.translatable(type.getNameKey()))
                .textStyle(style -> style.fontSize(10)
                        .textColor(0xFFFFFFFF)
                        .textWrap(TextWrap.NONE)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .adaptiveHeight(true))
                .layout(layout -> layout.width(CARD_WIDTH - 12)));

        UIElement buttonRow = new UIElement()
                .layout(layout -> layout.flexDirection(FlexDirection.ROW)
                        .gapAll(4).justifyContent(AlignContent.CENTER));
        selectArea.addChild(buttonRow);

        Button selectBtn = new Button()
                .setText(Component.translatable(
                        inUse ? KEY_IN_USE : (unlocked ? KEY_SELECT : KEY_LOCKED)))
                .textStyle(style -> style.fontSize(9)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .adaptiveHeight(true)
                        .textColor(inUse ? 0xFF55FF55 : (unlocked ? 0xFFFFFFFF : 0xFFFF5555)));
        selectBtn.setActive(unlocked);
        buttonRow.addChild(selectBtn);

        Button detailBtn = new Button()
                .setText(Component.translatable(KEY_DETAIL))
                .textStyle(style -> style.fontSize(9)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .adaptiveHeight(true));
        detailBtn.setOnClick(event -> openDetailOverlay(player, rootOf(card), type, overlayRef));
        buttonRow.addChild(detailBtn);

        card.addChild(selectArea);

        return new CardRef(type, card, selectArea, selectBtn, detailBtn, unlocked, inUse);
    }

    // ========== 详情弹层 ==========

    private static void openDetailOverlay(Player player, UIElement root, AbilityType type,
                                          UIElement[] overlayRef) {
        if (overlayRef[0] != null) return;

        UIElement overlay = new UIElement()
                .layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE)
                        .left(0).top(0)
                        .widthPercent(100).heightPercent(100)
                        .flexDirection(FlexDirection.COLUMN)
                        .alignItems(AlignItems.CENTER)
                        .justifyContent(AlignContent.CENTER))
                .style(style -> style.backgroundTexture(new ColorRectTexture(0x50000000))
                        .zIndex(100));

        overlay.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.target != overlay) return;
            overlay.removeSelf();
            overlayRef[0] = null;
        });

        overlay.addChild(buildDetailPanel(type, overlayRef));
        overlayRef[0] = overlay;
        root.addChild(overlay);
    }

    private static UIElement buildDetailPanel(AbilityType type, UIElement[] overlayRef) {
        UIElement panel = new UIElement()
                .layout(layout -> layout.width(350).heightPercent(85)
                        .paddingAll(12).gapAll(8)
                        .flexDirection(FlexDirection.COLUMN)
                        .alignItems(AlignItems.STRETCH))
                .style(style -> style.backgroundTexture(Sprites.RECT_RD))
                .addEventListener(UIEvents.MOUSE_DOWN, UIEvent::stopPropagation);

        panel.addChild(new TextElement()
                .setText(Component.translatable(type.getNameKey()))
                .textStyle(style -> style.fontSize(13)
                        .textColor(0xFFFFFFFF)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .adaptiveHeight(true)));

        ScrollerView scroller = new ScrollerView();
        scroller.layout(layout -> layout.width(326).flex(1));
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .minScrollPixel(40).maxScrollPixel(40));
        scroller.verticalScroller.scrollerStyle(style -> style.scrollDelta(SCROLL_DELTA));
        scroller.viewContainer.layout(layout -> layout
                .flexDirection(FlexDirection.COLUMN)
                .gapAll(4));
        scroller.addScrollViewChild(new TextElement()
                .setText(Component.translatable(type.getDetailKey()))
                .textStyle(style -> style.fontSize(10)
                        .textColor(0xFFE0E0E0)
                        .textWrap(TextWrap.WRAP)
                        .lineSpacing(4)
                        .adaptiveHeight(true)));
        panel.addChild(scroller);

        Button backBtn = new Button()
                .setText(Component.translatable(KEY_BACK))
                .textStyle(style -> style.fontSize(9)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .adaptiveHeight(true));
        backBtn.layout(layout -> layout.width(163));
        backBtn.setOnClick(event -> {
            if (overlayRef[0] != null) {
                overlayRef[0].removeSelf();
                overlayRef[0] = null;
            }
        });
        UIElement backRow = new UIElement()
                .layout(layout -> layout.flexDirection(FlexDirection.ROW)
                        .justifyContent(AlignContent.CENTER));
        backRow.addChild(backBtn);
        panel.addChild(backRow);
        return panel;
    }

    // ========== 工具 ==========

    private static UIElement rootOf(UIElement element) {
        UIElement current = element;
        while (true) {
            var parent = current.getParent();
            if (parent == null) return current;
            current = parent;
        }
    }

    private static boolean isWithin(UIElement target, UIElement ancestor) {
        UIElement cur = target;
        while (cur != null) {
            if (cur == ancestor) return true;
            cur = cur.getParent();
        }
        return false;
    }

    // ========== 状态查询（两端各自查询，服务端权威） ==========

    private static boolean isUnlocked(Player player, AbilityType type) {
        if (player instanceof ServerPlayer) {
            return AbilityManager.getInstance().isUnlocked(player, type);
        }
        return ClientAbilityCache.isUnlocked(type);
    }

    private static List<AbilityType> resolveOrder(Player player) {
        List<AbilityType> order;
        if (player instanceof ServerPlayer sp) {
            order = AbilityOrderData.get(sp.getServer()).resolve(sp);
        } else {
            order = ClientAbilityCache.getDisplayOrder();
        }
        // 胶卷相机与拍立得各用一套列表：按手持相机类型筛掉对方专属的能力
        return com.plumejade.lensouls.ability.CameraAbilityRules.filterFor(order,
                com.plumejade.lensouls.ability.CameraStacks.getWieldedCamera(player));
    }

    private static boolean isEnabled(Player player, AbilityType type) {
        if (player instanceof ServerPlayer sp) {
            AbilityType sel = CameraAbilityStore.getSelectedType(com.plumejade.lensouls.ability.CameraStacks.getWieldedCamera(sp));
            return sel == type && AbilityManager.getInstance().isUnlocked(sp, type);
        }
        AbilityType sel = CameraAbilityStore.getSelectedType(com.plumejade.lensouls.ability.CameraStacks.getWieldedCamera(player));
        return sel == type && ClientAbilityCache.isUnlocked(type);
    }
}
