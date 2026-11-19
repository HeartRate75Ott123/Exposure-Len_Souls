package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.PhotoInjector;
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
    private static final ThreadLocal<ItemStack> capturedPhoto = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<ItemStack> capturedStack = new ThreadLocal<>();

    @Unique
    private static final Set<String> SPECIAL_GROUPS = Set.of("weakness_lens", "spatial_warp", "temporal_recall", "ability_steal");

    // ── 空空插槽路径 ──
    @ModifyArg(method = "printPhotograph", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V"), index = 1, require = 0)
    private static ItemStack lensouls$captureSetItem(ItemStack stack) {
        LenSouls.LOGGER.debug("[Polaroid] capture: setItem");
        if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) capturedPhoto.set(stack);
        return stack;
    }

    // ── 堆叠路径：记下照片 + 目标堆，并照常调用 ──
    @Redirect(method = "printPhotograph", at = @At(value = "INVOKE", target = "Lio/github/mortuusars/exposure/world/item/StackedPhotographsItem;addPhotographOnTop(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"), require = 0)
    private static void lensouls$captureStackTarget(io.github.mortuusars.exposure.world.item.StackedPhotographsItem item,
                                                    ItemStack target, ItemStack photo) {
        if (photo.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) {
            capturedPhoto.set(photo);
            capturedStack.set(target);
        }
        item.addPhotographOnTop(target, photo);
    }

    // ── 背包满时直接丢地上路径 ──
    @ModifyArg(method = "printPhotograph", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"), index = 0, require = 0)
    private static ItemStack lensouls$captureDrop(ItemStack stack) {
        LenSouls.LOGGER.debug("[Polaroid] capture: drop");
        if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) capturedPhoto.set(stack);
        return stack;
    }

    @Inject(method = "printPhotograph", at = @At("RETURN"), require = 0)
    private static void lensouls$inject(
            CameraHolder holder, ItemStack cameraStack, Frame frame, CallbackInfo ci) {
        ItemStack photo = capturedPhoto.get();
        capturedPhoto.remove();
        ItemStack target = capturedStack.get();
        capturedStack.remove();

        if (!(holder.asHolderEntity() instanceof ServerPlayer player)) return;

        // 出片注入统一走 PhotoInjector（与暗房共用同一实现）；path 只用于 debug 日志区分来源
        PhotoInjector.inject(photo, frame, player, true, "polaroid");

        lensouls$divertToOwnStack(photo, target, player);
    }

    /** 特殊能力照片若被塞进了不匹配的堆，摘出来另起一堆。 */
    @Unique
    private static void lensouls$divertToOwnStack(ItemStack photo, ItemStack target, ServerPlayer player) {
        if (photo == null || photo.isEmpty() || target == null || target.isEmpty()) return;

        String ability = lensouls$ability(photo);
        if (ability == null || !SPECIAL_GROUPS.contains(ability)) return;

        if (!(target.getItem() instanceof io.github.mortuusars.exposure.world.item.StackedPhotographsItem item)) return;

        // 目标堆里第一张照片的能力 = 这一堆的分组
        var photographs = item.getPhotographs(target);
        if (photographs.size() <= 0) return;
        String stackAbility = lensouls$ability(photographs.getItemUnsafe(0));
        if (ability.equals(stackAbility)) return; // 同能力的堆，允许（继续往里放）

        // 刚插进来的一定是 index 0；直接改组件（removePhotograph 返回的是 ItemAndStack 包装）
        ItemStack removed = photographs.getItemUnsafe(0);
        if (removed.isEmpty()) return;
        item.setPhotographs(target, photographs.remove(0));

        ItemStack fresh = new ItemStack(item);
        item.addPhotographOnTop(fresh, removed);
        if (!player.getInventory().add(fresh)) {
            player.drop(fresh, false);
        }
        LenSouls.LOGGER.debug("[Polaroid] 特殊照片另起一堆: ability={}", ability);
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
