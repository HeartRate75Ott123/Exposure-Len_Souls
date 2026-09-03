package com.plumejade.lensouls.integration;

import com.plumejade.lensouls.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.event.CurioCanEquipEvent;

public class CuriosIntegration {

    /** 照片栏槽物品归属 tag（data/curios/tags/item/photograph.json）：凡命中即可佩戴进照片栏 */
    private static final TagKey<Item> PHOTOGRAPH_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("curios", "photograph"));

    /**
     * 注册照片栏自定义槽谓词（lensouls:photo_curio）。
     * <p>
     * Curios 的 shift 快速转移门槛（getItemStackSlots）与拖放（isStackValid）都按槽 validators 判定；
     * 运行时 curios:photograph item tag 时常未注册（ABSENT），导致默认 curios:tag 谓词判空、照片无法
     * shift 进槽。改用自定义谓词后，照片槽判定改为按 ItemStack 数据精确判断：
     * entity_photograph / photo_album 恒放；exposure/instant 照片仅当带 lensouls 拍摄数据
     * （photograph_curio 或 stolen_entity）放行；普通照片不放行。
     */
    public static void registerPhotoCurioPredicate() {
        CuriosApi.registerCurioPredicate(
                ResourceLocation.fromNamespaceAndPath("lensouls", "photo_curio"),
                CuriosIntegration::isPhotoCurioValid);
    }

    private static boolean isPhotoCurioValid(SlotResult slotResult) {
        ItemStack stack = slotResult.stack();
        String key = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (key.equals("lensouls:entity_photograph") || key.equals("lensouls:photo_album")) {
            return true;
        }
        if (key.equals("exposure:photograph") || key.equals("exposure_polaroid:instant_photograph")) {
            return hasLensoulsPhotoData(stack);
        }
        return false;
    }

    /** exposure/拍立得照片是否带镜魂拍摄数据（能力窃取/注入照片） */
    private static boolean hasLensoulsPhotoData(ItemStack stack) {
        if (stack.get(DataComponents.CUSTOM_DATA) == null) return false;
        var tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        return tag.getBoolean("lensouls:photograph_curio")
                || tag.contains("lensouls:stolen_entity");
    }

    @SubscribeEvent
    public static void onCurioCanEquip(CurioCanEquipEvent event) {
        String slotId = event.getSlotContext().identifier();
        if (!"photograph".equals(slotId) && !"lensouls:photograph".equals(slotId)) return;

        ItemStack stack = event.getStack();
        // 羽毛类饰品不限制：可佩戴进任意槽位（含照片栏）
        if (stack.is(ModItems.FEATHER_TWITCHER.get()) || stack.is(ModItems.FEATHER_ELEMENTRISE.get())) {
            return;
        }
        // 放宽：凡命中 curios:photograph tag（任何照片 / 相册）即放行，
        // 不要求 CUSTOM 标记 lensouls:photograph_curio —— 修复 JEI/合成等无标记照片无法 shift 进照片槽
        if (stack.is(PHOTOGRAPH_TAG)) {
            event.setEquipResult(TriState.TRUE);
            return;
        }
        // 相册：显式放行（兜底，其在 tag 内）
        if (stack.is(ModItems.PHOTO_ALBUM.get())) {
            event.setEquipResult(TriState.TRUE);
            return;
        }

        // 兼容旧带标记物品；其余（非照片类）拒绝占照片栏
        boolean hasTag = false;
        if (stack.get(DataComponents.CUSTOM_DATA) != null) {
            hasTag = stack.get(DataComponents.CUSTOM_DATA)
                    .copyTag().getBoolean("lensouls:photograph_curio");
        }
        event.setEquipResult(hasTag ? TriState.TRUE : TriState.FALSE);
    }
}

