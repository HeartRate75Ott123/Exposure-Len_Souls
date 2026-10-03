package com.plumejade.lensouls.client.feather;

import com.plumejade.lensouls.feather.FeatherSlotLoader;
import com.plumejade.lensouls.gui.FeatherSlotMenu;
import com.plumejade.lensouls.network.FeatherSlotPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * 羽毛选择屏：从玩家 41 格物品栏里挑一个可放入的物品装进指定槽位。
 * <p>
 * 结构照 {@code ReinforceSelectScreen}（独立 {@code Screen}，**不用 MenuAccess**）：父屏仍是
 * 羽毛界面，本屏只做"选一格"。
 * <p>
 * <b>读数口径</b>：直接 {@code player.getInventory().getItem(i)}，<b>不按 {@code menu.slots} 下标读</b>
 * ——羽毛菜单的 slot 顺序是「0..4 羽毛槽 + 5..45 隐藏背包槽」，按 menu.slots 读会整体错位 5 格。
 * 这与 {@code ReinforceMenu#getSelectedStack} 是同口径（也保证了与选择时同一 tick 的一致性）。
 * <p>
 * <b>红覆盖</b>：{@link FeatherSlotLoader#isSupported} 为假的物品盖红色蒙版且点击无效
 * （支持列表由数据包驱动、随 {@code DatapackSyncPacket} 下发到客户端）。
 */
public class FeatherSelectScreen extends Screen {

    /** 格子边长（18px 图标 + 2px 间隔） */
    private static final int CELL = 20;
    /**
     * 文字与物品栏的行距：标题、提示、物品栏顶边三者间距**一律相等**（用户口径）。
     * <p>提示原来只离物品栏 10px（比与标题的 12px 更近，显得贴住物品栏），现在统一为 12。
     */
    private static final int LINE_GAP = 12;
    /** 不可放入的红色覆盖 */
    private static final int RED_TINT = 0x99FF3B30;

    private final Screen parent;
    private final FeatherSlotMenu menu;
    private final int targetSlot;

    public FeatherSelectScreen(Screen parent, FeatherSlotMenu menu, int targetSlot) {
        super(Component.translatable("gui.lensouls.feather.select_title"));
        this.parent = parent;
        this.menu = menu;
        this.targetSlot = targetSlot;
    }

    // ========== 布局（与强化选物屏同款公式，不用 imageWidth） ==========

    private int gridX() {
        return (this.width - CELL * 9) / 2;
    }

    private int gridY() {
        return (this.height - CELL * 5) / 2 + 4;
    }

    private int[] cellPos(int inventoryIndex) {
        int x = gridX();
        int y = gridY();
        if (inventoryIndex >= 9 && inventoryIndex <= 35) {
            return new int[]{x + (inventoryIndex - 9) % 9 * CELL, y + (inventoryIndex - 9) / 9 * CELL};
        }
        if (inventoryIndex >= 0 && inventoryIndex <= 8) {
            return new int[]{x + inventoryIndex * CELL, y + 3 * CELL};
        }
        if (inventoryIndex >= 36 && inventoryIndex <= 40) {
            return new int[]{x + (inventoryIndex - 36) * CELL, y + 4 * CELL + 8};
        }
        return null;
    }

    private int cellAt(double mouseX, double mouseY) {
        for (int i = 0; i < FeatherSlotMenu.PLAYER_SLOTS; i++) {
            int[] pos = cellPos(i);
            if (pos == null) continue;
            if (mouseX >= pos[0] && mouseX < pos[0] + 18 && mouseY >= pos[1] && mouseY < pos[1] + 18) {
                return i;
            }
        }
        return -1;
    }

    /** 直接读玩家物品栏（服务端与客户端各自读自己的那份，容器槽负责同步） */
    private ItemStack stackAt(int inventoryIndex) {
        var player = this.minecraft != null ? this.minecraft.player : null;
        if (player == null || inventoryIndex < 0 || inventoryIndex >= FeatherSlotMenu.PLAYER_SLOTS) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(inventoryIndex);
    }

    /** 关闭按钮：与物品栏网格右缘对齐、放在网格上方（标题行右侧是空的） */
    private int closeX() {
        return CloseButton.x(gridX() + 9 * CELL);
    }

    private int closeY() {
        // 让按钮上沿与标题行对齐（写 -6 是补掉 CloseButton 自己的 MARGIN）
        return CloseButton.y(gridY() - LINE_GAP * 2 - 6);
    }

    // ========== 渲染 ==========

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, 0xC0080C12);

        Component title = Component.translatable("gui.lensouls.feather.select_title");
        Component hint = Component.translatable("gui.lensouls.feather.select_hint");
        // 标题 / 提示 / 物品栏顶边：三者间距一律 LINE_GAP
        int titleY = gridY() - LINE_GAP * 2;
        int hintY = gridY() - LINE_GAP;
        graphics.drawString(this.font, title, (this.width - this.font.width(title)) / 2,
                titleY, 0xF2F6FF, true);
        graphics.drawString(this.font, hint, (this.width - this.font.width(hint)) / 2,
                hintY, 0xAEB6C4, false);

        int hovered = cellAt(mouseX, mouseY);
        for (int i = 0; i < FeatherSlotMenu.PLAYER_SLOTS; i++) {
            int[] pos = cellPos(i);
            if (pos == null) continue;
            int x = pos[0];
            int y = pos[1];
            ItemStack stack = stackAt(i);
            boolean supported = FeatherSlotLoader.isSupported(stack);

            RoundedRect.roundedRect(graphics, x, y, 18, 18, 4.0F,
                    0x40FFFFFF, 0x30FFFFFF, 0x22FFFFFF, 1.0F);
            // 悬停高亮只给「可放入」的格子：不可放入的只保留红覆盖，不额外渲染（用户口径）
            if (hovered == i && supported) {
                RoundedRect.roundedOutline(graphics, x, y, 18, 18, 4.0F, 0x88FFFFFF, 1.0F);
            }
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 1);
                graphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
            }
            if (!supported) {
                // 不支持投入 ⇒ 红色覆盖，点击也无效（需求口径）。
                // ★ 必须与格子同形（同尺寸同圆角）：直角 fill 会在圆角处露出缺口，看着不贴合。
                RoundedRect.roundedRect(graphics, x, y, 18, 18, 4.0F,
                        RED_TINT, RED_TINT, 0, 0.0F);
            }
        }

        // 悬停在「不可放入」的物品上时**不给任何信息提示**（红覆盖本身就是提示，用户口径）；
        // 可放入的照常显示物品 tooltip，空格子提示「空槽位」。
        if (hovered >= 0) {
            ItemStack stack = stackAt(hovered);
            if (FeatherSlotLoader.isSupported(stack)) {
                graphics.renderTooltip(this.font, stack, mouseX, mouseY);
            } else if (stack.isEmpty()) {
                graphics.renderTooltip(this.font,
                        Component.translatable("gui.lensouls.feather.empty"), mouseX, mouseY);
            }
        }

        // 关闭按钮（点它回一级菜单）
        CloseButton.render(graphics, closeX(), closeY(), mouseX, mouseY);
    }

    // ========== 交互 ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // 关闭按钮优先：回一级菜单
        if (CloseButton.isOver(mouseX, mouseY, closeX(), closeY())) {
            back();
            return true;
        }

        int index = cellAt(mouseX, mouseY);
        if (index >= 0) {
            ItemStack stack = stackAt(index);
            if (!stack.isEmpty() && FeatherSlotLoader.isSupported(stack)) {
                PacketDistributor.sendToServer(FeatherSlotPacket.install(targetSlot, index));
                back();   // 只有成功放入才回上级
            }
            // 空槽 / 不可放入：**停在本屏**，不做任何更改、也不关闭（用户口径）
            return true;
        }

        // 点击物品栏区域之外 ⇒ 自动返回上级菜单（用户口径）
        if (!insideGrid(mouseX, mouseY)) {
            back();
        }
        return true;
    }

    /**
     * 物品栏网格的包围盒（含快捷栏与护甲/副手那一行）。
     * <p>
     * 格子之间的缝隙算「区域内」——点缝隙不应把玩家踢回上级，只有点到网格以外才算。
     */
    private boolean insideGrid(double mouseX, double mouseY) {
        int x0 = gridX();
        int y0 = gridY();
        int x1 = x0 + 9 * CELL;
        int y1 = y0 + 4 * CELL + 8 + 18;
        return mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
    }

    private void back() {
        if (this.minecraft != null) {
            CursorKeeper.remember();
            this.minecraft.setScreen(this.parent);
            CursorKeeper.restore();
        }
    }

    /** E / ESC 回一级菜单 */
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

    /**
     * 菜单若已在别处失效（服务端关掉了容器、玩家被踢等），本屏必须自己退场——
     * 否则会停在一个「父屏无菜单」的状态上，后续点击全部落空（参考项目同款护栏）。
     */
    @Override
    public void tick() {
        super.tick();
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (this.minecraft.player.containerMenu != this.menu) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
