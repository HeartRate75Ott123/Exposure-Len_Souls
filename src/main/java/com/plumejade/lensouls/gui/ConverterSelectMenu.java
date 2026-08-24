package com.plumejade.lensouls.gui;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 转换器精准选择菜单（长按呼出）：复用 ConverterMenu 逻辑，独立 MenuType 以绑定精简界面。
 */
public class ConverterSelectMenu extends ConverterMenu {

    public ConverterSelectMenu(int id, Inventory playerInventory, ItemStack converterStack) {
        super(ModMenus.CONVERTER_SELECT.get(), id, playerInventory, converterStack);
    }
}
