package com.plumejade.lensouls.item;

import net.minecraft.world.item.Item;

public class LensItem extends Item {
    private final int tier;

    public LensItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }
}
