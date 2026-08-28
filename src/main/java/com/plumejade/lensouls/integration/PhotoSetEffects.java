package com.plumejade.lensouls.integration;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.config.PhotoSetDefs;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.integration.PhotoSpecialEffects;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 照片套装运行时：每 tick 结算激活套装并施加属性/环境/免疫，命中和受击时结算抑制与增减伤，死亡时图腾复活。
 * <p>全部效果由数据包（photo_set / photo_set_defs）描述，本类只提供可复用机制。
 */
public class PhotoSetEffects {

    private static final String FLAGS = "lensouls:set_flags";
    private static final String ONHIT = "lensouls:set_onhit";
    private static final String DEATH = "lensouls:set_death";

    private static final Map<UUID, Set<ResourceLocation>> APPLIED = new ConcurrentHashMap<>();

    private static final ResourceLocation ID_MAXHP = ResourceLocation.fromNamespaceAndPath("lensouls", "set_maxhp");
    private static final ResourceLocation ID_SPEED = ResourceLocation.fromNamespaceAndPath("lensouls", "set_speed");
    private static final ResourceLocation ID_ARMOR = ResourceLocation.fromNamespaceAndPath("lensouls", "set_armor");
    private static final ResourceLocation ID_KB = ResourceLocation.fromNamespaceAndPath("lensouls", "set_kb");

    private static final List<Holder<Attribute>> SET_ATTRS = List.of(
            net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
            net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
            net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
            net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);

    /** 缓存结构（参照 L2Artifacts：重算仅在装备变动时发生，不在每 tick 重解析） */
    private record ElementActivity(ElementDamage el, int lvl) {}
    private record Plan(List<PhotoSetDefs.Tier> tiers) {}
    private record GearCache(long tick, List<String> gear) {}
    private record PlanCache(long tick, String sig, Plan plan) {}

    private static final Map<UUID, GearCache> GEAR_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, PlanCache> PLAN_CACHE = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        applyPlan(player, getPlan(player));
    }

    /** 本 tick 的装备实体集合：同 tick 内复用，避免每次伤害事件都重算 */
    private static List<String> getGear(ServerPlayer player) {
        long t = player.level().getGameTime();
        GearCache c = GEAR_CACHE.get(player.getUUID());
        if (c != null && c.tick == t) return c.gear;
        List<String> gear = PhotoSpecialEffects.collectGearEntities(player);
        GEAR_CACHE.put(player.getUUID(), new GearCache(t, gear));
        return gear;
    }

    /** 取激活套装计划；仅当装备签名变化时重新解析描述符 */
    private static Plan getPlan(ServerPlayer player) {
        long t = player.level().getGameTime();
        List<String> gear = getGear(player);
        String sig = String.join(",", new TreeSet<>(gear));
        PlanCache c = PLAN_CACHE.get(player.getUUID());
        if (c != null && c.sig.equals(sig)) {
            if (c.tick != t) PLAN_CACHE.put(player.getUUID(), new PlanCache(t, sig, c.plan));
            return c.plan;
        }
        int bossCount = PhotoSpecialEffects.countBossPhotos(player);
        Plan plan = buildPlan(player, gear, bossCount);
        PLAN_CACHE.put(player.getUUID(), new PlanCache(t, sig, plan));
        return plan;
    }

    private static Plan buildPlan(ServerPlayer player, List<String> gear, int bossCount) {
        List<PhotoSetDefs.Tier> tiers = PhotoSetRegistry.getActiveTiers(player, gear, bossCount);
        return new Plan(tiers);
    }

    private static void applyPlan(ServerPlayer player, Plan plan) {
        Set<ResourceLocation> desired = new HashSet<>();
        double maxhp = 0, speed = 0, armor = 0, kb = 0;
        List<ElementActivity> elems = new ArrayList<>();
        Set<String> envs = new HashSet<>();
        int deathCharges = 0, deathCd = 0;
        Set<String> onHit = new HashSet<>();
        CompoundTag flags = new CompoundTag();
        int infusionBoost = 0;
        List<String> converts = new ArrayList<>();

        for (PhotoSetDefs.Tier tier : plan.tiers()) {
            if (tier.when() != null && !condMet(tier.when(), player)) continue;
            for (String eff : tier.effects()) {
                String cond = null;
                String inner = eff;
                if (eff.startsWith("cond:")) {
                    int bar = eff.indexOf('|');
                    if (bar < 0) continue;
                    cond = eff.substring(5, bar);
                    inner = eff.substring(bar + 1);
                }
                if (cond != null && !condMet(cond, player)) continue;
                String[] p = inner.split(":");
                if (p.length == 0) continue;
                try {
                    switch (p[0]) {
                        case "maxhp" -> maxhp += Double.parseDouble(p[1]);
                        case "speed" -> speed += Double.parseDouble(p[1]);
                        case "armor" -> armor += Double.parseDouble(p[1]);
                        case "kb_resist" -> kb += Double.parseDouble(p[1]);
                        case "elem_activity" -> {
                            ElementDamage el = ElementDamage.byName(p[1]);
                            int lvl = Integer.parseInt(p[2]);
                            elems.add(new ElementActivity(el, lvl));
                        }
                        case "immune" -> flags.putBoolean("immune_" + p[1], true);
                        case "env" -> {
                            envs.add(p[1]);
                            flags.putBoolean("env_" + p[1], true);
                        }
                        case "death_revive" -> {
                            deathCharges = Math.max(deathCharges, Integer.parseInt(p[1]));
                            deathCd = Math.max(deathCd, Integer.parseInt(p[2]));
                        }
                        case "dodge" -> flags.putFloat("dodge", Math.max(flags.getFloat("dodge"), Float.parseFloat(p[1])));
                        case "on_hit_effect", "on_hit_suppress" -> onHit.add(inner);
                        case "dmg_mod" -> flags.putString("dmg_mod_" + p[1], p[2]);
                        case "dmg_taken" -> flags.putString("dmg_taken_" + p[1], p[2]);
                        case "infusion_boost" -> infusionBoost += Integer.parseInt(p[1]);
                        case "convert_eff" -> converts.add("minecraft:" + p[1] + ":" + p[2]);
                        case "barrage_trigger" -> flags.putInt("barrage_trigger", Math.max(flags.getInt("barrage_trigger"), Integer.parseInt(p[1])));
                        case "barrage_dmg" -> flags.putFloat("barrage_dmg", Math.max(flags.getFloat("barrage_dmg"), Float.parseFloat(p[1])));
                        default -> LenSouls.LOGGER.warn("[PhotoSet] 套装定义含未知效果类型 '{}': {}", p[0], inner);
                    }
                } catch (Exception ex) {
                    LenSouls.LOGGER.warn("[PhotoSet] 忽略无效效果描述符 '{}': {}", inner, ex.getMessage());
                }
            }
        }

        applyAttr(player, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, ID_MAXHP, maxhp, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, desired);
        applyAttr(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, ID_SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, desired);
        applyAttr(player, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, ID_ARMOR, armor, AttributeModifier.Operation.ADD_VALUE, desired);
        applyAttr(player, net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, ID_KB, kb, AttributeModifier.Operation.ADD_VALUE, desired);
        reconcile(player, desired);

        for (ElementActivity ea : elems) applyInfusion(player, ea.el(), ea.lvl() + infusionBoost);
        for (String env : envs) {
            if (!"flight".equals(env)) applyEnv(player, env);
        }

        boolean wantFly = envs.contains("flight");
        boolean hadFly = player.getPersistentData().getBoolean("lensouls:set_flight");
        if (wantFly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        } else if (hadFly) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        player.getPersistentData().putBoolean("lensouls:set_flight", wantFly);

        ListTag conv = new ListTag();
        for (String s : converts) conv.add(StringTag.valueOf(s));
        flags.put("convert", conv);
        player.getPersistentData().put(FLAGS, flags);

        CompoundTag onhit = new CompoundTag();
        ListTag list = new ListTag();
        for (String s : onHit) list.add(StringTag.valueOf(s));
        onhit.put("list", list);
        player.getPersistentData().put(ONHIT, onhit);

        // 不死图腾：可循环充能（cd 过后回满，无 cd 则常满）
        CompoundTag dr = player.getPersistentData().getCompound(DEATH);
        if (deathCharges <= 0) {
            dr.putInt("max", 0);
            dr.putInt("charges", 0);
        } else {
            int max = deathCharges;
            int charges = dr.contains("charges") ? dr.getInt("charges") : 0;
            int curMax = dr.contains("max") ? dr.getInt("max") : 0;
            long last = dr.contains("last") ? dr.getLong("last") : 0;
            long now = player.level().getGameTime();
            if (max != curMax) {
                dr.putInt("max", max);
                charges = max;
            } else if (charges < max && (deathCd == 0 || (last != 0 && now - last >= deathCd))) {
                charges = max;
            }
            dr.putInt("charges", charges);
        }
        dr.putInt("cd", deathCd);
        player.getPersistentData().put(DEATH, dr);
    }

    /** 条件求值：night/day/water/fire/flying/armor_gt:N/hp_gt:N/has_eff:<id> */
    private static boolean condMet(String c, ServerPlayer p) {
        String[] kv = c.split(":");
        switch (kv[0]) {
            case "night" -> { long t = p.level().getDayTime() % 24000; return t >= 12542 && t <= 23458; }
            case "day" -> { long t = p.level().getDayTime() % 24000; return t < 12542 || t > 23458; }
            case "water" -> { return p.isEyeInFluid(FluidTags.WATER) || p.isInWater(); }
            case "fire" -> { return p.isOnFire(); }
            case "flying" -> { return p.getAbilities().flying; }
            case "armor_gt" -> { return kv.length > 1 && p.getArmorValue() > Integer.parseInt(kv[1]); }
            case "hp_gt" -> { return kv.length > 1 && p.getHealth() > Double.parseDouble(kv[1]); }
            case "has_eff" -> {
                if (kv.length <= 1) return false;
                ResourceLocation rl = ResourceLocation.tryParse(kv[1]);
                if (rl == null) return false;
                var holder = BuiltInRegistries.MOB_EFFECT.getHolder(rl);
                return holder.isPresent() && p.hasEffect(holder.get());
            }
            default -> { return false; }
        }
    }

    private static void applyAttr(Player player, Holder<Attribute> attr, ResourceLocation id, double value,
                                   AttributeModifier.Operation op, Set<ResourceLocation> desired) {
        if (value == 0) return;
        AttributeInstance ai = player.getAttribute(attr);
        if (ai == null) return;
        ai.addOrUpdateTransientModifier(new AttributeModifier(id, value, op));
        desired.add(id);
    }

    private static void reconcile(Player player, Set<ResourceLocation> desired) {
        Set<ResourceLocation> prev = APPLIED.getOrDefault(player.getUUID(), Set.of());
        if (!prev.isEmpty()) {
            for (ResourceLocation id : prev) {
                if (!desired.contains(id)) {
                    for (Holder<Attribute> a : SET_ATTRS) {
                        AttributeInstance ai = player.getAttribute(a);
                        if (ai != null) ai.removeModifier(id);
                    }
                }
            }
        }
        APPLIED.put(player.getUUID(), desired);
    }

    private static void applyInfusion(Player player, ElementDamage el, int lvl) {
        Holder<MobEffect> effect = switch (el) {
            case FIRE -> ModEffects.FIRE_INFUSION;
            case WATER -> ModEffects.WATER_INFUSION;
            case EARTH -> ModEffects.EARTH_INFUSION;
            case ENDER -> ModEffects.ENDER_INFUSION;
            default -> null;
        };
        if (effect == null) return;
        int amp = Math.max(0, lvl - 1);
        var inst = player.getEffect(effect);
        if (inst == null || inst.getDuration() < 100 || inst.getAmplifier() != amp) {
            player.addEffect(new MobEffectInstance(effect, 300, amp, false, false, true));
        }
    }

    private static void applyEnv(Player player, String kind) {
        switch (kind) {
            case "water_breath" -> refreshEffect(player, MobEffects.WATER_BREATHING, 0);
            case "swim" -> refreshEffect(player, MobEffects.DOLPHINS_GRACE, 0);
            case "dark_invis" -> refreshEffect(player, MobEffects.INVISIBILITY, 0);
        }
    }

    private static void refreshEffect(Player player, Holder<MobEffect> effect, int amp) {
        var inst = player.getEffect(effect);
        if (inst == null || inst.getDuration() < 100) {
            player.addEffect(new MobEffectInstance(effect, 300, amp, false, false, true));
        }
    }

    // ========== 伤害事件 ==========

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;

        // ── 玩家造成伤害 ──
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            LivingEntity target = event.getEntity();
            List<String> gear = getGear(player);

            applyGenericSuppress(player, target, gear);
            applySetOnHit(player, target);
            applySetDealtDamage(player, target, event);
        }

        // ── 玩家受到伤害 ──
        if (event.getEntity() instanceof ServerPlayer player) {
            applySetTakenDamage(player, event);
        }
    }

    /** 通用元素抑制（组件驱动）：按去重装备实体统计各元素等级，玩家攻击时每种元素独立 3% 平行掷骰施加抑制 */
    private static void applyGenericSuppress(ServerPlayer player, LivingEntity target, List<String> gear) {
        Map<ElementDamage, Integer> levels = PhotographEffectRegistry.countElementLevels(gear);
        if (levels.isEmpty()) return;
        for (Map.Entry<ElementDamage, Integer> e : levels.entrySet()) {
            if (player.level().random.nextFloat() >= 0.03f) continue;
            Holder<MobEffect> sup = switch (e.getKey()) {
                case FIRE -> ModEffects.SUPPRESS_FIRE;
                case WATER -> ModEffects.SUPPRESS_WATER;
                case EARTH -> ModEffects.SUPPRESS_EARTH;
                case ENDER -> ModEffects.SUPPRESS_ENDER;
                default -> null;
            };
            if (sup == null) continue;
            target.addEffect(new MobEffectInstance(sup, 100, Math.max(0, e.getValue() - 1), false, true, true));
        }
    }

    private static void applySetOnHit(ServerPlayer player, LivingEntity target) {
        CompoundTag onhit = player.getPersistentData().getCompound(ONHIT);
        if (!onhit.contains("list", Tag.TAG_LIST)) return;
        ListTag list = onhit.getList("list", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String[] p = list.getString(i).split(":");
            if (p.length < 1) continue;
            if ("on_hit_effect".equals(p[0]) && p.length >= 5) {
                float prob = Float.parseFloat(p[2]);
                if (player.level().random.nextFloat() >= prob) continue;
                Holder<MobEffect> effect = onHitEffect(p[1]);
                if (effect == null) continue;
                int dur = Integer.parseInt(p[3]);
                int amp = Integer.parseInt(p[4]);
                target.addEffect(new MobEffectInstance(effect, dur, amp, false, false, true));
            } else if ("on_hit_suppress".equals(p[0]) && p.length >= 4) {
                float prob = Float.parseFloat(p[2]);
                if (player.level().random.nextFloat() >= prob) continue;
                ElementDamage el = ElementDamage.byName(p[1]);
                Holder<MobEffect> sup = switch (el) {
                    case FIRE -> ModEffects.SUPPRESS_FIRE;
                    case WATER -> ModEffects.SUPPRESS_WATER;
                    case EARTH -> ModEffects.SUPPRESS_EARTH;
                    case ENDER -> ModEffects.SUPPRESS_ENDER;
                    default -> null;
                };
                if (sup == null) continue;
                int dur = Integer.parseInt(p[3]);
                target.addEffect(new MobEffectInstance(sup, dur, 0, false, true, true));
            }
        }
    }

    private static Holder<MobEffect> onHitEffect(String name) {
        return switch (name) {
            case "slow" -> MobEffects.MOVEMENT_SLOWDOWN;
            case "poison" -> MobEffects.POISON;
            case "wither" -> MobEffects.WITHER;
            case "weak" -> MobEffects.WEAKNESS;
            case "blind" -> MobEffects.BLINDNESS;
            case "freeze" -> MobEffects.MOVEMENT_SLOWDOWN;
            default -> null;
        };
    }

    private static void applySetDealtDamage(ServerPlayer player, LivingEntity target, LivingDamageEvent.Pre event) {
        CompoundTag flags = player.getPersistentData().getCompound(FLAGS);
        for (String key : flags.getAllKeys()) {
            if (!key.startsWith("dmg_mod_")) continue;
            String setId = key.substring("dmg_mod_".length());
            if (setId.equals("boss_barrage")) {
                if (target.getMaxHealth() < 200.0f) continue;
            } else if (!PhotoSetRegistry.isInSet(target, setId)) {
                continue;
            }
            float mult = Float.parseFloat(flags.getString(key));
            event.setNewDamage(event.getNewDamage() * mult);
        }
    }

    private static void applySetTakenDamage(ServerPlayer player, LivingDamageEvent.Pre event) {
        CompoundTag flags = player.getPersistentData().getCompound(FLAGS);

        // 闪避
        if (flags.contains("dodge")) {
            float dodge = flags.getFloat("dodge");
            if (player.level().random.nextFloat() < dodge) {
                event.setNewDamage(0f);
                return;
            }
        }

        var src = event.getSource();
        if (flags.getBoolean("immune_explosion") && src.is(DamageTypeTags.IS_EXPLOSION)) {
            event.setNewDamage(0f);
            return;
        }
        if (flags.getBoolean("immune_projectile") && src.is(DamageTypeTags.IS_PROJECTILE)) {
            event.setNewDamage(0f);
            return;
        }
        if (flags.getBoolean("immune_fall") && src.is(DamageTypes.FALL)) {
            event.setNewDamage(0f);
            return;
        }
        if (flags.getBoolean("immune_fire") && (src.is(DamageTypes.ON_FIRE) || src.is(DamageTypes.IN_FIRE))) {
            event.setNewDamage(0f);
            return;
        }

        for (String key : flags.getAllKeys()) {
            if (!key.startsWith("dmg_taken_")) continue;
            String setId = key.substring("dmg_taken_".length());
            if (setId.equals("boss_barrage")) {
                if (event.getEntity().getMaxHealth() < 200.0f) continue;
            } else if (!PhotoSetRegistry.isInSet(event.getEntity(), setId)) {
                continue;
            }
            float mult = Float.parseFloat(flags.getString(key));
            event.setNewDamage(event.getNewDamage() * mult);
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var inst = event.getEffectInstance();
        if (inst == null) return;
        String effectId = BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value()).toString();
        CompoundTag flags = player.getPersistentData().getCompound(FLAGS);
        if (flags.getBoolean("immune_poison") && effectId.equals("minecraft:poison")) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        } else if (flags.getBoolean("immune_wither") && effectId.equals("minecraft:wither")) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (flags.contains("convert", Tag.TAG_LIST)) {
            ListTag conv = flags.getList("convert", Tag.TAG_STRING);
            for (int i = 0; i < conv.size(); i++) {
                String[] kv = conv.getString(i).split(":");
                if (kv.length < 2) continue;
                if (kv[0].equals(effectId)) {
                    Holder<MobEffect> to = convEffect(kv[1]);
                    if (to != null) {
                        int amp = inst.getAmplifier();
                        int dur = inst.getDuration();
                        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                        player.addEffect(new MobEffectInstance(to, dur, amp, inst.isAmbient(), inst.isVisible(), inst.showIcon()));
                    }
                    break;
                }
            }
        }
    }

    /** 效果名 → MobEffect（用于 convert_eff 目标） */
    private static Holder<MobEffect> convEffect(String name) {
        return switch (name) {
            case "healing" -> MobEffects.REGENERATION;
            case "speed" -> MobEffects.MOVEMENT_SPEED;
            case "strength" -> MobEffects.DAMAGE_BOOST;
            case "fire_resist" -> MobEffects.FIRE_RESISTANCE;
            case "invis" -> MobEffects.INVISIBILITY;
            case "jump" -> MobEffects.JUMP;
            case "night_vision" -> MobEffects.NIGHT_VISION;
            case "resistance" -> MobEffects.DAMAGE_RESISTANCE;
            case "water_breath" -> MobEffects.WATER_BREATHING;
            case "haste" -> MobEffects.DIG_SPEED;
            case "slow" -> MobEffects.MOVEMENT_SLOWDOWN;
            case "weak" -> MobEffects.WEAKNESS;
            case "blind" -> MobEffects.BLINDNESS;
            case "poison" -> MobEffects.POISON;
            case "wither" -> MobEffects.WITHER;
            default -> null;
        };
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator()) return;
        CompoundTag dr = player.getPersistentData().getCompound(DEATH);
        int max = dr.contains("max") ? dr.getInt("max") : 0;
        int charges = dr.contains("charges") ? dr.getInt("charges") : 0;
        int cd = dr.contains("cd") ? dr.getInt("cd") : 0;
        if (max <= 0 || charges <= 0) return;

        long last = dr.contains("last") ? dr.getLong("last") : 0;
        long now = player.level().getGameTime();
        if (last != 0 && now - last < cd) return;

        dr.putInt("charges", charges - 1);
        dr.putLong("last", now);
        player.getPersistentData().put(DEATH, dr);

        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        event.setCanceled(true);
    }
}

