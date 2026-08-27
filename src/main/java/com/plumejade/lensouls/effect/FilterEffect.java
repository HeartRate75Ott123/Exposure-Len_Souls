package com.plumejade.lensouls.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 滤镜效果基类（标记型，无图标、无粒子）。
 * 通过构造传入的 name 定制翻译键 {@code effect.lensouls.<name>}，与模组既有效果命名一致。
 */
public class FilterEffect extends MobEffect {

    private final String name;

    public FilterEffect(MobEffectCategory category, int color, String name) {
        super(category, color);
        this.name = name;
    }

    @Override
    public String getDescriptionId() {
        return "effect.lensouls." + name;
    }
}
