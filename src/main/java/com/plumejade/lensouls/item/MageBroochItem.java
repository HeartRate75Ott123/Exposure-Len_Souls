package com.plumejade.lensouls.item;

import com.plumejade.lensouls.handler.WhistlePhantomHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 法师胸针：Curios 任意槽位饰品。
 * <p>
 * 佩戴时自身造成伤害全部魔法化且 -50%；照片弹幕豁免减伤并在命中时追加 200% 魔法伤害；
 * 手持法杖（数据驱动 staff_item）时照片弹幕必定触发。
 * 与灵魂口哨互斥，不可同戴。效果逻辑见 {@link com.plumejade.lensouls.handler.BroochEffectHandler}。
 */
public class MageBroochItem extends Item implements ICurioItem {

    public MageBroochItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    /** 互斥：已佩戴灵魂口哨则无法佩戴胸针 */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return !WhistlePhantomHandler.hasWhistle(player);
        }
        return true;
    }

    @Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, TooltipContext context, ItemStack stack) {
        return List.of(Component.translatable("item.lensouls.curio_any_slot")
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lensouls.mage_brooch.desc1"));
        tooltip.add(Component.translatable("item.lensouls.mage_brooch.desc2"));
        tooltip.add(Component.translatable("item.lensouls.mage_brooch.desc3"));
        tooltip.add(Component.translatable("item.lensouls.mage_brooch.desc4"));
    }
}
