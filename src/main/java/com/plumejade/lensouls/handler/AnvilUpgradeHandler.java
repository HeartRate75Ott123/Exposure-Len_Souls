package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.item.LensoulItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 镜魂铁砧合成处理器。
 * <p>
 * 两个同元素同等级镜魂 → 下一等级。
 * 需要消耗经验，成品无冷却。
 * 通过 {@link net.neoforged.neoforge.event.AnvilUpdateEvent} 驱动。
 */
public class AnvilUpgradeHandler {

    /** 升级消耗经验: index=等级, value=经验等级消耗 */
    private static final int[] XP_COST = {0, 0, 5, 10, 15, 20};

    @SubscribeEvent
    public static void onAnvilUpdate(net.neoforged.neoforge.event.AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        // 两个物品必须都是镜魂
        if (!(left.getItem() instanceof LensoulItem leftSoul)
                || !(right.getItem() instanceof LensoulItem rightSoul)) return;

        // 必须同元素
        if (leftSoul.getElement() != rightSoul.getElement()) return;

        // 读取当前等级
        int leftLevel = getSoulLevel(left);
        int rightLevel = getSoulLevel(right);
        if (leftLevel != rightLevel) return;
        if (leftLevel <= 0 || leftLevel >= 5) return; // 1~4 → 可升到下一级

        int nextLevel = leftLevel + 1;
        int xpCost = XP_COST[nextLevel];

        // 设置输出
        ItemStack output = left.copy();
        output.setCount(1);
        setSoulLevel(output, nextLevel);

        // 清除冷却数据（成品无冷却）
        CompoundTag tag = output.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove("SoulCooldownEnd");
        tag.remove("SoulCooldownDur");
        output.set(DataComponents.CUSTOM_DATA, tag.isEmpty() ? CustomData.EMPTY : CustomData.of(tag));

        // 更新显示名
        int finalLevel = nextLevel;
        output.set(DataComponents.CUSTOM_NAME,
                Component.translatable(leftSoul.getDescriptionId())
                        .append(Component.literal(" §7[" + toRoman(finalLevel) + "]")));

        event.setOutput(output);
        event.setCost(xpCost);
        event.setMaterialCost(1);
    }

    /** 从 ItemStack 读取镜魂等级（默认 1） */
    public static int getSoulLevel(ItemStack stack) {
        if (!(stack.getItem() instanceof LensoulItem soul)) return 1;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            if (tag.contains("SoulLevel")) {
                return tag.getInt("SoulLevel");
            }
        }
        // 根据 damageMultiplier 推算初始等级
        float mult = soul.getDamageMultiplier();
        if (mult >= 2.0f) return 4;
        if (mult >= 1.5f) return 3;
        if (mult >= 1.2f) return 2;
        return 1;
    }

    /** 写入镜魂等级 */
    public static void setSoulLevel(ItemStack stack, int level) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt("SoulLevel", level);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 根据 ItemStack 获取 amplifier（用于效果等级），考虑升级等级 */
    public static int getAmplifier(ItemStack stack) {
        int level = getSoulLevel(stack);
        return level - 1;  // level 1→amp 0, level 2→amp 1, ...
    }

    private static String toRoman(int n) {
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
