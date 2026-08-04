package com.plumejade.lensouls.integration.jade;

import com.plumejade.lensouls.config.AttackerElementLoader;
import com.plumejade.lensouls.config.DataPackLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Jade 实体弱点信息提供器。
 * <p>
 * 格式：弱点：火, 水, 末影, 弹射物
 * 所有弱点显示在同一行，逗号分隔，绿色文字。
 * 仅显示在 {@code entity_weakness} 数据包中显式配置了弱点的实体。
 */
public enum EntityWeaknessComponentProvider implements IEntityComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("lensouls", "entity_weakness");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        Entity entity = accessor.getEntity();
        if (!(entity instanceof LivingEntity)) return;

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Map<ElementDamage, Float> weaknesses = DataPackLoader.getAllWeaknesses(entityId);

        if (weaknesses.isEmpty()) return;

        // 按元素顺序收集名称，包含 projectile
        List<Component> names = new ArrayList<>();
        for (ElementDamage element : ElementDamage.values()) {
            if (!weaknesses.containsKey(element)) continue;
            String shortKey = "element.lensouls." + element.getSerializedName() + ".short";
            names.add(Component.translatable(shortKey));
        }

        if (names.isEmpty()) return;

        // 弱点：火, 水, 末影, 弹射物
        MutableComponent line = Component.translatable("jade.lensouls.weakness");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) line = line.append("、");
            line = line.append(names.get(i));
        }
        tooltip.add(line.copy().withStyle(ChatFormatting.GREEN));

        // 活性：实体固有元素（来自 attacker_element 数据包，多元素逐一显示 + 等级）
        Map<ElementDamage, Float> levels = AttackerElementLoader.getLevels(entityId);
        boolean hasActivity = false;
        MutableComponent atkLine = Component.translatable("jade.lensouls.activity");
        for (ElementDamage element : ElementDamage.values()) {
            Float level = levels.get(element);
            if (level == null || level <= 0f) continue;
            if (hasActivity) atkLine = atkLine.append("、");
            hasActivity = true;
            atkLine = atkLine.append(Component.translatable(
                    "element.lensouls." + element.getSerializedName() + ".short"));
            atkLine = atkLine.append(" ").append(formatLevel(level));
        }
        if (hasActivity) {
            tooltip.add(atkLine.copy().withStyle(ChatFormatting.GREEN));
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
            return String.valueOf(n);
        }
        return String.valueOf(level);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
