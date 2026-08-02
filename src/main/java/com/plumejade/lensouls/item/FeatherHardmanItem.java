package com.plumejade.lensouls.item;

import com.plumejade.lensouls.handler.FeatherElementRiseHandler;
import com.plumejade.lensouls.handler.FeatherHardmanHandler;
import com.plumejade.lensouls.handler.FeatherTwitcherHandler;
import net.minecraft.ChatFormatting;
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
 * 羽·荒厄遗咒：Curios 任意槽位饰品。
 * <p>
 * 绑定机制（与元素/扭曲羽毛一致）：
 * 右键直接佩戴、戴上无法取下（创造除外）、不可丢弃、死亡不掉落（ALWAYS_KEEP）。
 * 三根羽毛互斥，只能同时佩戴一根（{@code canEquip} 三方互查）。
 * 战斗/惩罚效果由 {@link com.plumejade.lensouls.handler.FeatherHardmanHandler} 实现。
 */
public class FeatherHardmanItem extends Item implements ICurioItem {

    public FeatherHardmanItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    /** 右键佩戴 */
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    /** 互斥：已佩戴其他羽毛（元素/扭曲/荒厄）则无法佩戴 */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            return !FeatherElementRiseHandler.hasFeather(player)
                    && !FeatherTwitcherHandler.hasTwitcher(player)
                    && !FeatherHardmanHandler.hasHardman(player);
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

    /** 栏位提示：统一显示"任意饰品栏" */
    @Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, TooltipContext context, ItemStack stack) {
        return List.of(Component.translatable("item.lensouls.curio_any_slot")
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lensouls.feather_hardman.desc1"));
        tooltip.add(Component.translatable("item.lensouls.feather_hardman.desc2"));
        tooltip.add(Component.translatable("item.lensouls.feather_hardman.desc3"));
        tooltip.add(Component.translatable("item.lensouls.feather_hardman.desc4"));
    }
}
