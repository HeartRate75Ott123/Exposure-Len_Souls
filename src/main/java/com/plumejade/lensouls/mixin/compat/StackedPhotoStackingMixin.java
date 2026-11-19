package com.plumejade.lensouls.mixin.compat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 照片堆的合并规则（2026-11-19 用户选方案 B）。
 * <p>
 * 原版堆只按"是不是照片"判断能不能塞，结果空间扭曲/时空回溯/能力窃取的照片会和普通照片、
 * 甚至和另一种能力的照片混进同一堆。这里把"一张照片属于哪一组"算出来：
 * <ul>
 *   <li>三类特殊能力各自一组（空间扭曲 / 时空回溯 / 能力窃取）；</li>
 *   <li>普通照片（含见微知著等非特殊能力）统一算空组，照旧自由合并。</li>
 * </ul>
 * 组不同就不许合并——玩家手动拖拽也一样，于是"各自成堆"只是同一规则的自然结果，
 * 玩家的拆/合/取/放操作一个都没被禁掉。
 */
@Mixin(targets = "io.github.mortuusars.exposure.world.item.StackedPhotographsItem", remap = false)
public abstract class StackedPhotoStackingMixin {

    /** 需要各自成堆的四类特殊照片（lensouls:ability_type 的取值） */
    private static final Set<String> SPECIAL_GROUPS = Set.of("weakness_lens", "spatial_warp", "temporal_recall", "ability_steal");

    private static String lensouls$ability(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.contains("lensouls:ability_type") ? tag.getString("lensouls:ability_type") : null;
    }

    /** 分组键："" = 普通照片组；否则是三类特殊能力之一 */
    private static String lensouls$group(ItemStack stack) {
        String ability = lensouls$ability(stack);
        return ability != null && SPECIAL_GROUPS.contains(ability) ? ability : "";
    }

    @Inject(method = "overrideOtherStackedOnMe",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void lensouls$preventMixedStacking(
            ItemStack stack, ItemStack other, Slot slot,
            ClickAction action, Player player, SlotAccess access,
            CallbackInfoReturnable<Boolean> cir) {
        if (!lensouls$group(stack).equals(lensouls$group(other))) {
            cir.setReturnValue(false);
        }
    }
}
