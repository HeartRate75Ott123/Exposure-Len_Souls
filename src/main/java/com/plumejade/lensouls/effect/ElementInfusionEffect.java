package com.plumejade.lensouls.effect;

import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.particle.ModParticleTypes;
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
 * 元素附魔效果模板。
 * <p>
 * 每个元素对应一个效果实例，作为标记附着在玩家身上供 {@link com.plumejade.lensouls.damage.DamageHandler} 检测。
 * 不渲染图标、不产生粒子、不在 HUD 显示。
 * <p>
 * 通过 {@link #getDamageMultiplier(int)} 支持效果等级（amplifier）影响伤害倍率：
 * amplifier 0（I级）→ 基础元素弱点倍率；
 * amplifier 1（II级）→ 基础倍率 × 2。
 * amplifier N → (N+1).0x
 * <p>
 * 物品倍率（如 BOSS 镜魂自带的 x1.2/x1.5/x2）通过 {@link #setPlayerData} 存储，
 * 由 {@link #getPlayerMultiplier} 读取，在 {@link com.plumejade.lensouls.handler.DamageHandler} 中
 * 与元素弱点倍率叠乘。数据在效果过期时自动清理。
 */
public class ElementInfusionEffect extends MobEffect {

    private final ElementDamage element;

    /** 玩家 UUID → 是否攻击附带减速（云筑魔像专属） */
    private static final Map<UUID, Boolean> playerSlowness = new ConcurrentHashMap<>();
    /** 玩家 UUID → 元素类型 → 物品 descriptionId（BOSS 镜魂自定义名，如 item.lensouls.ignis_soul） */
    private static final Map<UUID, Map<ElementDamage, String>> playerCustomNames = new ConcurrentHashMap<>();

    /**
     * @param element 对应元素类型
     * @param color   粒子色值，设为 0 以完全隐藏
     */
    public ElementInfusionEffect(ElementDamage element, int color) {
        super(MobEffectCategory.BENEFICIAL, color);
        this.element = element;
    }

    /** 返回效果绑定的元素类型 */
    public ElementDamage getElement() {
        return element;
    }

    /**
     * 根据效果 amplifier 返回物品倍率系数（编码在 amplifier 中，利用原版高等级不被低等级覆盖的特性）。
     * <p>
     * amplifier 0 → ×1.0（基础镜魂）<br>
     * amplifier 1 → ×1.2（云筑魔像）<br>
     * amplifier 2 → ×1.5（堕落圣骑、末影守卫）<br>
     * amplifier 3 → ×2.0（焰魔、湮灭构造体、下界合金巨兽）
     */
    public float getDamageMultiplier(int amplifier) {
        return switch (amplifier) {
            case 0 -> 1.0f;
            case 1 -> 1.2f;
            case 2 -> 1.5f;
            case 3 -> 2.0f;
            default -> 1.0f + amplifier * 0.5f;
        };
    }

    // ========== 减速标记 / 自定义名称 ==========

    /**
     * 记录该玩家本次激活的镜魂特效标记（减速 + 按元素区分的自定义显示名）。
     * 同时写入玩家持久 NBT 以防登出重进后丢失。
     * <p>
     * <strong>注意：</strong>始终覆盖写入，不依赖 {@code effectChanged} 守卫。
     * 同一元素的两个 BOSS 镜魂会正确覆盖旧的 descriptionId。
     *
     * @param player     玩家
     * @param element    元素类型（用于区分不同元素的自定义名）
     * @param slowness   是否攻击减速
     * @param customName 物品 descriptionId（如 {@code item.lensouls.ignis_soul}），
     *                   null 或空字符串则清除该元素的自定义名，回退到元素通用名
     */
    public static void setPlayerData(Player player, ElementDamage element, boolean slowness, String customName) {
        UUID uuid = player.getUUID();
        if (slowness) {
            playerSlowness.put(uuid, true);
        } else {
            playerSlowness.remove(uuid);
        }
        Map<ElementDamage, String> map = playerCustomNames.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        if (customName != null && !customName.isEmpty()) {
            map.put(element, customName);
        } else {
            map.remove(element);
        }
        saveCustomNames(player);
    }

    /** 获取该玩家指定元素的自定义名称（如 item.lensouls.ignis_soul），没有则返回 null */
    public static String getPlayerCustomName(Player player, ElementDamage element) {
        UUID uuid = player.getUUID();
        Map<ElementDamage, String> map = playerCustomNames.get(uuid);
        String name = map != null ? map.get(element) : null;
        if (name != null) return name;
        // 从持久 NBT 恢复（登出重进后内存 Map 丢失）
        loadCustomNames(player);
        map = playerCustomNames.get(uuid);
        name = map != null ? map.get(element) : null;
        if (name != null) return name;
        // 内存和 NBT 都无记录 → 检查玩家是否仍有该元素效果，防止残留旧名称
        if (map != null && map.containsKey(element)) {
            boolean hasEffect = player.hasEffect(getEffectForElement(element));
            if (!hasEffect) {
                map.remove(element);
                saveCustomNames(player);
            }
        }
        return null;
    }

    /** 根据元素类型获取对应的环境粒子类型 */
    private net.minecraft.core.particles.SimpleParticleType getElementParticleType() {
        return switch (element) {
            case FIRE -> ModParticleTypes.ELEMENT_PARTICLE_FIRE.get();
            case WATER -> ModParticleTypes.ELEMENT_PARTICLE_WATER.get();
            case EARTH -> ModParticleTypes.ELEMENT_PARTICLE_EARTH.get();
            case ENDER -> ModParticleTypes.ELEMENT_PARTICLE_ENDER.get();
            case PROJECTILE -> ModParticleTypes.ELEMENT_PARTICLE_FIRE.get();
        };
    }

    /** 根据元素类型获取对应的效果 Holder */
    private static Holder<net.minecraft.world.effect.MobEffect> getEffectForElement(ElementDamage element) {
        return switch (element) {
            case FIRE -> ModEffects.FIRE_INFUSION;
            case WATER -> ModEffects.WATER_INFUSION;
            case EARTH -> ModEffects.EARTH_INFUSION;
            case ENDER -> ModEffects.ENDER_INFUSION;
            case PROJECTILE -> ModEffects.FIRE_INFUSION;
        };
    }

    // ========== 持久化工具 ==========

    private static void saveCustomNames(Player player) {
        UUID uuid = player.getUUID();
        Map<ElementDamage, String> map = playerCustomNames.get(uuid);
        CompoundTag tag = new CompoundTag();
        if (map != null) {
            for (Map.Entry<ElementDamage, String> entry : map.entrySet()) {
                tag.putString(entry.getKey().getSerializedName(), entry.getValue());
            }
        }
        player.getPersistentData().put("lensouls_custom_names", tag);
    }

    private static void loadCustomNames(Player player) {
        UUID uuid = player.getUUID();
        if (!player.getPersistentData().contains("lensouls_custom_names", net.minecraft.nbt.Tag.TAG_COMPOUND)) return;
        CompoundTag loaded = player.getPersistentData().getCompound("lensouls_custom_names");
        for (String key : loaded.getAllKeys()) {
            ElementDamage element = ElementDamage.byName(key);
            if (element != null) {
                playerCustomNames.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                        .put(element, loaded.getString(key));
            }
        }
    }

    /**
     * 检测该玩家激活的镜魂是否附带减速效果（云筑魔像）。
     */
    public static boolean hasPlayerSlowness(Player player) {
        return playerSlowness.getOrDefault(player.getUUID(), false);
    }

    /** 清理该玩家的全部临时数据 */
    public static void cleanupPlayer(Player player) {
        UUID uuid = player.getUUID();
        playerSlowness.remove(uuid);
        playerCustomNames.remove(uuid);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // 每 8 ticks 粒子（客户端），60/40/20 到期提醒（服务端）
        return duration % 8 == 0 || (duration <= 60 && duration % 20 == 0);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) {
            // 客户端：环绕玩家缓慢漂浮的元素粒子
            if (entity.level().random.nextInt(3) == 0) {
                double x = entity.getX() + (entity.level().random.nextDouble() - 0.5) * 2.0;
                double z = entity.getZ() + (entity.level().random.nextDouble() - 0.5) * 2.0;
                double y = entity.getY() + entity.getBbHeight() * 0.3
                        + entity.level().random.nextDouble() * entity.getBbHeight() * 0.6;
                entity.level().addParticle(getElementParticleType(),
                        x, y, z,
                        (entity.level().random.nextDouble() - 0.5) * 0.02,
                        0.02 + entity.level().random.nextDouble() * 0.03,
                        (entity.level().random.nextDouble() - 0.5) * 0.02);
            }
            return true;
        }

        // 服务端：仅 60/40/20 时弹出期提醒
        if (!(entity instanceof Player player)) return true;
        // 该 tick 如果不是 60/40/20（到期提醒 tick）则跳过
        int duration = 0;
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect().value() == this) {
                duration = inst.getDuration();
                break;
            }
        }
        if (duration <= 60 && duration % 20 == 0) {
            String customKey = getPlayerCustomName(player, this.element);
            Component soulName = customKey != null
                    ? Component.translatable(customKey)
                    : Component.translatable("element.lensouls." + element.getSerializedName());
            int remaining = duration / 20;
            player.displayClientMessage(
                    Component.translatable("message.lensouls.effect_expiring", soulName, remaining), true);
        }
        return true;
    }
}
