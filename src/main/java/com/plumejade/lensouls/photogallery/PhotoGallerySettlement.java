package com.plumejade.lensouls.photogallery;

import com.plumejade.lensouls.integration.PhotoSetRegistry;
import com.plumejade.lensouls.item.EntityPhotographItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 照片图鉴的**服务端权威结算**：点格子（发放 / 装备）、清空、剔除重复。
 * <p>
 * 客户端只发意图（见 {@code PhotoGalleryActionPacket} / {@code PhotoGalleryShortcutPacket}），
 * 一切校验与改动都在这里做；返回的 {@link Settlement} 带回「提示 lang 键 + 数字参数」，
 * 由同步包捎给客户端用 {@code FeatherToast} 显示（界面内提示，动作栏消息会被界面盖住）。
 * <p>
 * 物品去向的铁律：**只搬不删** —— 从饰品槽取出来的照片一律退回物品栏，满了丢在脚下，绝不凭空销毁。
 */
public final class PhotoGallerySettlement {

    /** 结算回执：toastKey 为空 = 不提示；count = 提示里的数字参数（件数） */
    public record Settlement(String toastKey, int count) {
        public static final Settlement NONE = new Settlement("", 0);
    }

    private static final String TOAST_LOCKED = "gui.lensouls.photos.toast.locked";
    private static final String TOAST_GRANTED = "gui.lensouls.photos.toast.granted";
    private static final String TOAST_DROPPED = "gui.lensouls.photos.toast.dropped";
    private static final String TOAST_EQUIPPED = "gui.lensouls.photos.toast.equipped";
    private static final String TOAST_ALREADY = "gui.lensouls.photos.toast.already";
    private static final String TOAST_NO_SLOT = "gui.lensouls.photos.toast.no_slot";

    private PhotoGallerySettlement() {
    }

    // ========== 点击网格 ==========

    /** 点击照片页的一个格子：未解锁 → 只提示；否则按当前规则发放 / 装备 */
    public static Settlement clickCell(ServerPlayer player, PhotoGalleryData data, String entityId) {
        if (entityId == null || entityId.isEmpty()) return Settlement.NONE;
        if (!data.isUnlocked(entityId)) return new Settlement(TOAST_LOCKED, 0);

        ItemStack photo = EntityPhotographItem.create(entityId);

        if (data.clickRule() == PhotoGalleryData.ClickRule.EQUIP) {
            switch (equip(player, entityId, photo)) {
                case EQUIPPED -> {
                    return new Settlement(TOAST_EQUIPPED, 0);
                }
                case ALREADY -> {
                    // 已装备同种：不重复装备、也不覆盖已有槽位内容
                    return new Settlement(TOAST_ALREADY, 0);
                }
                case NO_SLOT -> {
                    // 照片栏位已满 ⇒ 退化为发放到物品栏（默认口径，见文档 §14 待确认 ⑨）
                    boolean added = give(player, photo);
                    return new Settlement(added ? TOAST_NO_SLOT : TOAST_DROPPED, 0);
                }
                case NO_CURIOS -> {
                    // 没有 Curios（理论上装不了照片）⇒ 同样退化为发放
                }
                default -> {
                }
            }
        }

        boolean added = give(player, photo);
        return new Settlement(added ? TOAST_GRANTED : TOAST_DROPPED, 0);
    }

    private enum EquipResult { EQUIPPED, ALREADY, NO_SLOT, NO_CURIOS }

    /** 装备到第一个空闲的照片槽；已装备同种则直接返回 ALREADY（不覆盖任何已有内容） */
    private static EquipResult equip(ServerPlayer player, String entityId, ItemStack photo) {
        // 「已装备」口径与照片 tooltip / 套装统计完全一致（照片槽 + 相册内容物，按实体去重）
        try {
            if (PhotoSetRegistry.collectInstalledEntities(player).contains(entityId)) {
                return EquipResult.ALREADY;
            }
        } catch (Throwable ignored) {
        }

        try {
            var opt = CuriosApi.getCuriosInventory(player);
            if (opt.isEmpty()) return EquipResult.NO_CURIOS;
            var handler = opt.get().getStacksHandler(PhotoGalleryRules.PHOTO_SLOT);
            if (handler.isEmpty()) return EquipResult.NO_CURIOS;

            IDynamicStackHandler stacks = handler.get().getStacks();
            // 槽位数量是动态的（诅咒 e14 会 -2/+4、弱属性照片套装会加槽），所以每次现查
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (stacks.getStackInSlot(i).isEmpty()) {
                    opt.get().setEquippedCurio(PhotoGalleryRules.PHOTO_SLOT, i, photo);
                    return EquipResult.EQUIPPED;
                }
            }
            return EquipResult.NO_SLOT;
        } catch (Throwable ignored) {
            return EquipResult.NO_CURIOS;
        }
    }

    // ========== 快捷功能 ==========

    /**
     * 清空当前装备的全部照片饰品（照片槽 + 相册本体）。
     *
     * @return 实际搬走的件数（用于提示）
     */
    public static int clearEquipped(ServerPlayer player) {
        int moved = 0;
        List<PhotoGalleryRules.SlotRef> refs = PhotoGalleryRules.photoCurioSlots(player);
        for (PhotoGalleryRules.SlotRef ref : refs) {
            ItemStack taken = extract(player, ref.identifier(), ref.index());
            if (taken.isEmpty()) continue;
            give(player, taken);
            moved += taken.getCount();
        }
        return moved;
    }

    /**
     * 剔除重复的照片饰品：同一 {@code lensouls:stolen_entity} 只保留一件。
     * <p>
     * 保留顺序：**相册内容物优先**（相册里的照片不动）→ 照片槽按槽位靠前者优先。
     * 多余的一律退回物品栏（满则丢在脚下）。
     * <p>
     * 开发口径（**不要写进任何用户可见文案**）：照片本身可堆叠、加上相册会整箱生效，
     * 所以同种生物完全可能同时存在多张 —— 这是既有数据结构的结果，不是玩家操作失误。
     *
     * @return 实际剔除的件数（用于提示）
     */
    public static int dedupe(ServerPlayer player) {
        Set<String> seen = new HashSet<>();

        // ① 相册内容物里的 id 先登记：相册里那几张保留
        for (PhotoGalleryRules.SlotRef ref : PhotoGalleryRules.photoCurioSlots(player)) {
            if (ref.stack().getItem() instanceof com.plumejade.lensouls.item.PhotoAlbumItem) {
                seen.addAll(PhotoGalleryRules.albumPhotoIds(ref.stack()));
            }
        }

        // ② 逐个照片槽：先登记第一个，重复的取出来
        List<PhotoGalleryRules.SlotRef> duplicates = new ArrayList<>();
        try {
            var opt = CuriosApi.getCuriosInventory(player);
            if (opt.isEmpty()) return 0;
            var handler = opt.get().getStacksHandler(PhotoGalleryRules.PHOTO_SLOT);
            if (handler.isEmpty()) return 0;
            IDynamicStackHandler stacks = handler.get().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (stack.getItem() instanceof com.plumejade.lensouls.item.PhotoAlbumItem) continue;
                String id = PhotoGalleryRules.photoEntityId(stack);
                if (id == null || id.isEmpty()) continue;
                if (seen.add(id)) continue;
                duplicates.add(new PhotoGalleryRules.SlotRef(PhotoGalleryRules.PHOTO_SLOT, i, stack.copy()));
            }
        } catch (Throwable ignored) {
            return 0;
        }

        int removed = 0;
        for (PhotoGalleryRules.SlotRef ref : duplicates) {
            ItemStack taken = extract(player, ref.identifier(), ref.index());
            if (taken.isEmpty()) continue;
            give(player, taken);
            removed += taken.getCount();
        }
        return removed;
    }

    // ========== 搬运原语 ==========

    /** 从 Curios 指定槽位取出（Curios 会自行同步与重算属性） */
    private static ItemStack extract(ServerPlayer player, String identifier, int index) {
        try {
            var opt = CuriosApi.getCuriosInventory(player);
            if (opt.isEmpty()) return ItemStack.EMPTY;
            var handler = opt.get().getStacksHandler(identifier);
            if (handler.isEmpty()) return ItemStack.EMPTY;
            IDynamicStackHandler stacks = handler.get().getStacks();
            if (index < 0 || index >= stacks.getSlots()) return ItemStack.EMPTY;
            return stacks.extractItem(index, Integer.MAX_VALUE, false);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    /** 退回物品栏，满了丢在脚下；@return true = 进了物品栏 */
    private static boolean give(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        if (player.getInventory().add(stack)) return true;
        player.drop(stack, false);
        return false;
    }
}
