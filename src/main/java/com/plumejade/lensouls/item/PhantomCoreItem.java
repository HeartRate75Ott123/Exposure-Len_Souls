package com.plumejade.lensouls.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 虚影核心：Curios 任意槽位饰品。
 * <p>
 * 佩戴（或在背包中）时死亡，会在死亡地点留下一个<b>虚影残像</b>，把玩家身上的全部物品
 * （含饰品栏）与全部经验缓存进去；靠近拾取即按原槽位自动归还。
 * <p>
 * 开启「死亡不掉落」时<b>不生效</b>（那时本来就不会掉落，没必要再生成容器）。
 * 实现见 {@link com.plumejade.lensouls.handler.PhantomRemnantHandler}。
 */
public class PhantomCoreItem extends Item implements ICurioItem {

    public PhantomCoreItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    /**
     * 死亡不掉落：无论 keepInventory 规则如何，佩戴时死亡都不会掉落/失去（ALWAYS_KEEP）。
     * <p>
     * 注意：必须覆写 5 参版本——{@code ICurioItem} 的默认转发链经过 {@code defaultInstance}
     * （接口默认实例，恒返回 DEFAULT），覆写 3/4 参版本不会被调用。
     * <p>
     * 与之配套：{@code PhantomRemnantData.capture} 会<b>跳过饰品栏里的虚影核心</b>，
     * 不把它搬进容器、也不清空它所在的槽位——否则本规则会被打包逻辑抵消。
     */
    @Override
    public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source,
                                       int lootingLevel, boolean recentlyHit, ItemStack stack) {
        return ICurio.DropRule.ALWAYS_KEEP;
    }

    /** 栏位提示：统一显示「任意饰品栏」 */
    @Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, TooltipContext context, ItemStack stack) {
        return List.of(Component.translatable("item.lensouls.curio_any_slot")
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lensouls.phantom_core.desc1"));
        tooltip.add(Component.translatable("item.lensouls.phantom_core.desc2"));
        tooltip.add(Component.translatable("item.lensouls.phantom_core.desc3"));
    }
}
