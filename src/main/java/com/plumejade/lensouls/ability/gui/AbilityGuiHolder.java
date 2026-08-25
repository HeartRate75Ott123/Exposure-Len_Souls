package com.plumejade.lensouls.ability.gui;

import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
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
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.CameraAbilityStore;
import com.plumejade.lensouls.ability.client.ClientAbilityCache;
import com.plumejade.lensouls.ability.handler.CameraInputHandler;
import com.plumejade.lensouls.ability.network.CameraAbilitySyncPacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 能力选择 GUI（LDLib2 服务端菜单）。
 * <p>
 * 服务端与客户端各自构建 UI 树：
 * <ul>
 *   <li>每张卡片分为「选择区域」（图标 / 名称 / 选择按钮，整块可点击）与「详情」按钮（平级），
 *       点击选择区域 → 单个客户端监听做乐观更新 + 单个服务端监听做切换 RPC，
 *       详情按钮仅弹介绍框、绝不触发选中；</li>
 *   <li>选择区域点击为「切换式」：点已选中的卡取消、点其他卡选中（服务端权威，回声包同步 HUD）；</li>
 *   <li>点击一级菜单空白区域关闭整个菜单。</li>
 * </ul>
 * 卡片按 {@link AbilityType#values()} 枚举顺序自动收纳，新增能力无需改动本类。
 */
public class AbilityGuiHolder {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "ability_gui");

    private static final int PANEL_WIDTH = 350;
    private static final int CARD_WIDTH = 140;
    /** 滚轮单次滚动占溢出量的比例（默认 0.1，1.0 = 一格滚满） */
    private static final float SCROLL_DELTA = 1.0f;

    // ---- 翻译键 ----
    private static final String KEY_TITLE = "gui.lensouls.ability.title";
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
            // 解锁后按钮常驻可点（含使用中）：点击可切换取消选中
            selectBtn.setActive(unlocked);
        }
    }

    public static void register() {
        PlayerUIMenuType.register(ID, player -> new PlayerUIMenuType.PlayerUIHolder() {
            @Override
            public ModularUI createUI(Player p) {
                ModularUI ui = new ModularUI(UI.of(buildRoot(p)), p);
                // 登记客户端打开的实例，供移动注入 mixin 识别本 GUI
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

        // 详情弹层引用（null = 未打开）
        UIElement[] overlayRef = {null};

        root.addChild(buildMainPanel(player, root, overlayRef));

        // 点击空白：详情弹层打开 → 返回一级；否则关闭整个菜单
        root.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.target != root) return;
            if (overlayRef[0] != null) {
                overlayRef[0].removeSelf();
                overlayRef[0] = null;
            } else {
                player.closeContainer();
            }
        });
        // ESC 保持原版行为：直接关闭整个菜单（详情页关闭用「返回」按钮）
        return root;
    }

    private static UIElement buildMainPanel(Player player, UIElement root, UIElement[] overlayRef) {
        UIElement panel = new UIElement()
                .layout(layout -> layout.width(PANEL_WIDTH).heightPercent(85)
                        .paddingAll(12).gapAll(8)
                        .flexDirection(FlexDirection.COLUMN)
                        .alignItems(AlignItems.STRETCH))
                .style(style -> style.backgroundTexture(Sprites.RECT_RD));

        // 标题
        panel.addChild(new TextElement()
                .setText(Component.translatable(KEY_TITLE))
                .textStyle(style -> style.fontSize(13)
                        .textColor(0xFFFFFFFF)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .adaptiveHeight(true)));

        // 卡片网格：手动每行两张（viewContainer 固定宽 = 面板 350 - padding 24 - 视口 padding 10 - 滚动条 20）
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

        // 构建全部卡片（每两张一行），收集引用以便「选择」时实时切换状态
        List<CardRef> cardRefs = new ArrayList<>();
        // 排序：已解锁按解锁顺序倒序（新解锁最前），未解锁按枚举序在后
        List<AbilityType> ordered = new ArrayList<>();
        List<AbilityType> unlockOrder = getUnlockOrder(player);
        for (int i = unlockOrder.size() - 1; i >= 0; i--) {
            AbilityType t = unlockOrder.get(i);
            if (isUnlocked(player, t) && !ordered.contains(t)) ordered.add(t);
        }
        for (AbilityType t : AbilityType.values()) {
            if (!isUnlocked(player, t)) ordered.add(t);
        }
        UIElement row = null;
        int col = 0;
        for (int i = 0; i < ordered.size(); i++) {
            if (col == 0) {
                row = new UIElement()
                        .layout(layout -> layout.width(PANEL_WIDTH - 24 - 10 - 20)
                                .flexDirection(FlexDirection.ROW)
                                .gapAll(8));
                scroller.addScrollViewChild(row);
            }
            CardRef ref = buildCard(player, ordered.get(i), overlayRef);
            cardRefs.add(ref);
            row.addChild(ref.card);
            col = (col + 1) % 2;
        }
        // 选择回调：选择区域内任意点击（背景 / 图标 / 选择按钮）均触发切换，
        // 服务端监听挂在选择按钮上（分发器自动发送），图标/名称区域手动补发，
        // 详情按钮点击被排除，绝不触发选中。整体仍只有一个服务端 RPC。
        for (CardRef ref : cardRefs) {
            if (!ref.unlocked) continue;
            ref.selectArea.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (isWithin(event.target, ref.detailBtn)) return;  // 详情按钮不触发选中
                updateSelection(ref, cardRefs);
                if (!isWithin(event.target, ref.selectBtn)) {
                    // 图标 / 名称 / 背景：手动补发服务端 RPC（选择按钮自身由分发器自动发送）
                    var rpc = ref.selectBtn.getBaubleServerEvent(UIEvents.MOUSE_DOWN);
                    if (rpc != null) ref.selectBtn.sendEvent(rpc, event);
                }
                event.stopPropagation();
            });
            ref.selectBtn.addServerEventListener(UIEvents.MOUSE_DOWN, event -> doSelectServer(ref, player));
        }
        panel.addChild(scroller);
        return panel;
    }

    private static void doSelectServer(CardRef ref, Player player) {
        if (player instanceof ServerPlayer sp) {
            ItemStack cam = sp.getMainHandItem();
            if (!CameraInputHandler.isCamera(cam)) return;
            // 切换式：点已选中的卡 → 取消；点其他卡 → 选中
            AbilityType current = CameraAbilityStore.getSelectedType(cam);
            if (current == ref.type) {
                CameraAbilityStore.clearSelected(cam);
                CameraAbilitySyncPacket.send(sp, -1);
            } else {
                if (!AbilityManager.getInstance().isUnlocked(sp, ref.type)) return;
                CameraAbilityStore.setSelected(cam, ref.type, sp);
                CameraAbilitySyncPacket.send(sp, ref.type.ordinal());
            }
        }
    }

    /** 客户端乐观更新：切换式——点已「使用中」的卡取消，点其他卡选中 */
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

    // ========== 能力卡片 ==========

    private static CardRef buildCard(Player player, AbilityType type, UIElement[] overlayRef) {
        boolean unlocked = isUnlocked(player, type);
        boolean inUse = isEnabled(player, type);

        // 整张卡片容器
        UIElement card = new UIElement()
                .layout(layout -> layout.width(CARD_WIDTH)
                        .paddingAll(6).gapAll(3)
                        .flexDirection(FlexDirection.COLUMN)
                        .alignItems(AlignItems.CENTER))
                .style(style -> style.backgroundTexture(Sprites.RECT_RD))
                .style(style -> style.tooltips(Component.translatable(type.getNameKey())));

        // 选择区域：图标 + 名称 + 选择按钮，整块可点击切换选中
        UIElement selectArea = new UIElement()
                .layout(layout -> layout.width(CARD_WIDTH - 12)
                        .flexDirection(FlexDirection.COLUMN)
                        .alignItems(AlignItems.CENTER).gapAll(3));

        // 图标（能力球占位）
        ItemStack iconStack = new ItemStack(
                BuiltInRegistries.ITEM.get(type.getIconItemId()));
        selectArea.addChild(new UIElement()
                .layout(layout -> layout.width(20).height(20))
                .style(style -> style.backgroundTexture(new ItemStackTexture(iconStack))));

        // 名称
        selectArea.addChild(new TextElement()
                .setText(Component.translatable(type.getNameKey()))
                .textStyle(style -> style.fontSize(10)
                        .textColor(0xFFFFFFFF)
                        .textWrap(TextWrap.NONE)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .adaptiveHeight(true))
                .layout(layout -> layout.width(CARD_WIDTH - 12)));

        // 按钮行：选择按钮（左）与详情按钮（右）同行
        UIElement buttonRow = new UIElement()
                .layout(layout -> layout.flexDirection(FlexDirection.ROW)
                        .gapAll(4).justifyContent(AlignContent.CENTER));
        selectArea.addChild(buttonRow);

        // 选择按钮：选中后变绿「使用中」，未解锁显示红「未解锁」；常驻可点，点击切换取消
        Button selectBtn = new Button()
                .setText(Component.translatable(
                        inUse ? KEY_IN_USE : (unlocked ? KEY_SELECT : KEY_LOCKED)))
                .textStyle(style -> style.fontSize(9)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .adaptiveHeight(true)
                        .textColor(inUse ? 0xFF55FF55 : (unlocked ? 0xFFFFFFFF : 0xFFFF5555)));
        selectBtn.setActive(unlocked);
        buttonRow.addChild(selectBtn);

        // 详情按钮：与选择按钮同行，点击只弹详情，不触发选中
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

        // 点弹层空白 → 移除弹层，返回一级菜单
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
                // 点击面板内部不关闭弹层
                .addEventListener(UIEvents.MOUSE_DOWN, UIEvent::stopPropagation);

        // 标题
        panel.addChild(new TextElement()
                .setText(Component.translatable(type.getNameKey()))
                .textStyle(style -> style.fontSize(13)
                        .textColor(0xFFFFFFFF)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .adaptiveHeight(true)));

        // 介绍长文案（超出显示滚动条，滚轮 + 拖拽滑块）
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

        // 返回按钮：关闭详情弹层回到一级菜单（宽度为面板内容宽的一半，避免被 stretch 拉全宽）
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

    /** 沿 UI 树向上查找根元素（详情按钮所在卡片 → 面板 → 根） */
    private static UIElement rootOf(UIElement element) {
        UIElement current = element;
        while (true) {
            var parent = current.getParent();
            if (parent == null) return current;
            current = parent;
        }
    }

    /** 判断 target 是否位于 ancestor 子树内（含自身）。用于区分详情按钮与选择区域。 */
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

    /** 解锁顺序（旧→新，最近解锁在尾部）；两端各自查询 */
    private static List<AbilityType> getUnlockOrder(Player player) {
        if (player instanceof ServerPlayer) {
            return AbilityManager.getInstance().getUnlockOrder(player);
        }
        return ClientAbilityCache.getUnlockOrder();
    }

    private static boolean isEnabled(Player player, AbilityType type) {
        if (player instanceof ServerPlayer sp) {
            AbilityType sel = CameraAbilityStore.getSelectedType(sp.getMainHandItem());
            return sel == type && AbilityManager.getInstance().isUnlocked(sp, type);
        }
        AbilityType sel = CameraAbilityStore.getSelectedType(player.getMainHandItem());
        return sel == type && ClientAbilityCache.isUnlocked(type);
    }
}