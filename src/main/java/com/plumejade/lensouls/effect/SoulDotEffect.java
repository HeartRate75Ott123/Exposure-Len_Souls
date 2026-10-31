package com.plumejade.lensouls.effect;

import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.particle.ModParticleTypes;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
 * 镜魂 DoT 增益效果（镜魂右键专属，与照片饰品/套装的元素活性 {@link ElementInfusionEffect} 彻底分离）。
 * <p>
 * 每个元素对应一个效果实例，HUD 显示图标（textures/mob_effect/&lt;name&gt;.png）。
 * 增益期间玩家攻击命中会由 {@code SoulDotHandler} 附加持续伤害：
 * 火元素→火焰 DoT、水元素→冰冻 DoT、土元素→中毒 DoT、末影元素→凋零 DoT。
 * <p>
 * <b>到期提示只在这里发</b>：效果剩 60/40/20 tick 时提示「XX镜魂 还有 N 秒结束！」。
 * 名称按<b>实际来源</b>区分（记录在 {@link #PLAYER_SOURCES}）：
 * <ul>
 *   <li>BOSS 镜魂（如焰魔镜魂）→ 显示该镜魂的物品名（{@code item.lensouls.ignis_soul}）；</li>
 *   <li>基础镜魂 → 显示元素通用名（{@code element.lensouls.fire} = 火焰镜魂）。</li>
 * </ul>
 * 同一元素只保留「真正在生效的那一颗镜魂」的来源：低等级激活不会冲掉高等级 BOSS 镜魂的来源标记
 * （旧实现是「后用的镜魂无条件覆盖」，导致焰魔 DoT 还在身上、先用一颗基础火焰镜魂就把提示冲成
 * 「火焰镜魂」）。额外数据（是否攻击附带减速）同样按元素记录，互不干扰。
 */
public class SoulDotEffect extends MobEffect {

    private final ElementDamage element;

    /** 单个元素的 DoT 来源数据（BOSS 镜魂 descId / 云筑魔像减速标记） */
    public record SoulSource(String soulDescId, boolean slowness) {
    }

    /** 玩家 UUID → 元素类型 → 来源数据（基础镜魂 descId 为 null） */
    private static final Map<UUID, Map<ElementDamage, SoulSource>> PLAYER_SOURCES = new ConcurrentHashMap<>();

    private static final String NBT_IDS = "lensouls_soul_dot_ids";
    private static final String NBT_SLOW = "lensouls_soul_dot_slow";

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

    // ========== 施加效果（唯一入口） ==========

    /**
     * 施加镜魂 DoT 增益并登记来源。所有「基础镜魂 / BOSS 模组未加载」的施加入口都应走这里，
     * 不要自己 addEffect + setPlayerData——那会在低等级镜魂无法覆盖现有效果时错误改写来源。
     *
     * @param soulDescId BOSS 镜魂的 descriptionId（基础镜魂传 null）
     * @return true = 效果已施加/刷新；false = 已有更高等级的同类 DoT，本次激活不改变效果与来源
     */
    public static boolean applySoulEffect(Player player, Holder<MobEffect> effect, ElementDamage element,
                                          int amplifier, int durationTicks, boolean slowness, String soulDescId) {
        MobEffectInstance current = player.getEffect(effect);
        // 已有更高等级的同类 DoT：原版不会覆盖，来源也不该被冲掉
        if (current != null && current.getAmplifier() > amplifier) return false;

        player.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, false, false, true));

        // 基础镜魂（descId == null）不夺走同级/更高等级 BOSS 镜魂的来源标记：
        // 例如焰魔镜魂（默认 V 级 = amp 4）还在生效时再点一颗基础/暮色镜魂，提示仍应是焰魔镜魂。
        boolean keepBossSource = soulDescId == null && current != null
                && current.getAmplifier() >= amplifier
                && getPlayerSoulId(player, element) != null;
        if (!keepBossSource) {
            setPlayerData(player, element, slowness, soulDescId);
        }
        return true;
    }

    // ========== 玩家附加数据 ==========

    /**
     * 记录该玩家该元素本次激活的镜魂来源（减速标记 + 镜魂 descriptionId）。
     * 同时写入玩家持久 NBT 以防登出重进后丢失。
     *
     * @param soulDescId BOSS 镜魂的 descriptionId（如 {@code item.lensouls.ignis_soul}），
     *                   null/空 = 普通镜魂（清除该元素的 BOSS 来源）
     */
    public static void setPlayerData(Player player, ElementDamage element, boolean slowness, String soulDescId) {
        UUID uuid = player.getUUID();
        Map<ElementDamage, SoulSource> map = PLAYER_SOURCES.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        boolean boss = soulDescId != null && !soulDescId.isEmpty();
        if (!boss && !slowness) {
            map.remove(element);
        } else {
            map.put(element, new SoulSource(boss ? soulDescId : null, slowness));
        }
        saveSources(player);
    }

    /**
     * 获取该玩家指定元素激活的镜魂 descriptionId（非空 = BOSS 镜魂激活中）。
     * <p>
     * 判定以「该元素 DoT 效果此刻是否在身上」为准：效果不在 → 来源记录（含 NBT 残留）一律作废，
     * 避免登出/到期后旧镜魂名继续被当成当前来源（会误导提示，也会让基础镜魂白吃 DoT ×2）。
     */
    public static String getPlayerSoulId(LivingEntity entity, ElementDamage element) {
        if (!(entity instanceof Player player)) return null;
        SoulSource src = activeSource(player, element);
        return src != null ? src.soulDescId() : null;
    }

    /** 检测该玩家激活的镜魂是否附带减速效果（云筑魔像）。同样只认仍在生效的 DoT。 */
    public static boolean hasPlayerSlowness(Player player) {
        SoulSource water = activeSource(player, ElementDamage.WATER);
        if (water != null && water.slowness()) return true;
        // 兜底：其它元素上的减速标记也算（未来若有非水元素的减速镜魂）
        for (ElementDamage el : ElementDamage.values()) {
            if (el == ElementDamage.WATER || el == ElementDamage.PROJECTILE) continue;
            SoulSource src = activeSource(player, el);
            if (src != null && src.slowness()) return true;
        }
        return false;
    }

    /** 清理该玩家的全部临时数据（登录时调用；仍有 DoT 的元素会在下次读取时从 NBT 恢复） */
    public static void cleanupPlayer(Player player) {
        PLAYER_SOURCES.remove(player.getUUID());
    }

    /** 该实体是否激活着指定元素的镜魂 DoT 增益（DamageHandler 用于抑制该元素的 up 螺旋） */
    public static boolean hasActiveDot(LivingEntity entity, ElementDamage element) {
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect().value() instanceof SoulDotEffect effect && effect.getElement() == element) {
                return true;
            }
        }
        return false;
    }

    /** 读取该元素来源（只认仍在生效的 DoT；效果已消失则顺手清掉记录，含 NBT 残留）。 */
    private static SoulSource activeSource(Player player, ElementDamage element) {
        if (!player.hasEffect(getEffectForElement(element))) {
            clearSource(player, element);
            return null;
        }
        Map<ElementDamage, SoulSource> map = sourceMap(player, true);
        return map != null ? map.get(element) : null;
    }

    private static void clearSource(Player player, ElementDamage element) {
        Map<ElementDamage, SoulSource> map = PLAYER_SOURCES.get(player.getUUID());
        if (map != null && map.containsKey(element)) {
            map.remove(element);
            if (map.isEmpty()) PLAYER_SOURCES.remove(player.getUUID());
            saveSources(player);
        }
    }

    private static Map<ElementDamage, SoulSource> sourceMap(Player player, boolean loadIfMissing) {
        UUID uuid = player.getUUID();
        Map<ElementDamage, SoulSource> map = PLAYER_SOURCES.get(uuid);
        if ((map == null || map.isEmpty()) && loadIfMissing) {
            loadSources(player);
            map = PLAYER_SOURCES.get(uuid);
        }
        return map;
    }

    // ========== 持久化 ==========

    private static void saveSources(Player player) {
        UUID uuid = player.getUUID();
        Map<ElementDamage, SoulSource> map = PLAYER_SOURCES.get(uuid);
        CompoundTag ids = new CompoundTag();
        ListTag slow = new ListTag();
        if (map != null) {
            for (Map.Entry<ElementDamage, SoulSource> entry : map.entrySet()) {
                SoulSource src = entry.getValue();
                if (src.soulDescId() != null) {
                    ids.putString(entry.getKey().getSerializedName(), src.soulDescId());
                }
                if (src.slowness()) {
                    slow.add(net.minecraft.nbt.StringTag.valueOf(entry.getKey().getSerializedName()));
                }
            }
        }
        CompoundTag root = player.getPersistentData();
        root.put(NBT_IDS, ids);
        root.put(NBT_SLOW, slow);
    }

    private static void loadSources(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(NBT_IDS, Tag.TAG_COMPOUND) && !root.contains(NBT_SLOW, Tag.TAG_LIST)) return;
        UUID uuid = player.getUUID();
        Map<ElementDamage, SoulSource> map = PLAYER_SOURCES.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        CompoundTag ids = root.getCompound(NBT_IDS);
        for (String key : ids.getAllKeys()) {
            ElementDamage element = ElementDamage.byName(key);
            if (element != null) {
                map.merge(element, new SoulSource(ids.getString(key), false),
                        (old, loaded) -> new SoulSource(loaded.soulDescId(), old.slowness()));
            }
        }
        ListTag slow = root.getList(NBT_SLOW, Tag.TAG_STRING);
        for (int i = 0; i < slow.size(); i++) {
            ElementDamage element = ElementDamage.byName(slow.getString(i));
            if (element != null) {
                map.merge(element, new SoulSource(null, true),
                        (old, loaded) -> new SoulSource(old.soulDescId(), true));
            }
        }
    }

    // ========== 效果 tick ==========

    /** 原版环绕粒子（与元素灌注同款）：按元素输出 swirl 粒子 */
    @Override
    public net.minecraft.core.particles.ParticleOptions createParticleOptions(MobEffectInstance instance) {
        return getElementParticleType();
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

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // 每 8 ticks 漂浮粒子（客户端），60/40/20 到期提醒（服务端）
        return duration % 8 == 0 || (duration <= 60 && duration % 20 == 0);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) {
            // 客户端：环绕玩家缓慢漂浮的元素粒子（效果实例 visible=false，原版发射器不跑，只能自己补）。
            // 初速按 ElementParticle 的原版口径给：**横向传 0**（provider 会把横向初速再收 10 倍＝温和漂移），
            // 纵向给 0.1~0.25 是因为 ElementParticle 内部会 ×0.2，算回去正好是旧的 0.02~0.05。
            if (entity.level().random.nextInt(3) == 0) {
                double x = entity.getX() + (entity.level().random.nextDouble() - 0.5) * 2.0;
                double z = entity.getZ() + (entity.level().random.nextDouble() - 0.5) * 2.0;
                double y = entity.getY() + entity.getBbHeight() * 0.3
                        + entity.level().random.nextDouble() * entity.getBbHeight() * 0.6;
                entity.level().addParticle(getElementParticleType(),
                        x, y, z,
                        0.0,
                        0.1 + entity.level().random.nextDouble() * 0.15,
                        0.0);
            }
            return true;
        }

        // 服务端：仅 60/40/20 tick 到期提醒（BOSS 镜魂显示具体镜魂名，基础镜魂显示元素名）
        if (!(entity instanceof Player player)) return true;
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
