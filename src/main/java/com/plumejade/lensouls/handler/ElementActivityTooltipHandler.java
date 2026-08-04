package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.config.ItemElementActivityLoader;
import com.plumejade.lensouls.damage.ElementDamage;
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
 * 如果物品在 {@code item_element_activity} 数据包中配置了元素活性，
 * 在 tooltip 末尾添加一行绿色提示，格式如 "§a火 II"。
 * 支持 {@code /reload} 热重载。
 */
public class ElementActivityTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // 从注册名查活性配置
        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Map<ElementDamage, Float> levels = ItemElementActivityLoader.getLevels(itemId);
        if (levels == null || levels.isEmpty()) return;

        // 按元素顺序添加 tooltip
        for (ElementDamage element : ElementDamage.values()) {
            if (element == ElementDamage.PROJECTILE) continue;
            Float level = levels.get(element);
            if (level != null && level > 0f) {
                String elementKey = "element.lensouls." + element.getSerializedName() + ".short";
                event.getToolTip().add(Component.translatable("item.lensouls.element_activity_tooltip",
                        Component.translatable(elementKey),
                        formatLevel(level)).copy().withStyle(ChatFormatting.GREEN));
            }
        }
    }

    /** 等级显示：1~5 用罗马数字，其余（0.5 步进/超 5）显示数值 */
    private static String formatLevel(float level) {
        if (level == Math.floor(level)) {
            int n = (int) level;
            if (n >= 1 && n <= 5) {
                return switch (n) {
                    case 1 -> "I";
                    case 2 -> "II";
                    case 3 -> "III";
                    case 4 -> "IV";
                    case 5 -> "V";
                    default -> String.valueOf(n);
                };
            }
        }
        if (level == Math.floor(level)) return String.valueOf((int) level);
        return String.valueOf(level);
    }
}
