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

    /** 次元强化界面（次元锤右键打开）：界面全部自绘，菜单负责玩家物品栏同步与权威结算 */
    public static final DeferredHolder<MenuType<?>, MenuType<ReinforceMenu>> REINFORCE =
            MENUS.register("reinforce", () -> IMenuTypeExtension.create(
                    (IContainerFactory<ReinforceMenu>) (id, inv, buf) -> new ReinforceMenu(id, inv)));

    /**
     * 羽毛装配界面（l2tabs 新分页点开后进入）：5 个羽毛槽 + 41 个隐藏玩家背包槽。
     * 界面全部自绘，菜单负责背包同步与「生存永久锁定 / 创造可卸下」的权威结算。
     */
    public static final DeferredHolder<MenuType<?>, MenuType<FeatherSlotMenu>> FEATHER =
            MENUS.register("feather_slots", () -> IMenuTypeExtension.create(
                    (IContainerFactory<FeatherSlotMenu>) (id, inv, buf) -> new FeatherSlotMenu(id, inv)));

    /**
     * 照片图鉴界面（{@code /lensouls photos}）：41 个隐藏玩家背包槽 + 2 条 ContainerData（规则/版本）。
     * 界面全部自绘；这些槽只为「服务端改背包 → 客户端可见」而存在（发放/清空/剔除都会改背包）。
     */
    public static final DeferredHolder<MenuType<?>, MenuType<PhotoGalleryMenu>> PHOTO_GALLERY =
            MENUS.register("photo_gallery", () -> IMenuTypeExtension.create(
                    (IContainerFactory<PhotoGalleryMenu>) (id, inv, buf) -> new PhotoGalleryMenu(id, inv)));

    /**
     * 在玩家背包中查找第一个转换器物品。
     */    public static ItemStack findConverter(Player player) {
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
