package com.plumejade.lensouls.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 照片角标图标解析：根据 lensouls:stolen_entity 解析出对应生物的小图标（刷怪蛋/代表物/怪物笼）。
 * <p>
 * 解析顺序完全复刻 FTB-Quests 击杀任务图标逻辑：
 * 先查该实体是否注册了刷怪蛋（带原版 ItemColors 着色），否则用实体自身的 getPickResult() 代表物，最后兜底怪物笼。
 */
public class PhotoBadgeIcon {

    private static final Map<ResourceLocation, ItemStack> CACHE = new HashMap<>();

    /** 需要角标的能力照片：贴图直接用对应能力球物品（{@code textures/item/skill_ball/<id>.png}） */
    private static final java.util.Set<String> ABILITY_BADGES =
            java.util.Set.of("weakness_lens", "spatial_warp", "temporal_recall");

    /**
     * 空间扭曲 / 时空回溯照片的角标：返回对应能力球物品（渲染出来就是那张能力球贴图）。
     * 其它能力、空 id、物品不存在都返回 null（不画角标）。
     */
    @Nullable
    public static ItemStack getAbilityBadge(@Nullable String abilityId) {
        if (abilityId == null || !ABILITY_BADGES.contains(abilityId)) return null;
        com.plumejade.lensouls.ability.AbilityType type =
                com.plumejade.lensouls.ability.AbilityType.byId(abilityId);
        if (type == null) return null;
        Item item = BuiltInRegistries.ITEM.get(type.getIconItemId());
        if (item == Items.AIR) return null;
        return new ItemStack(item);
    }

    @Nullable
    public static ItemStack getEggStack(@Nullable ResourceLocation entityId) {
        if (entityId == null) return null;
        ItemStack cached = CACHE.get(entityId);
        if (cached != null) return cached;
        ItemStack result = compute(entityId);
        CACHE.put(entityId, result);
        return result;
    }

    private static ItemStack compute(ResourceLocation id) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        if (type == null || type == EntityType.PLAYER) {
            return new ItemStack(Items.SPAWNER);
        }
        Item egg = SpawnEggItem.byId(type);
        if (egg != null) return new ItemStack(egg);
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            Entity entity = type.create(level);
            if (entity != null) {
                ItemStack pick = entity.getPickResult();
                if (pick != null && !pick.isEmpty()) return pick;
            }
        }
        return new ItemStack(Items.SPAWNER);
    }
}
