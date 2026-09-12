package com.plumejade.lensouls.item;

import com.plumejade.lensouls.handler.PhantomRemnantData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 虚影残像（托管容器本体）。
 * <p>
 * 正常途径由 {@code PhantomRemnantEntity} 托在死亡地点、靠上去自动归还；本类主要承担
 * <b>兜底</b>：万一容器以物品形式落到手里（被指令给出、被容器搬运等），右键也能取出全部遗物。
 * 不提供合成与掉落来源。
 */
public class PhantomRemnantItem extends Item {

    public PhantomRemnantItem(Properties properties) {
        super(properties.stacksTo(1).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (PhantomRemnantData.restore(serverPlayer, stack)) {
                return InteractionResultHolder.success(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (PhantomRemnantData.hasPayload(stack)) {
            tooltip.add(Component.translatable("item.lensouls.phantom_remnant.stored")
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("item.lensouls.phantom_remnant.take")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("item.lensouls.phantom_remnant.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
