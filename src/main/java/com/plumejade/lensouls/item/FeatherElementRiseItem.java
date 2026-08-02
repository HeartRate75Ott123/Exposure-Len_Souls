package com.plumejade.lensouls.item;

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
 * 羽·元素觉醒者：Curios 任意槽位饰品。
 * <p>
 * 绑定机制（参考绑定魔咒）：
 * <ul>
 *   <li>右键可直接佩戴（{@code canEquipFromUse}）</li>
 *   <li>戴上后无法取下（{@code canUnequip} 恒 false）</li>
 *   <li>无法主动丢弃（{@code onDroppedByPlayer} 恒 false）</li>
 *   <li>死亡掉落赦免（{@code getDropRule} = ALWAYS_KEEP，keepInventory 为 false 时也不掉落）</li>
 * </ul>
 * 战斗/药水效果由 {@link com.plumejade.lensouls.handler.FeatherElementRiseHandler} 实现。
 */
public class FeatherElementRiseItem extends Item implements ICurioItem {

    public FeatherElementRiseItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    /** 右键佩戴 */
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
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

    /** 死亡赦免：无论 keepInventory 游戏规则如何都不掉落（Curios 运行时调用 3 参版本） */
    public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source, boolean recentlyHit) {
        return ICurio.DropRule.ALWAYS_KEEP;
    }

    /** 绑定：禁止主动丢弃（Q 键/丢出） */
    @Override
    public boolean onDroppedByPlayer(ItemStack stack, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lensouls.feather_elementrise.desc1"));
        tooltip.add(Component.translatable("item.lensouls.feather_elementrise.desc2"));
        tooltip.add(Component.translatable("item.lensouls.feather_elementrise.desc3"));
    }
}
