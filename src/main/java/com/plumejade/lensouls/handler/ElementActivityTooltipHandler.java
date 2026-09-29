package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.config.ItemElementActivityLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.util.WeaknessLensPhoto;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Map;

/**
 * 武器元素活性 tooltip 处理器。
 * <p>
 * 等级 = max(物品在 {@code item_element_activity} 数据包里配置的活性,
 * 已装弱点透镜照片提供的 2 级活性)，在 tooltip 末尾添加一行绿色提示，格式如 "§a火 II"。
 * 支持 {@code /reload} 热重载。
 */
public class ElementActivityTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // 从注册名查活性配置
        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Map<ElementDamage, Integer> levels = ItemElementActivityLoader.getLevels(itemId);

        // 装了弱点透镜照片的武器：照片记录的元素按 2 级活性计入（武器自身更高则按自身的）。
        // 需要注册表做「武器是否还附有摄魂术」判定；玩家缺席（JEI/创造搜索渲染）时退回 tooltip 上下文。
        ElementDamage lens = WeaknessLensPhoto.inspectActive(stack, registryAccess(event)).element();
        if ((levels == null || levels.isEmpty()) && lens == null) return;

        // 按元素顺序添加 tooltip（等级取 max(物品自身活性, 照片提供的 2 级活性)）
        for (ElementDamage element : ElementDamage.values()) {
            if (element == ElementDamage.PROJECTILE) continue;
            int level = WeaknessLensPhoto.getActivityLevel(stack, itemId, element);
            if (level > 0) {
                String elementKey = "element.lensouls." + element.getSerializedName() + ".short";
                event.getToolTip().add(Component.translatable("item.lensouls.element_activity_tooltip",
                        Component.translatable(elementKey),
                        String.valueOf(level)).copy().withStyle(ChatFormatting.GREEN));
            }
        }
    }

    private static net.minecraft.core.RegistryAccess registryAccess(ItemTooltipEvent event) {
        var player = event.getEntity();
        if (player != null) return player.registryAccess();
        var provider = event.getContext().registries();
        return provider instanceof net.minecraft.core.RegistryAccess access ? access : null;
    }
}
