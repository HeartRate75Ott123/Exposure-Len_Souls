package com.plumejade.lensouls.gui;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.item.ConverterItem;
import com.plumejade.lensouls.item.PhotoAlbumItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组容器菜单类型注册表。
 */
public class ModMenus {

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, LenSouls.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ConverterMenu>> CONVERTER =
            MENUS.register("converter", () -> IMenuTypeExtension.create((IContainerFactory<ConverterMenu>) (id, inv, buf) -> {
                ItemStack stack = findConverter(inv.player);
                return new ConverterMenu(id, inv, stack);
            }));

    public static final DeferredHolder<MenuType<?>, MenuType<ConverterSelectMenu>> CONVERTER_SELECT =
            MENUS.register("converter_select", () -> IMenuTypeExtension.create(
                    (IContainerFactory<ConverterSelectMenu>) (id, inv, buf) -> new ConverterSelectMenu(id, inv, findConverter(inv.player))));

    public static final DeferredHolder<MenuType<?>, MenuType<PhotoGuiMenu>> PHOTO_GUI =
            MENUS.register("photo_gui", () -> IMenuTypeExtension.create(
                    (IContainerFactory<PhotoGuiMenu>) (id, inv, buf) -> new PhotoGuiMenu(id, inv)));

    public static final DeferredHolder<MenuType<?>, MenuType<AlbumMenu>> PHOTO_ALBUM =
            MENUS.register("photo_album", () -> IMenuTypeExtension.create(
                    (IContainerFactory<AlbumMenu>) (id, inv, buf) -> new AlbumMenu(id, inv, findAlbum(inv.player))));

    /**
     * 在玩家背包中查找第一个转换器物品。
     */
    public static ItemStack findConverter(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof ConverterItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 在玩家背包（主槽 + 副手）中查找第一个相册物品。 */
    public static ItemStack findAlbum(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof PhotoAlbumItem) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof PhotoAlbumItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
