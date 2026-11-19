package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.PhotoInjector;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.CameraHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * 拍立得 {@code printPhotograph} 出片时注入能力数据。
 * <p>
 * 拦截照片进入背包前的关键调用点：
 * <ul>
 *   <li>{@code Inventory.setItem} — 空格直接塞入</li>
 *   <li>{@code StackedPhotographsItem.addPhotographOnTop} — 堆叠（同时记下目标堆，供出片后分流）</li>
 * </ul>
 * 通过 ThreadLocal 暂存照片引用，RETURN 时从队列取能力并注入。
 * <p>
 * <b>分流（2026-11-19 用户选方案 B）</b>：三类特殊能力的照片如果被原版塞进了
 * 普通堆或另一种能力的堆，就在注入完成后把它从那一堆里摘出来（它是刚插进 index 0 的），
 * 另开一个只有它自己的堆。原版堆满时会自己新建堆，所以"堆满 → 新建"不需要额外处理。
 */
@Mixin(targets = "io.github.mortuusars.exposure_polaroid.world.item.InstantCameraItem", remap = false)
public class PolaroidPrintMixin {

    @Unique
    private static final ThreadLocal<Boolean> suppressClientInsert = ThreadLocal.withInitial(() -> false);

    @Unique
    private static final ThreadLocal<ItemStack> capturedPhoto = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<ItemStack> capturedStack = new ThreadLocal<>();

    /** 空槽插入路径下照片落在第几格（堆路径置 -1） */
    @Unique
    private static final ThreadLocal<Integer> capturedSlot = ThreadLocal.withInitial(() -> -1);

    @Unique
    private static final Set<String> SPECIAL_GROUPS = Set.of("weakness_lens", "spatial_warp", "temporal_recall", "ability_steal");

    // ── 记录本次出片跑在哪一侧 ──
    @Inject(method = "printPhotograph", at = @At("HEAD"), require = 0)
    private static void lensouls$markSide(
            CameraHolder holder, ItemStack cameraStack, Frame frame, CallbackInfo ci) {
        // 非 cancellable，ci 不参与；descriptor 必须与 RETURN 那条保持一致，否则整条 mixin 会被判非法。
        suppressClientInsert.set(holder.asHolderEntity().level().isClientSide);
    }

    // ── 空空插槽路径 ──
    @Redirect(method = "printPhotograph", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V"), require = 0)
    private static void lensouls$captureSetItem(net.minecraft.world.entity.player.Inventory inventory, int slot, ItemStack stack) {
        if (suppressClientInsert.get()) return;
        LenSouls.LOGGER.debug("[Polaroid] capture: setItem slot={}", slot);
        if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) {
            capturedPhoto.set(stack);
            capturedSlot.set(slot);
        }
        inventory.setItem(slot, stack);
    }

    // ── 堆叠路径：记下照片 + 目标堆，并照常调用 ──
    @Redirect(method = "printPhotograph", at = @At(value = "INVOKE", target = "Lio/github/mortuusars/exposure/world/item/StackedPhotographsItem;addPhotographOnTop(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"), require = 0)
    private static void lensouls$captureStackTarget(io.github.mortuusars.exposure.world.item.StackedPhotographsItem item,
                                                    ItemStack target, ItemStack photo) {
        if (suppressClientInsert.get()) return;
        capturedSlot.set(-1);
        LenSouls.LOGGER.debug("[Polaroid] capture: addPhotographOnTop pileSize={}", item.getPhotographs(target).size());
        if (photo.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) {
            capturedPhoto.set(photo);
            capturedStack.set(target);
        }
        item.addPhotographOnTop(target, photo);
    }

    // ── 背包满时直接丢地上路径 ──
    @Redirect(method = "printPhotograph", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"), require = 0)
    private static net.minecraft.world.entity.item.ItemEntity lensouls$captureDrop(net.minecraft.world.entity.player.Player player, ItemStack stack, boolean dropAround, boolean traceItem) {
        if (suppressClientInsert.get()) return null;
        LenSouls.LOGGER.debug("[Polaroid] capture: drop");
        if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) capturedPhoto.set(stack);
        return player.drop(stack, dropAround, traceItem);
    }

    @Inject(method = "printPhotograph", at = @At("RETURN"), require = 0)
    private static void lensouls$inject(
            CameraHolder holder, ItemStack cameraStack, Frame frame, CallbackInfo ci) {
        ItemStack photo = capturedPhoto.get();
        capturedPhoto.remove();
        ItemStack target = capturedStack.get();
        capturedStack.remove();
        capturedSlot.remove();
        suppressClientInsert.remove();

        if (!(holder.asHolderEntity() instanceof ServerPlayer player)) return;

        // 出片注入统一走 PhotoInjector（与暗房共用同一实现）；path 只用于 debug 日志区分来源
        PhotoInjector.inject(photo, frame, player, true, "polaroid");

        // 空槽路径 = 原版已经给了独立一格，正是「优先单张」想要的结果：不搬、不动，也就不会闪跳。
        // 只有「原版把它并进某个堆」时才需要按口径纠正（见 divert）。
        lensouls$divertToOwnStack(photo, target, player);
        lensouls$splitMixedStacks(player);
        lensouls$normalizeSinglePhotoStacks(player);
        // 客户端已在插入点被挡掉（suppressClientInsert），不会再出现「本地多一张」，
        // 所以不需要整包重发；服务端 setItem/add 的改动会按原版通道同步。
    }

    /**
     * 特殊能力照片若被塞进了「另一种能力」或「普通照片」的堆，摘出来单独归位。
     * <p>
     * <b>坑 1（2026-11-20 用户实测「能力窃取和空间扭曲还是混在一起」）</b>：
     * 原实现拿 {@code getPhotographs(target).getItemUnsafe(0)} 当「这堆的分组」去比，
     * 而 index 0 就是刚插进去的这张（{@code addPhotographOnTop} = {@code add(0, photo)}），
     * 永远和自己相等、永远提前 return。⇒ 必须跟 <b>index 1..n</b> 的其余照片比。
     * <p>
     * <b>坑 2（同日「堆成了一个空照片堆，右键拖出来只剩空壳」）</b>：
     * 原修法把摘出来的那张塞进 {@code new ItemStack(stacked_photographs)} 造了<b>只装 1 张的堆</b>，
     * 而原版 {@code overrideOtherStackedOnMe} 拿照片时是「先 remove 再判 size()==1」——
     * 1 张的堆被判成 0 张，槽里就留下一个<b>没有照片的堆物品</b>（空壳）。
     * 原版世界里永远不存在 1 张的堆（降到 1 张就换回单张照片实体），所以这里必须遵守同一不变量：
     * <b>绝不造 1 张的堆</b>——先找身上同组且有位的堆塞进去，找不到就让它以单张照片存在。
     */
    @Unique
    private static void lensouls$divertToOwnStack(ItemStack photo, ItemStack target, ServerPlayer player) {
        if (photo == null || photo.isEmpty() || target == null || target.isEmpty()) return;

        String photoGroup = lensouls$group(photo);
        if (photoGroup.isEmpty()) return;   // 普通照片完全交还原版

        if (!(target.getItem() instanceof io.github.mortuusars.exposure.world.item.StackedPhotographsItem item)) return;

        var photographs = item.getPhotographs(target);
        if (photographs.isEmpty()) return;

        var topFrame = photographs.getItemUnsafe(0).get(Exposure.DataComponents.PHOTOGRAPH_FRAME);
        var photoFrame = photo.get(Exposure.DataComponents.PHOTOGRAPH_FRAME);
        if (topFrame == null || photoFrame == null || !topFrame.equals(photoFrame)) return;

        var withInjected = photographs.remove(0).add(0, photo);

        boolean mixed = false;
        for (int i = 1; i < withInjected.size(); i++) {
            if (!photoGroup.equals(lensouls$group(withInjected.getItemUnsafe(i)))) {
                mixed = true;
                break;
            }
        }
        item.setPhotographs(target, withInjected);

        // 口径（2026-11-20 用户定）：特殊照片**优先各自单张**，只有真的没地方放才并堆。
        boolean hasSpace = lensouls$hasFreeSlot(player);
        if (!mixed && !hasSpace) {
            LenSouls.LOGGER.debug("[Polaroid] 没空格，保留并入同组堆: group={} size={}", photoGroup, withInjected.size());
            return;
        }
        if (!mixed) {
            item.setPhotographs(target, withInjected.remove(0));
            if (player.getInventory().add(photo)) {
                LenSouls.LOGGER.debug("[Polaroid] 照片改回单张: group={} 已落 slot={}", photoGroup, lensouls$slotOf(player, photo));
            } else {
                player.drop(photo, false);
            }
            return;
        }

        // 混装：必须摘出来，再按「单张优先」归位
        item.setPhotographs(target, withInjected.remove(0));
        if (player.getInventory().add(photo)) {
            LenSouls.LOGGER.debug("[Polaroid] 照片另成单张: group={} 已落 slot={}（摘出前所在一堆 slot={}）",
                    photoGroup, lensouls$slotOf(player, photo), lensouls$slotOf(player, target));
            return;
        }
        lensouls$mergeOrDrop(photo, photoGroup, player);
    }
    /** 这个物品栈当前在第几格（-1 = 不在背包里）；只用于诊断日志 */
    @Unique
    private static int lensouls$slotOf(ServerPlayer player, ItemStack stack) {
        var items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == stack) return i;
        }
        return -1;
    }

    /** 背包（主背包 + 快捷栏）里是否还有空格 */
    @Unique
    private static boolean lensouls$hasFreeSlot(ServerPlayer player) {
        var items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) return true;
        }
        return false;
    }

    /**
     * 背包真满时的兜底：并进**同组且有位**的堆；连堆都没有就掉在地上（绝不删除）。
     * <p>
     * 「同组单张升级成堆」故意不做：那要在满是照片的背包里找两个同组单张重建物品，
     * 一旦中途任何一步不成立就会把照片弄丢，和「宁可掉地上」的口径冲突。
     */
    @Unique
    private static void lensouls$mergeOrDrop(ItemStack photo, String group, ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack candidate = inventory.items.get(i);
            if (candidate.isEmpty()) continue;
            if (!(candidate.getItem() instanceof io.github.mortuusars.exposure.world.item.StackedPhotographsItem other)) continue;
            var inner = other.getPhotographs(candidate);
            if (inner.isEmpty()) continue;
            if (!group.equals(lensouls$group(inner.getItemUnsafe(0)))) continue;
            if (!other.canAddPhotograph(candidate)) continue;
            other.addPhotographOnTop(candidate, photo);
            LenSouls.LOGGER.debug("[Polaroid] 没空格，并入同组堆: group={} size={}", group, other.getPhotographs(candidate).size());
            return;
        }
        player.drop(photo, false);
        LenSouls.LOGGER.debug("[Polaroid] 背包满且无同组堆，照片掉在地上: group={}", group);
    }
    /**
     * 追溯整理：把**已经混装**的堆按组拆开。
     * <p>
     * 1.8.182 之前形成的堆里可能同时躺着普通照片和不同能力的照片（分流只处理「新进堆的那张」，
     * 旧内容不管）；用户「把堆掀开」时看到的就是这些。这里在每次出片后顺手扫一遍背包：
     * 第一组留在原格（只剩 1 张就还原成单张照片，绝不造 1 张的堆），其余组逐张归位。
     */
    @Unique
    private static void lensouls$splitMixedStacks(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof io.github.mortuusars.exposure.world.item.StackedPhotographsItem item)) continue;
            var photographs = item.getPhotographs(stack);
            if (photographs.size() < 2) continue;

            java.util.LinkedHashMap<String, java.util.ArrayList<ItemStack>> byGroup = new java.util.LinkedHashMap<>();
            for (int k = 0; k < photographs.size(); k++) {
                ItemStack inner = photographs.getItemUnsafe(k);
                byGroup.computeIfAbsent(lensouls$group(inner), key -> new java.util.ArrayList<>()).add(inner);
            }
            if (byGroup.size() < 2) continue;

            var iterator = byGroup.entrySet().iterator();
            var first = iterator.next();
            if (first.getValue().size() == 1) {
                inventory.setItem(i, first.getValue().get(0));
            } else {
                item.setPhotographs(stack, new io.github.mortuusars.exposure.world.item.component.StackedPhotographs(first.getValue()));
            }
            while (iterator.hasNext()) {
                for (ItemStack orphan : iterator.next().getValue()) {
                    lensouls$placePhoto(orphan, player, stack);
                }
            }
            LenSouls.LOGGER.debug("[Polaroid] 混装堆已拆开 slot={} groups={}", i, byGroup.keySet());
        }
    }

    /** 把一张照片归位：优先并进身上同组且有位的堆，否则作为单张照片放进背包（不行就丢地上）。 */
    @Unique
    private static void lensouls$placePhoto(ItemStack photo, ServerPlayer player, ItemStack skip) {
        String group = lensouls$group(photo);
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack slotStack = inventory.items.get(i);
            if (slotStack.isEmpty() || slotStack == skip) continue;
            if (!(slotStack.getItem() instanceof io.github.mortuusars.exposure.world.item.StackedPhotographsItem other)) continue;
            var inner = other.getPhotographs(slotStack);
            if (inner.isEmpty()) continue;
            if (!group.equals(lensouls$group(inner.getItemUnsafe(0)))) continue;
            if (!other.canAddPhotograph(slotStack)) continue;
            other.addPhotographOnTop(slotStack, photo);
            return;
        }
        if (!inventory.add(photo)) {
            player.drop(photo, false);
        }
    }

    /**
     * 原版不变量：**不存在只装 1 张照片的堆**（降到 1 张就会换回单张 photograph 物品）。
     * <p>
     * {@code InstantCameraItem.printPhotograph} 有一条"背包里有单张照片 → 新建一个堆把两张都塞进去"的
     * 路径，我们分流时会把刚拍的那张从新建的堆里摘走，于是一个只装 1 张的堆被 {@code setItem} 放进背包，
     * 玩家拖它出来时原版先 remove 再判 {@code size()==1}，判成 0 张 ⇒ 槽里留下**空壳**。
     * 所以每次出片分流后，把背包里所有"只剩 1 张的堆"还原成那张照片本身。
     */
    @Unique
    private static void lensouls$normalizeSinglePhotoStacks(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof io.github.mortuusars.exposure.world.item.StackedPhotographsItem item)) continue;
            var photographs = item.getPhotographs(stack);
            if (photographs.size() != 1) continue;
            inv.setItem(i, photographs.getItemUnsafe(0).copy());
            LenSouls.LOGGER.debug("[Polaroid] 单张堆还原成照片 slot={}", i);
        }
        if (!inv.offhand.isEmpty()) {
            ItemStack off = inv.offhand.get(0);
            if (!off.isEmpty() && off.getItem() instanceof io.github.mortuusars.exposure.world.item.StackedPhotographsItem item) {
                var photographs = item.getPhotographs(off);
                if (photographs.size() == 1) inv.offhand.set(0, photographs.getItemUnsafe(0).copy());
            }
        }
    }

    /** 分组键：特殊能力名；普通照片返回空串。 */
    @Unique
    private static String lensouls$group(ItemStack stack) {
        String ability = lensouls$ability(stack);
        return ability != null && SPECIAL_GROUPS.contains(ability) ? ability : "";
    }

    @Unique
    private static String lensouls$ability(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.contains("lensouls:ability_type") ? tag.getString("lensouls:ability_type") : null;
    }
}