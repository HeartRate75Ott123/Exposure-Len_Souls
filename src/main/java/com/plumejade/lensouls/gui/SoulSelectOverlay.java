package com.plumejade.lensouls.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.component.SoulCooldownData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 精准触发：长按激活键呼出的镜魂选择菜单。
 * <p>
 * 渲染逻辑完全仿照已验证的 {@link ConverterScreen}（原版 AbstractContainerScreen：
 * 物品图标/数量/耐久条/附魔光效/悬停白亮），仅剔除玩家物品栏 36 格的渲染与悬停判定，
 * 只保留上方 3×3 镜魂槽。容器为真实 {@link ConverterSelectMenu}，与右键 GUI 完全同步。
 */
public class SoulSelectOverlay extends AbstractContainerScreen<ConverterSelectMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "textures/gui/lensoul_choose.png");

    private Slot currentHover;

    public SoulSelectOverlay(ConverterSelectMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, Component.empty());
        this.imageWidth = 176;
        this.imageHeight = 89; // 仅 3×3 镜魂区，剔除物品栏
    }

    @Override public boolean isPauseScreen() { return false; }

    /** 悬停镜魂槽（0-8），未悬停返回 -1 */
    public int getHoveredSlot() {
        return currentHover != null ? currentHover.index : -1;
    }

    /** 指定镜魂槽是否可激活（非空且未冷却） */
    public boolean isSlotReady(int slot) {
        if (slot < 0 || slot >= this.menu.slots.size()) return false;
        ItemStack stack = this.menu.getSlot(slot).getItem();
        if (stack.isEmpty()) return false;
        SoulCooldownData cd = stack.get(ModDataComponents.SOUL_COOLDOWN.get());
        if (cd == null) return true;
        return cd.endTime() <= this.minecraft.level.getGameTime();
    }

    // ========== 渲染 ==========

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x99000000);
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBg(guiGraphics, partialTick, mouseX, mouseY);

        // 镜魂槽：原版 renderSlot（图标+数量+耐久+附魔），跳过物品栏槽
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 0.0F);
        RenderSystem.enableDepthTest();
        for (Slot slot : this.menu.slots) {
            if (slot.index < 9) {
                this.renderSlot(guiGraphics, slot);
            }
        }
        RenderSystem.disableDepthTest();
        guiGraphics.pose().popPose();

        // 悬停判定：仅 3×3 镜魂；白亮用原版 renderSlotHighlight
        Slot hover = null;
        for (Slot slot : this.menu.slots) {
            if (slot.index < 9 && this.isMouseOverSlot(slot, mouseX, mouseY)) {
                hover = slot;
                break;
            }
        }
        this.currentHover = hover;
        if (hover != null) {
            this.renderSlotHighlight(guiGraphics, this.leftPos + hover.x, this.topPos + hover.y, 0);
        }

        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.lensouls.soul_select_hint"),
                this.width / 2, this.topPos + this.imageHeight + 6, 0xFFFFFF);
    }

    /** 原版 findSlot 同款坐标判定 */
    private boolean isMouseOverSlot(Slot slot, double mouseX, double mouseY) {
        return mouseX >= this.leftPos + slot.x && mouseX < this.leftPos + slot.x + 16
                && mouseY >= this.topPos + slot.y && mouseY < this.topPos + slot.y + 16;
    }

    // ========== 交互 ==========

    /** 禁用鼠标点击（防止容器物品操作），通过松开激活键触发 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }
}
