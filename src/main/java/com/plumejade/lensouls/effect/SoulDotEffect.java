package com.plumejade.lensouls.effect;

import com.plumejade.lensouls.damage.ElementDamage;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 镜魂 DoT 增益效果（镜魂右键专属，与照片活性使用的 {@link ElementInfusionEffect} 彻底分离）。
 * <p>
 * 每个元素对应一个效果实例，HUD 显示图标（textures/mob_effect/&lt;name&gt;.png）。
 * 增益期间玩家攻击命中会由 {@code SoulDotHandler} 附加持续伤害：
 * 火元素→火焰 DoT、水元素→冰冻 DoT、土元素→中毒 DoT、末影元素→凋零 DoT。
 * <p>
 * 效果携带三类附加数据（仿 {@link ElementInfusionEffect} 的静态 Map + 玩家 NBT 持久化）：
 * <ul>
 *   <li>镜魂 descriptionId（非空 = BOSS 镜魂，描边判定与 DoT ×2 的唯一依据；普通镜魂存 null）</li>
 *   <li>是否攻击附带减速（云筑魔像专属）</li>
 * </ul>
 */
public class SoulDotEffect extends MobEffect {

    private final ElementDamage element;

    /** 玩家 UUID → 是否攻击附带减速（云筑魔像专属） */
    private static final Map<UUID, Boolean> playerSlowness = new ConcurrentHashMap<>();
    /** 玩家 UUID → 元素类型 → 镜魂 descriptionId（普通镜魂不写，null/缺失 = 非BOSS） */
    private static final Map<UUID, Map<ElementDamage, String>> playerSoulIds = new ConcurrentHashMap<>();

    public SoulDotEffect(ElementDamage element, int color) {
        super(MobEffectCategory.BENEFICIAL, color);
        this.element = element;
    }

    /** 返回效果绑定的元素类型 */
    public ElementDamage getElement() {
        return element;
    }

    /** 根据元素类型获取对应的镜魂 DoT 效果 Holder */
    public static Holder<MobEffect> getEffectForElement(ElementDamage element) {
        return switch (element) {
            case FIRE -> ModEffects.FIRE_DOT;
            case WATER -> ModEffects.WATER_DOT;
            case EARTH -> ModEffects.EARTH_DOT;
            case ENDER -> ModEffects.ENDER_DOT;
            case PROJECTILE -> ModEffects.FIRE_DOT;
        };
    }

    // ========== 玩家附加数据 ==========

    /**
     * 记录该玩家本次激活的镜魂数据（减速标记 + 镜魂 descriptionId）。
     * 同时写入玩家持久 NBT 以防登出重进后丢失。
     *
     * @param soulDescId BOSS 镜魂的 descriptionId（如 {@code item.lensouls.ignis_soul}），
     *                   null/空 = 普通镜魂（清除该元素记录）
     */
    public static void setPlayerData(Player player, ElementDamage element, boolean slowness, String soulDescId) {
        UUID uuid = player.getUUID();
        if (slowness) {
            playerSlowness.put(uuid, true);
        } else {
            playerSlowness.remove(uuid);
        }
        Map<ElementDamage, String> map = playerSoulIds.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        if (soulDescId != null && !soulDescId.isEmpty()) {
            map.put(element, soulDescId);
        } else {
            map.remove(element);
        }
        saveSoulIds(player);
    }

    /** 获取该玩家指定元素激活的镜魂 descriptionId（非空 = BOSS 镜魂激活中）。 */
    public static String getPlayerSoulId(LivingEntity entity, ElementDamage element) {
        if (!(entity instanceof Player player)) return null;
        UUID uuid = player.getUUID();
        Map<ElementDamage, String> map = playerSoulIds.get(uuid);
        String id = map != null ? map.get(element) : null;
        if (id != null) return id;
        // 从持久 NBT 恢复（登出重进后内存 Map 丢失）
        loadSoulIds(player);
        map = playerSoulIds.get(uuid);
        id = map != null ? map.get(element) : null;
        if (id != null) return id;
        // 内存和 NBT 都无记录 → 玩家已无该元素效果时清除残留
        if (map != null && map.containsKey(element) && !player.hasEffect(getEffectForElement(element))) {
            map.remove(element);
            saveSoulIds(player);
        }
        return null;
    }

    /** 检测该玩家激活的镜魂是否附带减速效果（云筑魔像）。 */
    public static boolean hasPlayerSlowness(Player player) {
        return playerSlowness.getOrDefault(player.getUUID(), false);
    }

    /** 清理该玩家的全部临时数据 */
    public static void cleanupPlayer(Player player) {
        UUID uuid = player.getUUID();
        playerSlowness.remove(uuid);
        playerSoulIds.remove(uuid);
    }

    // ========== 持久化 ==========

    private static void saveSoulIds(Player player) {
        UUID uuid = player.getUUID();
        Map<ElementDamage, String> map = playerSoulIds.get(uuid);
        CompoundTag tag = new CompoundTag();
        if (map != null) {
            for (Map.Entry<ElementDamage, String> entry : map.entrySet()) {
                tag.putString(entry.getKey().getSerializedName(), entry.getValue());
            }
        }
        player.getPersistentData().put("lensouls_soul_dot_ids", tag);
    }

    private static void loadSoulIds(Player player) {
        UUID uuid = player.getUUID();
        if (!player.getPersistentData().contains("lensouls_soul_dot_ids", net.minecraft.nbt.Tag.TAG_COMPOUND)) return;
        CompoundTag loaded = player.getPersistentData().getCompound("lensouls_soul_dot_ids");
        for (String key : loaded.getAllKeys()) {
            ElementDamage element = ElementDamage.byName(key);
            if (element != null) {
                playerSoulIds.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                        .put(element, loaded.getString(key));
            }
        }
    }

    // ========== 效果 tick ==========

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // 仅服务端 60/40/20 到期提醒
        return duration <= 60 && duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || !(entity instanceof Player player)) return true;
        // 60/40/20 tick 到期提醒（显示镜魂名，普通镜魂显示元素名）
        int duration = 0;
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect().value() == this) {
                duration = inst.getDuration();
                break;
            }
        }
        if (duration <= 60 && duration % 20 == 0) {
            String soulId = getPlayerSoulId(player, this.element);
            Component soulName = soulId != null
                    ? Component.translatable(soulId)
                    : Component.translatable("element.lensouls." + element.getSerializedName());
            int remaining = duration / 20;
            player.displayClientMessage(
                    Component.translatable("message.lensouls.effect_expiring", soulName, remaining), true);
        }
        return true;
    }
}
