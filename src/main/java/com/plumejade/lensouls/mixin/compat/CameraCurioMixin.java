package com.plumejade.lensouls.mixin.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 让 Exposure 的相机（含拍立得 {@code InstantCameraItem}，它是 {@code CameraItem} 的子类）
 * 成为<b>任意饰品栏</b>可戴的物品——照抄石之心的做法。
 * <p>
 * Curios 判定「能不能放进某个槽」只看 {@code ICurioItem.canEquip}（默认 true），
 * 槽位标签 {@code data/curios/tags/item/<slot>.json} 只负责生成 tooltip 里那串栏位名单，
 * 所以实现接口 + 覆盖 {@link #getSlotsTooltip} 就够了，不需要往 10 个槽位标签里各塞一遍
 * （那样 tooltip 会变成「栏位：护符, 腰带, 项链, 脚, 背饰, 首饰, 戒指, 手镯, 胸饰, 手饰」）。
 * <p>
 * {@code canEquipFromUse} 明确返回 false：相机的右键是开取景框，不能被 Curios 抢去装备。
 */
@Mixin(targets = "io.github.mortuusars.exposure.world.item.camera.CameraItem", remap = false)
public class CameraCurioMixin implements ICurioItem {

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return false;
    }

    /** 栏位提示：替换 Curios 生成的槽位列表，统一显示「任意饰品栏」 */
    @Override
    public List<Component> getSlotsTooltip(List<Component> tooltips, Item.TooltipContext context, ItemStack stack) {
        return List.of(Component.translatable("item.lensouls.curio_any_slot").withStyle(ChatFormatting.GOLD));
    }
}
