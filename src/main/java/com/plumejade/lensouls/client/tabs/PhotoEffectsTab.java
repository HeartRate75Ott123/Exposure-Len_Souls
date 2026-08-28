package com.plumejade.lensouls.client.tabs;

import com.plumejade.lensouls.item.ModItems;
import dev.xkmc.l2tabs.tabs.core.TabBase;
import dev.xkmc.l2tabs.tabs.core.TabManager;
import dev.xkmc.l2tabs.tabs.core.TabToken;
import dev.xkmc.l2tabs.tabs.inventory.InvTabData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class PhotoEffectsTab extends TabBase<InvTabData, PhotoEffectsTab> {

    public PhotoEffectsTab(int index, TabToken<InvTabData, PhotoEffectsTab> token,
                           TabManager<InvTabData> manager, Component title) {
        super(index, token, manager, title);
    }

    @Override
    public void onTabClicked() {
        Minecraft.getInstance().setScreen(new PhotoEffectsScreen(getMessage()));
    }

    @Override
    protected void renderIcon(GuiGraphics g) {
        ItemStack stack = new ItemStack(ModItems.PHOTO_ALBUM.get());
        token.getType().drawIcon(g, getX(), getY(), stack);
    }
}
