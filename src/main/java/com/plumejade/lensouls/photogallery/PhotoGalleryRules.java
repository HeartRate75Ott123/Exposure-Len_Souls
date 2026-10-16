package com.plumejade.lensouls.photogallery;

import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import com.plumejade.lensouls.item.EntityPhotographItem;
import com.plumejade.lensouls.item.ModItems;
import com.plumejade.lensouls.item.PhotoAlbumItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 照片饰品的统一判定与遍历口径（服务端结算 / 客户端界面共用一份）。
 * <p>
 * <b>「照片饰品」= 能装进 Curios {@code photograph} 槽的东西</b>：
 * {@code lensouls:entity_photograph}、{@code lensouls:photo_album}、以及带镜魂拍摄数据
 * （{@code lensouls:stolen_entity} / {@code lensouls:photograph_curio}）的 Exposure 照片。
 * 判存在性一律先 {@code CustomData.contains}，不 {@code copyTag()}（热路径零分配，见 AGENTS.md）。
 */
public final class PhotoGalleryRules {

    /** 照片饰品栏的 Curios 标识（与 CuriosIntegration / PhotoSpecialEffects 同口径） */
    public static final String PHOTO_SLOT = "photograph";

    private PhotoGalleryRules() {
    }

    /** 该物品是否是「照片饰品」（可放照片槽） */
    public static boolean isPhotoCurio(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof EntityPhotographItem) return true;
        if (stack.getItem() instanceof PhotoAlbumItem) return true;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        if (data.contains("lensouls:stolen_entity")) return true;
        return data.contains("lensouls:photograph_curio")
                && data.copyTag().getBoolean("lensouls:photograph_curio");
    }

    /** 能力窃取照片记录的实体 id（非能力窃取照片/普通物品 → null） */
    public static String photoEntityId(ItemStack stack) {
        return PhotographEffectRegistry.getStolenEntity(stack);
    }

    /**
     * 遍历玩家 Curios 里**所有槽位**的照片饰品（含相册本体；不含相册内容物）。
     * <p>
     * 只在「打开图鉴界面点快捷功能」这种低频路径调用；不缓存句柄。
     */
    public static List<SlotRef> photoCurioSlots(Player player) {
        List<SlotRef> out = new ArrayList<>();
        if (player == null) return out;
        try {
            var opt = CuriosApi.getCuriosInventory(player);
            if (opt.isEmpty()) return out;
            for (var entry : opt.get().getCurios().entrySet()) {
                IDynamicStackHandler stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty() && isPhotoCurio(stack)) {
                        out.add(new SlotRef(entry.getKey(), i, stack.copy()));
                    }
                }
            }
        } catch (Throwable ignored) {
            // Curios 缺席 / 句柄异常：当作没有照片饰品，绝不影响开屏
        }
        return out;
    }

    /** 照片槽（仅 {@code photograph} 栏）里当前占用情况：index → stack */
    public static List<ItemStack> photoSlotStacks(Player player) {
        List<ItemStack> out = new ArrayList<>();
        try {
            var opt = CuriosApi.getCuriosInventory(player);
            if (opt.isEmpty()) return out;
            var handler = opt.get().getStacksHandler(PHOTO_SLOT);
            if (handler.isEmpty()) return out;
            IDynamicStackHandler stacks = handler.get().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) out.add(stacks.getStackInSlot(i));
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 相册内容物里记录的能力窃取实体 id 列表（去重由调用方决定） */
    public static List<String> albumPhotoIds(ItemStack album) {
        List<String> out = new ArrayList<>();
        if (album == null || album.isEmpty() || !(album.getItem() instanceof PhotoAlbumItem)) return out;
        ItemContainerContents contents =
                album.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        for (ItemStack inner : contents.nonEmptyItems()) {
            String id = photoEntityId(inner);
            if (id != null && !id.isEmpty()) out.add(id);
        }
        return out;
    }

    /** 槽位引用（identifier + index），用于「清空/剔除」时的定位 */
    public record SlotRef(String identifier, int index, ItemStack stack) {
    }
}
