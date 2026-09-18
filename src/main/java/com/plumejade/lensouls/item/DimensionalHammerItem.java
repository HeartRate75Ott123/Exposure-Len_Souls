package com.plumejade.lensouls.item;

import com.plumejade.lensouls.gui.ReinforceMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 次元锤：手持右键打开次元强化界面（{@link ReinforceMenu}）。
 * <p>
 * 动态贴图（16×64 四帧）见 {@code textures/item/dimensional_hammer.png} + 同名 {@code .mcmeta}。
 */
public class DimensionalHammerItem extends Item {

    public DimensionalHammerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    @NotNull
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                  @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override
                @NotNull
                public Component getDisplayName() {
                    return Component.translatable("screen.lensouls.reinforce.title");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, @NotNull Inventory inventory, @NotNull Player p) {
                    return new ReinforceMenu(id, inventory);
                }
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lensouls.dimensional_hammer.tooltip")
                .withStyle(ChatFormatting.GREEN));
    }
}
