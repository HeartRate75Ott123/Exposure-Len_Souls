package com.plumejade.lensouls.client.feather;

import com.plumejade.lensouls.feather.FeatherSlotData;
import com.plumejade.lensouls.gui.FeatherSlotMenu;
import com.plumejade.lensouls.network.FeatherSlotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * 羽毛装配界面（全屏，l2tabs 分页点开后进入）。
 * <p>
 * 结构照 {@code ReinforceScreen}：{@code Screen implements MenuAccess<FeatherSlotMenu>}
 * （<b>刻意不是</b> {@code AbstractContainerScreen}——JEI 的接入判据是
 * {@code instanceof AbstractContainerScreen}，普通 Screen 后 JEI 完全不介入），
 * {@code render} 里不调 {@code super.render}，槽位全部自绘。
 * <p>
 * 渲染层级（移植自参考项目的实测结论）：背景 cover 铺满 → {@code graphics.flush()}
 * → {@link GlassPanel} 在面板矩形内合成磨砂玻璃 → 描边/标题/图标/文字压在玻璃之上。
 * 帧末合成会盖住文字，不要改回那种做法。
 * <p>
 * 交互规则见 {@link FeatherSlotMenu}；本屏只做即时反馈（{@link FeatherToast}），
 * 最终以服务端结算为准。
 */
public class FeatherScreen extends Screen implements MenuAccess<FeatherSlotMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/gui/feather_background.png");
    /**
     * 背景素材<b>真实尺寸</b>（cover 裁切用）。
     * <p>
     * ★ **换素材必须同步改这两个常量**：只按屏幕比例算裁切的公式离不开素材尺寸，
     * 写错就会拉伸——此前素材是 1620×1080（3:2）而这里没更新，实机表现就是「y 方向被拉长」。
     * 现资产为 1920×1080（16:9）。
     */
    private static final int TEX_W = 1920;
    private static final int TEX_H = 1080;

    /** 单个槽位边长 = 原版一个物品槽的大小（18px 含 1px 边框），槽间距与面板内边距 */
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 14;
    private static final float PANEL_RADIUS = 10.0F;
    /** 玻璃色调（深蓝灰，a = 混合强度）：底色偏亮，需要压一压保证羽毛清晰 */
    private static final int GLASS_TINT = 0x8C121A24;

    private final FeatherSlotMenu menu;
    private final Inventory inventory;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public FeatherScreen(FeatherSlotMenu menu, Inventory inventory, Component title) {
        super(title);
        this.menu = menu;
        this.inventory = inventory;
    }

    @Override
    public FeatherSlotMenu getMenu() {
        return this.menu;
    }

    /**
     * 面板纵向版式（全部由一个行距常量推导，改行距只动 {@link #LINE_GAP}）：
     * <ul>
     *   <li>{@code TITLE_Y} 12 —— 面板顶 → 第一行文字（上留白）</li>
     *   <li>{@code HINT_Y} 30、{@code SLOTS_Y} 48 —— 标题/提示/槽位行**行距一律 18**</li>
     *   <li>{@code BOTTOM_PAD} = {@code TITLE_Y} 12 —— 槽位底端 → 面板底部，
     *       与「面板顶 → 第一行文字」**完全相等**（用户口径）</li>
     * </ul>
     */
    private static final int TITLE_Y = 12;
    private static final int LINE_GAP = 18;
    private static final int HINT_Y = TITLE_Y + LINE_GAP;
    private static final int SLOTS_Y = HINT_Y + LINE_GAP;
    private static final int BOTTOM_PAD = TITLE_Y;

    @Override
    protected void init() {
        super.init();
        this.panelW = FeatherSlotData.SLOTS * SLOT_SIZE + (FeatherSlotData.SLOTS - 1) * SLOT_GAP + 48;
        // 上留白 + 两行文字 + 槽位行 + 下留白（= 行距 + 上留白，见 BOTTOM_PAD）
        this.panelH = SLOTS_Y + SLOT_SIZE + BOTTOM_PAD;
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;
        // 恢复进入本界面前的光标位置（必须晚于原版的居中处理）
        CursorKeeper.restore();
    }

    // ========== 布局 ==========

    private int slotX(int index) {
        int total = FeatherSlotData.SLOTS * SLOT_SIZE + (FeatherSlotData.SLOTS - 1) * SLOT_GAP;
        int startX = (this.width - total) / 2;
        return startX + index * (SLOT_SIZE + SLOT_GAP);
    }

    private int slotY() {
        return panelY + SLOTS_Y;
    }

    /** 点击是否落在面板矩形内（面板外点击 = 关闭） */
    private boolean insidePanel(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX < panelX + panelW
                && mouseY >= panelY && mouseY < panelY + panelH;
    }

    /** 关闭按钮：贴面板右上角（与参考项目同口径） */
    private int closeX() {
        return CloseButton.x(panelX + panelW);
    }

    private int closeY() {
        return CloseButton.y(panelY);
    }

    private int slotAt(double mouseX, double mouseY) {
        for (int i = 0; i < FeatherSlotData.SLOTS; i++) {
            int x = slotX(i);
            int y = slotY();
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                return i;
            }
        }
        return -1;
    }

    // ========== 渲染 ==========

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderCoverBackground(graphics);

        // ★ 层级关键：先把已经提交的背景（fill/blit 是延迟批处理）刷到主画面，再合成玻璃
        graphics.flush();
        if (GlassPanel.available()) {
            GlassPanel.renderPanelGlass(panelX, panelY, panelW, panelH, PANEL_RADIUS, GLASS_TINT);
        }
        // 面板描边（磨砂主体已由上面的玻璃合成负责）
        RoundedRect.roundedOutline(graphics, panelX, panelY, panelW, panelH, PANEL_RADIUS,
                0x40FFFFFF, 1.0F);

        Component title = Component.translatable("gui.lensouls.feather.title");
        graphics.drawString(this.font, title,
                panelX + (panelW - this.font.width(title)) / 2, panelY + TITLE_Y, 0xF2F6FF, true);

        Component hint = Component.translatable("gui.lensouls.feather.hint");
        graphics.drawString(this.font, hint,
                panelX + (panelW - this.font.width(hint)) / 2, panelY + HINT_Y, 0xAEB6C4, false);

        int hovered = slotAt(mouseX, mouseY);
        for (int i = 0; i < FeatherSlotData.SLOTS; i++) {
            renderSlot(graphics, i, hovered == i);
        }

        if (hovered >= 0) {
            renderSlotTooltip(graphics, mouseX, mouseY, hovered);
        }

        // 提示最后画 ⇒ 压在所有内容之上
        FeatherToast.render(graphics, this.width, this.height);

        // 关闭按钮（右上角，点它回玩家物品栏）
        CloseButton.render(graphics, closeX(), closeY(), mouseX, mouseY);
    }

    /** 背景 cover 铺满（等比放大到覆盖整个窗口，超出部分裁掉；素材 16:9 时恰好满屏） */
    private void renderCoverBackground(GuiGraphics graphics) {
        if (this.width <= 0 || this.height <= 0) return;
        float scale = Math.max((float) this.width / TEX_W, (float) this.height / TEX_H);
        int srcW = Math.max(1, (int) (this.width / scale));
        int srcH = Math.max(1, (int) (this.height / scale));
        int u = (TEX_W - srcW) / 2;
        int v = (TEX_H - srcH) / 2;
        graphics.blit(BACKGROUND, 0, 0, this.width, this.height, u, v, srcW, srcH, TEX_W, TEX_H);
    }

    private void renderSlot(GuiGraphics graphics, int index, boolean hovered) {
        int x = slotX(index);
        int y = slotY();
        boolean locked = this.menu.isLocked(index);
        ItemStack stack = this.menu.featherStack(index);

        // 槽底：圆角深色块（rrect shader；未就绪时自动回退直角）
        // 尺寸 = 原版物品槽（18px），不再使用 slot.png 素材——程式化画法更贴合物品图
        RoundedRect.roundedRect(graphics, x, y, SLOT_SIZE, SLOT_SIZE, 4.0F,
                hovered ? 0xE61B2330 : 0xCC0E141C,
                hovered ? 0xE6151C27 : 0xCC0A0F16,
                0x33FFFFFF, 1.0F);

        if (!stack.isEmpty()) {
            // 原版槽位的物品绘制位置（+1 避开边框）
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
        }

        // 锁定标记：金色描边（生存装入后永久锁定）
        if (locked) {
            RoundedRect.roundedOutline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, 4.0F,
                    0xE6FFC24D, 1.5F);
        }
    }

    private void renderSlotTooltip(GuiGraphics graphics, int mouseX, int mouseY, int index) {
        ItemStack stack = this.menu.featherStack(index);
        if (!stack.isEmpty()) {
            graphics.renderTooltip(this.font, stack, mouseX, mouseY);
            return;
        }
        Component tip = this.menu.isLocked(index)
                ? Component.translatable("gui.lensouls.feather.locked")
                : Component.translatable("gui.lensouls.feather.empty");
        graphics.renderTooltip(this.font, tip, mouseX, mouseY);
    }

    // ========== 交互 ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 关闭按钮优先（它在面板内、且在槽位行之上，必须先判）
        if (CloseButton.isOver(mouseX, mouseY, closeX(), closeY())) {
            this.onClose();
            return true;
        }

        int slot = slotAt(mouseX, mouseY);
        if (slot < 0) {
            // 面板外点击 ⇒ 直接关闭（回到玩家物品栏）；面板内空白处不响应
            if (!insidePanel(mouseX, mouseY)) {
                this.onClose();
            }
            return true;
        }

        ItemStack current = this.menu.featherStack(slot);
        boolean creative = this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.isCreative();

        if (button == 1) {
            // 右键：创造模式卸下；生存模式给提示（GUI 内浮动提示，动作栏消息会被界面盖住）
            if (current.isEmpty()) return true;
            if (!creative) {
                FeatherToast.show(Component.translatable("gui.lensouls.feather.creative_only"));
                return true;
            }
            PacketDistributor.sendToServer(FeatherSlotPacket.uninstall(slot));
            return true;
        }

        if (button == 0) {
            // 左键：允许修改才打开选择界面；否则给提示（生存锁定 / 已占用）
            if (!this.menu.canModify(slot)) {
                FeatherToast.show(Component.translatable(
                        this.menu.isLocked(slot)
                                ? "gui.lensouls.feather.locked"
                                : "gui.lensouls.feather.creative_only"));
                return true;
            }
            if (this.minecraft != null) {
                CursorKeeper.remember();
                this.minecraft.setScreen(new FeatherSelectScreen(this, this.menu, slot));
                CursorKeeper.restore();
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // E / ESC 快速返回
        if (keyCode == 256 || keyCode == 69) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 关闭一级菜单 = 回**玩家物品栏**（用户口径），而不是直接回游戏。
     * <p>
     * <b>为什么不能只调 super.onClose()</b>：原版只有 {@code AbstractContainerScreen.removed()} 会去关容器，
     * 而我们（同 ReinforceScreen）是普通 {@code Screen} —— 直接关屏会让服务端容器一直挂着，
     * 表现就是「一关就直接回游戏」。这里显式 {@code player.closeContainer()}：
     * 客户端会立刻把 {@code containerMenu} 切回 {@code inventoryMenu} 并发出关闭包，
     * 服务端随之回到物品栏容器，此时再打开 {@link InventoryScreen} 才是自洽的。
     */
    @Override
    public void onClose() {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.player == null) {
            super.onClose();
            return;
        }
        CursorKeeper.remember();
        mc.player.closeContainer();
        mc.setScreen(new InventoryScreen(mc.player));
        CursorKeeper.restore();   // 打开物品栏后把光标放回原位（否则会被刷到屏幕中央）
    }

    /** 供选择屏返回时复用（避免重新 init 丢布局） */
    public void reopen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.setScreen(this);
    }
}
