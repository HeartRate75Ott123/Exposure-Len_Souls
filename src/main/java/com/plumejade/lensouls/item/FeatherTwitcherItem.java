package com.plumejade.lensouls.item;

import com.plumejade.lensouls.handler.FeatherElementRiseHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 羽·扭曲之人：Curios 任意槽位饰品。
 * <p>
 * 与羽·元素觉醒者互斥（不能同时佩戴）。绑定机制参考元素羽毛：
 * 右键直接佩戴、戴上无法取下（创造除外）、不可丢弃、死亡不掉落（ALWAYS_KEEP）。
 * 扭曲值机制见 {@link com.plumejade.lensouls.handler.FeatherTwitcherHandler}。
 */
public class FeatherTwitcherItem extends Item implements ICurioItem {

    public FeatherTwitcherItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    /** 右键佩戴 */
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    /** 互斥：已佩戴羽·元素觉醒者则无法佩戴 */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return !FeatherElementRiseHandler.hasFeather(player);
        }
        return true;
    }

    /** 绑定：无法从饰品槽取下，创造模式除外 */
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return player.getAbilities().instabuild;
        }
        return false;
    }

    /**
     * 死亡赦免：无论 keepInventory 游戏规则如何都不掉落（ALWAYS_KEEP）。
     * 注意：必须覆写 5 参版本——ICurioItem 的默认转发链经过 defaultInstance
     * （接口默认实例，恒返回 DEFAULT），覆写 3/4 参版本不会被调用。
     */
    @Override
    public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source,
                                       int lootingLevel, boolean recentlyHit, ItemStack stack) {
        return ICurio.DropRule.ALWAYS_KEEP;
    }

    /** 绑定：禁止主动丢弃（Q 键/丢出） */
    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lensouls.feather_twitcher.desc1"));
        tooltip.add(Component.translatable("item.lensouls.feather_twitcher.desc2"));
        tooltip.add(Component.translatable("item.lensouls.feather_twitcher.desc3"));
        tooltip.add(Component.translatable("item.lensouls.feather_twitcher.desc4"));
        tooltip.add(Component.translatable("item.lensouls.feather_twitcher.desc5"));
    }
}
