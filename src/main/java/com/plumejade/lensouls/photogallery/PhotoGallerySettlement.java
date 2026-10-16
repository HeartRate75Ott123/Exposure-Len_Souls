package com.plumejade.lensouls.photogallery;

import com.plumejade.lensouls.config.BossEntityLoader;
import com.plumejade.lensouls.config.PhotoSetDefs;
import com.plumejade.lensouls.integration.PhotoSetRegistry;
import com.plumejade.lensouls.item.EntityPhotographItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    /** 成套：一部分进了照片栏、其余因栏位满而发放到物品栏 */
    private static final String TOAST_PARTIAL = "gui.lensouls.photos.toast.partial";
    /** 成套：该套装已全部装备（安装规则下没有可做的事） */
    private static final String TOAST_ALREADY_SET = "gui.lensouls.photos.toast.already.set";
    /** 成套：一个成员都还没窃取过 */
    private static final String TOAST_LOCKED_SET = "gui.lensouls.photos.toast.locked.set";

    private PhotoGallerySettlement() {
    }

    // ========== 点击网格 ==========
    // （套装格见下方 clickSetCell：一次成套发放 / 安装）

    /**
     * 点击**套装页**的一个格子：把该套装**已经窃取过的成员**一次性成套发放 / 安装。
     * <p>
     * 口径（2026-10 反馈）：
     * <ul>
     *   <li>发放 / 安装都遵守当前的点击规则（与照片格同一条规则行）。</li>
     *   <li>只处理**已窃取**的成员 —— 没窃取过的生物本来就没有照片（照片页的同一铁律）；
     *       套装格只有在成员集齐时才会解锁，所以正常路径下这就是「一整套」。</li>
     *   <li>安装规则下**不重复安装已有的**（照片槽或相册里已有的直接跳过）。</li>
     *   <li>照片栏放不下的部分**退化为发放到物品栏**，提示如实交代（部分放入 ⇒ 「照片栏已满，无法放入的照片已发放到物品栏」）。</li>
     * </ul>
     */
    public static Settlement clickSetCell(ServerPlayer player, PhotoGalleryData data, String setId) {
        if (setId == null || setId.isEmpty()) return Settlement.NONE;

        List<String> members = new ArrayList<>();
        if ("boss_barrage".equals(setId)) {
            // 首领套没有手工成员清单 —— 成员的语义是「**任意 N 只 BOSS**」（N = 该档 count，现为 5）。
            // 用户口径：点它就直接发放**最新拍的**那 N 只（stolenSnapshot 的值就是「首次窃取」的时间），
            // 而不是把已窃取的全部 BOSS 都发一遍。
            int need = bossSetNeed(setId);
            List<Map.Entry<String, Long>> newest = new ArrayList<>();
            for (Map.Entry<String, Long> e : data.stolenSnapshot().entrySet()) {
                if (data.isUnlocked(e.getKey()) && BossEntityLoader.isBoss(e.getKey())) newest.add(e);
            }
            newest.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));   // 时间新 → 前
            for (int i = 0; i < newest.size() && i < need; i++) members.add(newest.get(i).getKey());
        } else {
            for (String member : PhotoSetRegistry.getMembers(setId)) {
                if (data.isUnlocked(member)) members.add(member);
            }
        }
        if (members.isEmpty()) return new Settlement(TOAST_LOCKED_SET, 0);

        boolean equip = data.clickRule() == PhotoGalleryData.ClickRule.EQUIP;
        int installed = 0;   // 装进照片栏
        int granted = 0;     // 按发放规则进物品栏
        int fallback = 0;    // 安装规则下因栏位满而退化为发放
        int dropped = 0;     // 物品栏也满，丢在脚下

        for (String member : members) {
            ItemStack photo = EntityPhotographItem.create(member);
            if (equip) {
                switch (equip(player, member, photo)) {
                    case EQUIPPED -> {
                        installed++;
                        continue;
                    }
                    case ALREADY -> {
                        continue;   // ★ 不重复安装已有的（照片槽 / 相册里已有）
                    }
                    default -> {
                        // NO_SLOT / NO_CURIOS ⇒ 退化为发放（见下）
                    }
                }
                if (photo.isEmpty()) {
                    // 已被 Curios 退回背包：照片其实已经「发放」了，别再补发（那会变两份）
                    fallback++;
                    continue;
                }
                if (give(player, photo)) fallback++;
                else dropped++;
            } else {
                if (give(player, photo)) granted++;
                else dropped++;
            }
        }

        int given = granted + fallback;
        if (installed > 0 && (given > 0 || dropped > 0)) return new Settlement(TOAST_PARTIAL, 0);
        if (installed > 0) return new Settlement(TOAST_EQUIPPED, 0);
        if (dropped > 0) return new Settlement(TOAST_DROPPED, 0);
        if (fallback > 0) return new Settlement(TOAST_NO_SLOT, 0);
        if (granted > 0) return new Settlement(TOAST_GRANTED, 0);
        // 成员都在、但一件都没动手 ⇒ 安装规则下该套装已全部装备
        return new Settlement(TOAST_ALREADY_SET, 0);
    }


    /** 首领套一次要发几只：取该套装各档 {@code effectiveCount} 的**最大**档（与照片 tooltip / 图鉴同行口径） */
    private static int bossSetNeed(String setId) {
        PhotoSetDefs.SetDef def = PhotoSetDefs.get(setId);
        if (def == null) return 1;
        return def.tiers().stream()
                .mapToInt(t -> PhotoSetDefs.effectiveCount(setId, t)).max().orElse(1);
    }

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
                    // 照片栏没有可用空位（或写入未落槽、被 Curios 退回）⇒ **如实回退为发放到物品栏**；
                    // 若实例已被 Curios 退回背包，give() 收到空堆会安全跳过，不会重复发放。
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

    /**
     * 装备到第一个空闲的照片槽；已装备同种则直接返回 ALREADY（不覆盖任何已有内容）。
     * <p>
     * <b>写入后必须回读校验。</b>{@code setEquippedCurio} 在「饰品与槽位不匹配 / 槽位被修饰符裁掉 /
     * 该槽此刻不可用」这类情况下**不抛异常也不落槽**，Curios 会把那件饰品直接退回玩家背包
     * （同一个 ItemStack 实例被消耗）。不回读就会出现「实物进了背包、提示却写『已装备照片』」。
     * 校验不过就继续找下一个空槽；全都不行返回 {@link EquipResult#NO_SLOT}，
     * 由调用方**如实**回退成「发放到物品栏」的提示。
     */
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
                if (!stacks.getStackInSlot(i).isEmpty()) continue;
                opt.get().setEquippedCurio(PhotoGalleryRules.PHOTO_SLOT, i, photo);

                // ★ 回读校验：真落进这一格、且就是这张照片，才算装备成功
                ItemStack landed = stacks.getStackInSlot(i);
                if (!landed.isEmpty() && entityId.equals(PhotoGalleryRules.photoEntityId(landed))) {
                    return EquipResult.EQUIPPED;
                }
                // 没落槽。若我们的实例已被 Curios 退回背包（被消耗），说明照片其实已经「发放」了，
                // 不能再往下试、也不能由调用方补发（那会变两份），直接按 NO_SLOT 如实提示。
                if (photo.isEmpty()) return EquipResult.NO_SLOT;
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
            // ★ 必须先读数量再 give()：Inventory.add 会把入参 stack **原地消耗成 0**，
            //   放在 give 之后读就会恒为 0（实测「卸下 N 个却提示 0 个」的根因）。
            int count = taken.getCount();
            give(player, taken);
            moved += count;
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
            int count = taken.getCount();      // 同上：务必在 give() 之前读
            give(player, taken);
            removed += count;
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

    /**
     * 退回物品栏，满了丢在脚下。
     * <p>
     * <b>注意：会消耗入参 {@code stack}。</b>{@code Inventory.add(ItemStack)} 是把数量搬进背包后
     * **原地把该 stack 清成 0**（原版语义），所以调用方若要统计件数，**必须先 `getCount()` 再 give()**
     * —— 放到之后读会恒为 0（照片图鉴「一键卸下」曾因此永远提示 0 个）。
     *
     * @return true = 进了物品栏
     */
    private static boolean give(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        if (player.getInventory().add(stack)) return true;
        player.drop(stack, false);
        return false;
    }
}
