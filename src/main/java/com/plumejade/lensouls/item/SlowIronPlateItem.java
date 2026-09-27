package com.plumejade.lensouls.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 减速铁板：纯粹的负担型物品——只要躺在玩家物品栏里就压慢移动速度。
 * <p>
 * 数值统计与属性修饰符由 {@link com.plumejade.lensouls.handler.SlowIronPlateHandler} 在服务端
 * 每 10 tick 扫一次玩家物品栏（41 格）完成；<b>不</b>扫描饰品栏、精妙背包内容物与超越维度终端，
 * 因此把铁板塞进背包/终端就不再拖慢速度。这里只负责 tooltip。
 */
public class SlowIronPlateItem extends Item {

    public SlowIronPlateItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lensouls.slow_iron_plate.desc1"));
        tooltip.add(Component.translatable("item.lensouls.slow_iron_plate.desc2"));
        tooltip.add(Component.translatable("item.lensouls.slow_iron_plate.desc3"));
    }
}
