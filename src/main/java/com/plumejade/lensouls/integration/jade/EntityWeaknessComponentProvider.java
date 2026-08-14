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
        Map<ElementDamage, Integer> levels = AttackerElementLoader.getLevels(entityId);
        boolean hasActivity = false;
        MutableComponent atkLine = Component.translatable("jade.lensouls.activity");
        for (ElementDamage element : ElementDamage.values()) {
            Integer level = levels.get(element);
            if (level == null || level <= 0) continue;
            if (hasActivity) atkLine = atkLine.append("、");
            hasActivity = true;
            atkLine = atkLine.append(Component.translatable(
                    "element.lensouls." + element.getSerializedName() + ".short"));
            atkLine = atkLine.append(" ").append(String.valueOf(level));
        }
        if (hasActivity) {
            tooltip.add(atkLine.copy().withStyle(ChatFormatting.GREEN));
        }

        // 韧性：生物韧性 x/x（服务端同步的 requiredHits + progress）
        com.plumejade.lensouls.boss.ToughnessEntry toughness =
                com.plumejade.lensouls.boss.BossToughnessClientCache.find(entity.getId());
        if (toughness != null && toughness.requiredHits() > 0) {
            // progress = 已削韧比例（0=满，1=破防），显示剩余韧性（初始 x/x，逐次降低）
            int removed = Math.min(toughness.requiredHits(),
                    Math.round(toughness.progress() * toughness.requiredHits()));
            int current = toughness.requiredHits() - removed;
            tooltip.add(Component.translatable("jade.lensouls.toughness",
                    current, toughness.requiredHits()).withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
