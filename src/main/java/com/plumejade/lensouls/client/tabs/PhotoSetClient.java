package com.plumejade.lensouls.client.tabs;

import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import com.plumejade.lensouls.item.PhotoAlbumItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 客户端版「装备照片收集器」：只读 Curios 照片栏（含相册展开），不依赖服务端逻辑。
 * 逻辑镜像 {@link com.plumejade.lensouls.integration.PhotoSpecialEffects}，但接收 Player 而非 ServerPlayer。
 */
public class PhotoSetClient {

    public static List<String> collectGearEntities(Player player) {
        List<String> ids = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                IDynamicStackHandler stackHandler = stacksHandler.getStacks();
                for (int i = 0; i < stackHandler.getSlots(); i++) {
                    ItemStack stack = stackHandler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    if (stack.getItem() instanceof PhotoAlbumItem) {
                        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                        for (ItemStack photo : contents.nonEmptyItems()) {
                            String stolen = PhotographEffectRegistry.getStolenEntity(photo);
                            if (stolen == null) stolen = PhotographEffectRegistry.getElementEntity(photo);
                            if (stolen != null && !ids.contains(stolen)) ids.add(stolen);
                        }
                        continue;
                    }
                    String stolen = PhotographEffectRegistry.getStolenEntity(stack);
                    if (stolen == null) stolen = PhotographEffectRegistry.getElementEntity(stack);
                    if (stolen != null && !ids.contains(stolen)) ids.add(stolen);
                }
            }
        });
        return ids;
    }

    /** 客户端：已装备 Boss 照片的去重种类数（同 Boss 多张只算一次） */
    public static int countBossPhotos(Player player) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        int n = 0;
        var handlerOpt = CuriosApi.getCuriosInventory(player);
        if (handlerOpt.isPresent()) {
            for (var sh : handlerOpt.get().getCurios().values()) {
                var stacks = sh.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    if (stack.getItem() instanceof PhotoAlbumItem) {
                        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                        for (ItemStack photo : contents.nonEmptyItems()) {
                            if (isBossDedup(photo, seen)) n++;
                        }
                    } else if (isBossDedup(stack, seen)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static boolean isBossDedup(ItemStack stack, java.util.Set<String> seen) {
        if (!PhotographEffectRegistry.isBossPhoto(stack)) return false;
        String ent = PhotographEffectRegistry.getPhotoEntity(stack);
        return ent != null ? seen.add(ent) : true;
    }

    /** 客户端：当前各元素抑制等级（供「照片套装效果」界面通用效果块显示） */
    public static Map<ElementDamage, Integer> collectElementLevels(Player player) {
        return PhotographEffectRegistry.countElementLevels(collectGearEntities(player));
    }
}
