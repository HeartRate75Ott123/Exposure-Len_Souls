package com.plumejade.lensouls.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.network.ConverterMenuSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 精准触发：长按激活键呼出的镜魂选择菜单。
 * <p>
 * 完全复刻转换器手持右键 GUI（dispenser.png 全图，含物品栏），悬停白亮对齐原版。
 * 3×3 镜魂区显示服务端同步的真实内容（冷却灰显）；鼠标移到非冷却镜魂上松开激活键即触发。
 */
public class SoulSelectOverlay extends Screen {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "textures/gui/lensoul_choose.png");

    private static final int IMAGE_W = 176;
    private static final int IMAGE_H = 166;
    private static final int SLOT = 18;
    // 3×3 镜魂（对齐 ConverterMenu）
    private static final int SOUL_X = 62, SOUL_Y = 17;

    private static SoulSelectOverlay active;
    private static List<ConverterMenuSyncPacket.SoulEntry> souls = List.of();
    private static int hoveredSlot = -1;       // 任意槽（白亮）
    private static int hoveredSoulSlot = -1;   // 镜魂可激活槽

    private int left, top;

    public SoulSelectOverlay() {
        super(Component.empty());
        active = this;
    }

    @Override public boolean isPauseScreen() { return false; }

    // ========== 静态控制 ==========

    public static boolean isOpen() { return active != null; }

    /** 打开不依赖数据——先显示空面板，SyncPacket 到达后填充，避免长按等待闪烁 */
    public static void open(Minecraft mc) {
        if (active == null) mc.setScreen(new SoulSelectOverlay());
    }

    public static void close(Minecraft mc) {
        if (active != null) {
            mc.setScreen(null);
            active = null;
        }
    }

    public static void setSouls(List<ConverterMenuSyncPacket.SoulEntry> list) {
        souls = list == null ? List.of() : list;
        if (active != null) {
            Minecraft mc = Minecraft.getInstance();
            active.computeHover((int) mc.mouseHandler.xpos(), (int) mc.mouseHandler.ypos());
        }
    }

    public static boolean hasHovered() { return hoveredSoulSlot >= 0; }
    public static int getHoveredSlot() { return hoveredSoulSlot; }

    // ========== 槽位几何 ==========

    private void soulCell(int slot, int[] out) {
        out[0] = left + SOUL_X + (slot % 3) * SLOT;
        out[1] = top + SOUL_Y + (slot / 3) * SLOT;
    }

    // ========== 渲染 ==========

    @Override
    protected void init() {
        left = (this.width - IMAGE_W) / 2;
        top = (this.height - IMAGE_H) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x99000000);
        RenderSystem.enableBlend();
        guiGraphics.blit(TEXTURE, left, top, 0, 0, IMAGE_W, IMAGE_H, 256, 256);
        RenderSystem.disableBlend();

        Map<Integer, ConverterMenuSyncPacket.SoulEntry> bySlot = new HashMap<>();
        for (ConverterMenuSyncPacket.SoulEntry e : souls) bySlot.put(e.slot(), e);

        // 3×3 镜魂（仿右键 GUI 正常渲染物品 + 数量/耐久条装饰）
        for (int slot = 0; slot < 9; slot++) {
            int[] cell = new int[2];
            soulCell(slot, cell);
            ConverterMenuSyncPacket.SoulEntry entry = bySlot.get(slot);
            if (entry != null && !entry.stack().isEmpty()) {
                guiGraphics.renderItem(entry.stack(), cell[0], cell[1]);
                guiGraphics.renderItemDecorations(this.font, entry.stack(), cell[0], cell[1]);
            }
        }

        // 悬停白亮（仅 3×3 镜魂）
        if (hoveredSlot >= 0) {
            int[] cell = new int[2];
            soulCell(hoveredSlot, cell);
            guiGraphics.fill(cell[0], cell[1], cell[0] + 16, cell[1] + 16, 0x80FFFFFF);
        }

        guiGraphics.drawCenteredString(this.font, "释放以激活悬停镜魂，Esc 取消",
                this.width / 2, top + IMAGE_H + 6, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true; // 通过松开激活键触发，鼠标点击不激活（防止穿透）
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        computeHover((int) mouseX, (int) mouseY);
    }

    /** 计算悬停槽：仅 3×3 镜魂；非冷却镜魂为可激活 */
    private void computeHover(int mouseX, int mouseY) {
        hoveredSlot = -1;
        hoveredSoulSlot = -1;

        int[] cell = new int[2];
        for (int slot = 0; slot < 9; slot++) {
            soulCell(slot, cell);
            if (inCell(mouseX, mouseY, cell)) {
                hoveredSlot = slot;
                for (ConverterMenuSyncPacket.SoulEntry e : souls) {
                    if (e.slot() == slot && !e.stack().isEmpty() && e.remainingTicks() == 0) {
                        hoveredSoulSlot = slot;
                        return;
                    }
                }
                return;
            }
        }
    }

    private boolean inCell(int mx, int my, int[] cell) {
        return mx >= cell[0] - 2 && mx < cell[0] + SLOT - 2
                && my >= cell[1] - 2 && my < cell[1] + SLOT - 2;
    }

    @Override
    public void onClose() {
        if (active == this) active = null;
        super.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
