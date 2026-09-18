package com.plumejade.lensouls.gui;

import com.plumejade.lensouls.reinforce.ReinforceDataLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * 选物界面（独立屏幕）：展示玩家物品栏，点击其中一格即选中它为强化目标，并返回主界面。
 * <p>
 * 单独打开而不是在主界面里叠一层：点击物品栏中任意非空物品 → 上报槽位 → 回到主界面。
 * 右键 / ESC / E / 点击空白处 = 直接返回，不改变当前选中。
 */
public class ReinforceSelectScreen extends Screen {

    private static final int SLOT = 20;       // 18px 格子 + 2px 间隔
    private static final int PAD_TOP = 16;
    private static final int PAGE_BG = 0xFF1E1E1E;
    private static final int CELL_BG = 0xD80F0F0F;
    private static final int CELL_HOVER = 0xE62A2A2A;
    private static final int BORDER_SELECTED = 0xFF55FF55;
    private static final int TEXT = 0xFFFDFEFD;
    private static final int TEXT_DIM = 0xFFB0B0B0;
    private static final int BLACKLIST_TINT = 0x88FF3030;

    private final Screen parent;
    private final ReinforceMenu menu;

    public ReinforceSelectScreen(Screen parent, ReinforceMenu menu) {
        super(Component.translatable("screen.lensouls.reinforce.select_title"));
        this.parent = parent;
        this.menu = menu;
    }

    // ========== 版式 ==========

    private int gridX() {
        return (this.width - SLOT * 9) / 2;
    }

    private int gridY() {
        return (this.height - SLOT * 5) / 2 + 4;
    }

    /** 物品栏槽位（0..40）在屏幕上的位置。 */
    private int[] cellPos(int inventoryIndex) {
        int x = gridX();
        int y = gridY();
        if (inventoryIndex >= 9 && inventoryIndex <= 35) {          // 主背包 3 行
            return new int[]{x + (inventoryIndex - 9) % 9 * SLOT, y + (inventoryIndex - 9) / 9 * SLOT};
        }
        if (inventoryIndex >= 0 && inventoryIndex <= 8) {            // 快捷栏
            return new int[]{x + inventoryIndex * SLOT, y + 3 * SLOT};
        }
        if (inventoryIndex >= 36 && inventoryIndex <= 40) {          // 护甲 4 + 副手
            return new int[]{x + (inventoryIndex - 36) * SLOT, y + 4 * SLOT + 8};
        }
        return null;
    }

    private int cellAt(double mouseX, double mouseY) {
        for (int i = 0; i < ReinforceMenu.PLAYER_SLOTS; i++) {
            int[] pos = cellPos(i);
            if (pos == null) continue;
            if (mouseX >= pos[0] && mouseX < pos[0] + SLOT && mouseY >= pos[1] && mouseY < pos[1] + SLOT) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack stackAt(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= this.menu.slots.size()) return ItemStack.EMPTY;
        return this.menu.slots.get(inventoryIndex).getItem();
    }

    // ========== 渲染 ==========

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 与主界面同一张全屏背景（银白液态玻璃枝条）；加载失败时回退纯色
        if (!com.plumejade.lensouls.client.render.ReinforceBackground.render(
                graphics, this.minecraft, partialTick, this.width, this.height)) {
            renderTransparentBackground(graphics);
            graphics.fill(0, 0, this.width, this.height, PAGE_BG);
        }

        graphics.drawCenteredString(this.font, Component.translatable("screen.lensouls.reinforce.select_title"),
                this.width / 2, PAD_TOP, TEXT);
        graphics.drawCenteredString(this.font, Component.translatable("screen.lensouls.reinforce.select_hint"),
                this.width / 2, PAD_TOP + 12, TEXT_DIM);

        int selected = this.menu.getSelectedSlot();
        int hovered = cellAt(mouseX, mouseY);

        for (int i = 0; i < ReinforceMenu.PLAYER_SLOTS; i++) {
            int[] pos = cellPos(i);
            if (pos == null) continue;
            int x = pos[0];
            int y = pos[1];
            boolean isHovered = i == hovered;
            graphics.fill(x, y, x + SLOT, y + SLOT, isHovered ? CELL_HOVER : CELL_BG);
            if (i == selected) {
                outline(graphics, x, y, SLOT, BORDER_SELECTED);
            } else if (isHovered) {
                outline(graphics, x, y, SLOT, 0xFF6A6A6A);
            }

            ItemStack stack = stackAt(i);
            if (stack.isEmpty()) continue;
            graphics.renderItem(stack, x + 2, y + 2);
            graphics.renderItemDecorations(this.font, stack, x + 2, y + 2);
            if (ReinforceDataLoader.isBlacklisted(stack)) {
                graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, BLACKLIST_TINT);
            }
        }

        // 分隔：护甲/副手行与主背包之间
        int[] firstArmor = cellPos(36);
        if (firstArmor != null) {
            graphics.fill(gridX(), firstArmor[1] - 5, gridX() + SLOT * 9, firstArmor[1] - 4, 0xFF3C3C3C);
        }

        ItemStack hoveredStack = hovered >= 0 ? stackAt(hovered) : ItemStack.EMPTY;
        if (!hoveredStack.isEmpty()) {
            graphics.renderTooltip(this.font, hoveredStack, mouseX, mouseY);
        } else if (hovered >= 0) {
            graphics.renderTooltip(this.font,
                    Component.translatable("screen.lensouls.reinforce.empty_slot"), mouseX, mouseY);
        }
    }

    private void outline(GuiGraphics graphics, int x, int y, int size, int color) {
        graphics.fill(x, y, x + size, y + 1, color);
        graphics.fill(x, y + size - 1, x + size, y + size, color);
        graphics.fill(x, y, x + 1, y + size, color);
        graphics.fill(x + size - 1, y, x + size, y + size, color);
    }

    // ========== 交互 ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = cellAt(mouseX, mouseY);
        if (button == 0 && slot >= 0) {
            ItemStack stack = stackAt(slot);
            if (!stack.isEmpty()) {
                PacketDistributor.sendToServer(new com.plumejade.lensouls.network.ReinforceSelectPacket(slot));
            }
        }
        back();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || keyCode == 69) {   // ESC / E
            back();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 本界面依附于主界面的菜单：任何"关闭"都回到主界面，绝不能让屏幕变成 null（菜单还开着会卡死交互）。 */
    @Override
    public void onClose() {
        back();
    }

    /** 菜单在别处被关掉（死亡 / 服务端关闭）时，退出本界面而不是留在空屏。 */
    @Override
    public void tick() {
        super.tick();
        if (this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.containerMenu != this.menu) {
            this.minecraft.setScreen(null);
        }
    }

    private void back() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
