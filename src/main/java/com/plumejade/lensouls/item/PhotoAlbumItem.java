package com.plumejade.lensouls.item;

import com.plumejade.lensouls.gui.AlbumMenu;
import com.plumejade.lensouls.gui.ModMenus;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 饰品相册——9 格（3×3）照片容器饰品。
 * <p>
 * 右键打开发射器式 GUI，存入多张合法照片；佩戴时等效于其内照片全部装备（去重 + 边界安全）。
 */
public class PhotoAlbumItem extends Item {

    public PhotoAlbumItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    @NotNull
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.consume(stack);
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new AlbumMenu(id, inv, p.getItemInHand(hand)),
                Component.translatable("container.lensouls.photo_album")
        ));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.lensouls.photo_album.tooltip"));
    }
}
