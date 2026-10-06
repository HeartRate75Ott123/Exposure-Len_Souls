package com.plumejade.lensouls.client.feather;

import com.plumejade.lensouls.client.CurseClientCache;
import com.plumejade.lensouls.feather.CurseDef;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.gui.FeatherSlotMenu;
import com.plumejade.lensouls.network.CurseInstallPacket;
import com.plumejade.lensouls.network.FeatherSlotPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 诅咒列表弹窗（{@code docs/诅咒系统-重设计方案.md} §4.1）。
 * <p>
 * 点 {@code FeatherScreen} 的任意方框进来，标题显示「第 N 槽」。
 * <b>列表固定包含全部 7 款诅咒</b>（与背包里有没有无关）：
 * <ul>
 *   <li><b>悬停</b>某行 ⇒ 渲染该款道具的完整 tooltip（常显 + Shift 两段，见 §4.2）</li>
 *   <li><b>左键</b>某行 ⇒ 直接装上：背包里有就搬一个过来，没有就发放一个；
 *       槽里已有则视为「更换」，旧物退回背包</li>
 *   <li><b>右键</b> ⇒ 创造模式撤下该槽；生存模式给提示（不可撤下）</li>
 * </ul>
 * 行数超过视口时：{@code mouseScrolled} 滚动 + {@code enableScissor} 裁切 + 右侧自绘滚动条
 * （口径照 {@code ReinforceScreen}）。
 */
public class CurseListScreen extends Screen {

    /** 行高 / 列表宽度 / 一屏可见行数 */
    private static final int ROW_H = 22;
    private static final int LIST_W = 300;
    private static final int VISIBLE_ROWS = 8;
    /** 行内元素：图标 16、内边距、滚动条宽 */
    private static final int ICON = 16;
    private static final int PAD = 6;
    private static final int SCROLLBAR_W = 4;

    private final Screen parent;
    private final FeatherSlotMenu menu;
    private final int targetSlot;

    /** 固定 7 款 */
    private final List<CurseDef> rows = new ArrayList<>();

    private int scroll;
    private int listX;
    private int listY;
    private int listH;

    public CurseListScreen(Screen parent, FeatherSlotMenu menu, int targetSlot) {
        super(Component.translatable("gui.lensouls.curse.list_title", targetSlot + 1));
        this.parent = parent;
        this.menu = menu;
        this.targetSlot = targetSlot;
    }

    @Override
    protected void init() {
        super.init();
        rows.clear();
        rows.addAll(CurseDefs.ALL);
        this.listH = ROW_H * Math.min(rows.size(), VISIBLE_ROWS);
        this.listX = (this.width - LIST_W) / 2;
        this.listY = (this.height - listH) / 2;
        this.scroll = 0;
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - VISIBLE_ROWS);
    }

    private boolean scrollable() {
        return rows.size() > VISIBLE_ROWS;
    }

    private int rowY(int visibleIndex) {
        return listY + visibleIndex * ROW_H;
    }

    /** 鼠标落在第几行（已折算滚动） */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listX || mouseX >= listX + LIST_W) return -1;
        if (mouseY < listY || mouseY >= listY + listH) return -1;
        int visible = (int) ((mouseY - listY) / ROW_H);
        int index = visible + scroll;
        return (index >= 0 && index < rows.size()) ? index : -1;
    }

    private boolean insideList(double mouseX, double mouseY) {
        return mouseX >= listX - 8 && mouseX < listX + LIST_W + 8
                && mouseY >= listY - 30 && mouseY < listY + listH + 22;
    }

    // ========== 渲染 ==========

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, 0xC0080C12);

        Component title = Component.translatable("gui.lensouls.curse.list_title", targetSlot + 1);
        graphics.drawString(this.font, title, (this.width - this.font.width(title)) / 2,
                listY - 26, 0xF2F6FF, true);

        RoundedRect.roundedRect(graphics, listX - 4, listY - 4, LIST_W + 8, listH + 8, 6.0F,
                0xCC0E141C, 0xCC0A0F16, 0x33FFFFFF, 1.0F);

        int hovered = rowAt(mouseX, mouseY);
        graphics.enableScissor(listX - 4, listY - 4, listX + LIST_W + 4, listY + listH + 4);
        for (int i = 0; i < rows.size(); i++) {
            int visible = i - scroll;
            if (visible < 0 || visible >= VISIBLE_ROWS) continue;
            renderRow(graphics, rows.get(i), rowY(visible), i == hovered);
        }
        graphics.disableScissor();

        if (scrollable()) {
            int barX = listX + LIST_W + 6;
            int barH = Math.max(12, listH * VISIBLE_ROWS / rows.size());
            int barY = listY + (listH - barH) * scroll / Math.max(1, maxScroll());
            graphics.fill(barX, listY, barX + SCROLLBAR_W, listY + listH, 0x40FFFFFF);
            graphics.fill(barX, barY, barX + SCROLLBAR_W, barY + barH, 0xC0FFFFFF);
        }

        // 提示行：按目标槽状态切换（空槽 = 说明黄框含义与怎么装；已占用 = 交换 + 撤下权限）
        boolean emptySlot = this.menu.featherStack(targetSlot).isEmpty();
        Component hint = Component.translatable(emptySlot
                ? "gui.lensouls.curse.hint_empty" : "gui.lensouls.curse.hint");
        graphics.drawString(this.font, hint, (this.width - this.font.width(hint)) / 2,
                listY + listH + 10, 0xAEB6C4, false);

        // 悬停 ⇒ 该款道具的完整 tooltip（常显 + Shift 两段）。必须画在 scissor 之外，否则被裁掉。
        if (hovered >= 0) {
            ItemStack stack = CurseDefs.stackOf(rows.get(hovered));
            if (!stack.isEmpty()) {
                graphics.renderTooltip(this.font, stack, mouseX, mouseY);
            }
        }
    }

    /**
     * 目标槽**为空**时、已经装在**别的槽**里的款：画黄框且点击无反应（防重复装同一款）。
     * 目标槽**已占用**时不设任何锁 —— 此时点别处已装的款 = **交换两个槽的内容**（用户口径）。
     */
    private boolean lockedRow(CurseDef def) {
        if (!this.menu.featherStack(targetSlot).isEmpty()) return false;
        net.minecraft.world.item.Item item = CurseDefs.itemOf(def);
        return item != null && this.menu.isInstalled(item);
    }

    private void renderRow(GuiGraphics graphics, CurseDef def, int y, boolean hovered) {
        boolean locked = lockedRow(def);
        if (hovered) {
            RoundedRect.roundedRect(graphics, listX, y, LIST_W, ROW_H - 2, 4.0F,
                    0xE61B2330, 0xE6151C27, 0x55FFFFFF, 1.0F);
        }
        // 空槽 + 已装在别处 ⇒ 黄框（点击无反应）
        if (locked) {
            RoundedRect.roundedOutline(graphics, listX, y, LIST_W, ROW_H - 2, 4.0F, 0xE6FFC24D, 1.5F);
        }

        ItemStack icon = CurseDefs.stackOf(def);
        int iconY = y + (ROW_H - 2 - ICON) / 2;
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, listX + PAD, iconY);
        }

        String name = Component.translatable("item.lensouls." + def.id()).getString();
        String motto = Component.translatable("item.lensouls." + def.id() + ".motto").getString();
        String status = Component.translatable("gui.lensouls.curse.status",
                CurseClientCache.reversedCount(def), def.entries()).getString();

        int textY = y + (ROW_H - 2 - 8) / 2;
        int textX = listX + PAD + ICON + PAD;
        graphics.drawString(this.font, name, textX, textY,
                locked ? 0x8A93A0 : 0xF2F6FF, false);
        graphics.drawString(this.font, motto, textX + this.font.width(name) + PAD, textY,
                0x8A93A0, false);
        int statusW = this.font.width(status);
        graphics.drawString(this.font, status, listX + LIST_W - PAD - statusW, textY,
                CurseClientCache.reversedCount(def) > 0 ? 0x7BE38A : 0xC7CCD6, false);
    }

    // ========== 交互 ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = rowAt(mouseX, mouseY);

        if (button == 1) {
            boolean creative = this.minecraft != null && this.minecraft.player != null
                    && this.minecraft.player.isCreative();
            if (!creative) {
                FeatherToast.show(Component.translatable("gui.lensouls.feather.creative_only"));
                return true;
            }
            PacketDistributor.sendToServer(FeatherSlotPacket.uninstall(targetSlot));
            back();
            return true;
        }

        if (button == 0) {
            if (index >= 0) {
                // 空槽 + 已装在别处 ⇒ 黄框、点击无反应（留在列表里）
                if (lockedRow(rows.get(index))) return true;
                PacketDistributor.sendToServer(
                        new CurseInstallPacket(targetSlot, rows.get(index).id()));
                back();
                return true;
            }
            if (!insideList(mouseX, mouseY)) {
                back();
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!scrollable()) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll - (int) Math.signum(scrollY)));
        return true;
    }

    private void back() {
        if (this.minecraft != null) {
            CursorKeeper.remember();
            this.minecraft.setScreen(this.parent);
            CursorKeeper.restore();
        }
    }

    /** E / ESC 回一级界面 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || keyCode == 69) {
            back();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        back();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 菜单若已在别处失效，本屏必须自己退场（否则停在一个「父屏无菜单」的状态） */
    @Override
    public void tick() {
        super.tick();
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (this.minecraft.player.containerMenu != this.menu) {
            this.minecraft.setScreen(null);
        }
    }
}
