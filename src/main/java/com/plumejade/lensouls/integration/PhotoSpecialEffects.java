package com.plumejade.lensouls.integration;

import com.plumejade.lensouls.attribute.ModAttributes;
import com.plumejade.lensouls.config.DataPackLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.item.PhotoAlbumItem;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ICuriosMenu;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.*;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 照片饰品运行逻辑层。与 {@link PhotographEffectRegistry} 描述一一对应：
 * 属性修饰符 / 伤害减免与元素弱点 / 玩家造成增伤(kind) / 免疫与机制 / Curios 照片槽位扩展。
 */
public class PhotoSpecialEffects {

    private static final Set<String> FLYING_ENTITIES = Set.of(
            "twilightforest:ur_ghast", "twilightforest:snow_queen",
            "minecraft:bat", "minecraft:ender_dragon", "minecraft:wither", "minecraft:phantom"
    );
    private static final String FLIGHT_TAG = "lensouls:flight_photo";
    private static final ResourceLocation MOD_BASE = ResourceLocation.parse("lensouls:attr_");
    /** 玩家已集中施加的照片属性修饰符（按 UUID 追踪，用于精准回收） */
    private static final Map<UUID, Multimap<Holder<Attribute>, AttributeModifier>> APPLIED_ATTRS = new ConcurrentHashMap<>();

    /** 弱属性照片组 → 额外照片栏位数（越弱给越多，1~3，无上限叠加） */
    public static final Map<String, Integer> WEAK_SLOT_BONUS = Map.ofEntries(
            Map.entry("minecraft:chicken", 3),
            Map.entry("minecraft:sheep", 3),
            Map.entry("minecraft:bat", 3),
            Map.entry("minecraft:cod", 3),
            Map.entry("minecraft:salmon", 3),
            Map.entry("minecraft:tropical_fish", 3),
            Map.entry("minecraft:tadpole", 3),
            Map.entry("minecraft:squid", 2),
            Map.entry("minecraft:glow_squid", 2),
            Map.entry("minecraft:frog", 2),
            Map.entry("minecraft:bee", 2),
            Map.entry("minecraft:rabbit", 1),
            Map.entry("minecraft:parrot", 1),
            Map.entry("minecraft:ocelot", 1)
    );

    public static boolean isWeakSlotPhoto(String entityId) {
        return WEAK_SLOT_BONUS.containsKey(entityId);
    }

    public static int getWeakSlotBonus(String entityId) {
        return WEAK_SLOT_BONUS.getOrDefault(entityId, 0);
    }
    private static final String SLOT_MOD_ID = "lensouls:photo_slot_bonus";
    private static final String SLOT_TAG = "lensouls:extra_photo_slots";

    // ── 收到的伤害减免/元素弱点：kind 或 DamageType 匹配 → multiplier（>1 为弱点） ──
    private static final Map<String, List<DamageRule>> DAMAGE_RULES = new HashMap<>();
    private record DamageRule(Predicate<LivingDamageEvent.Pre> matcher, float multiplier) {}

    private static void addRule(String id, DamageRule rule) {
        DAMAGE_RULES.computeIfAbsent(id, k -> new ArrayList<>()).add(rule);
    }

    private static boolean isMelee(LivingDamageEvent.Pre e) {
        DamageSource src = e.getSource();
        return src.getEntity() instanceof LivingEntity && src.isDirect() && !src.is(DamageTypeTags.IS_PROJECTILE);
    }
    private static boolean isRanged(LivingDamageEvent.Pre e) {
        DamageSource src = e.getSource();
        return src.getEntity() instanceof LivingEntity && (!src.isDirect() || src.is(DamageTypeTags.IS_PROJECTILE));
    }
    private static boolean isMagic(LivingDamageEvent.Pre e) {
        DamageSource src = e.getSource();
        return src.is(DamageTypes.MAGIC) || src.is(DamageTypes.INDIRECT_MAGIC)
                || src.is(Tags.DamageTypes.IS_MAGIC);
    }

    static {
        // ── Boss 减伤 ──
        addRule("cataclysm:ender_guardian", new DamageRule(e -> true, 0.85f));
        addRule("cataclysm:netherite_monstrosity", new DamageRule(PhotoSpecialEffects::isMelee, 0.88f));
        addRule("cataclysm:the_harbinger", new DamageRule(e -> e.getSource().is(DamageTypes.WITHER) || e.getSource().is(DamageTypes.WITHER_SKULL), 0.5f));
        addRule("cataclysm:ancient_remnant", new DamageRule(PhotoSpecialEffects::isMelee, 0.88f));
        addRule("cataclysm:maledictus", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        addRule("cataclysm:scylla", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        addRule("legendary_monsters:posessed_paladin", new DamageRule(PhotoSpecialEffects::isMelee, 0.88f));
        addRule("legendary_monsters:cloud_golem", new DamageRule(e -> true, 1.12f));
        // ── 原版 ──
        addRule("minecraft:creeper", new DamageRule(e -> e.getSource().is(DamageTypes.EXPLOSION) || e.getSource().is(DamageTypes.PLAYER_EXPLOSION), 0.8f));
        addRule("minecraft:ghast", new DamageRule(e -> e.getSource().is(DamageTypes.EXPLOSION) || e.getSource().is(DamageTypes.PLAYER_EXPLOSION), 0.8f));
        addRule("minecraft:wither", new DamageRule(e -> e.getSource().is(DamageTypes.WITHER) || e.getSource().is(DamageTypes.WITHER_SKULL), 0.05f));
        addRule("minecraft:ender_dragon", new DamageRule(e -> e.getSource().is(DamageTypes.FALL), 0.05f));
        addRule("minecraft:snow_golem", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("minecraft:stray", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("minecraft:slime", new DamageRule(e -> e.getSource().is(DamageTypes.FALL), 0.05f));
        addRule("minecraft:chicken", new DamageRule(e -> e.getSource().is(DamageTypes.FALL), 0.05f));
        addRule("minecraft:parrot", new DamageRule(e -> e.getSource().is(DamageTypes.FALL), 0.05f));
        addRule("minecraft:phantom", new DamageRule(e -> e.getSource().is(DamageTypes.FALL), 0.05f));
        addRule("minecraft:magma_cube", new DamageRule(e -> e.getSource().is(DamageTypes.FALL), 0.05f));
        addRule("minecraft:iron_golem", new DamageRule(e -> e.getSource().is(DamageTypeTags.IS_PROJECTILE), 0.0f));
        addRule("minecraft:skeletosaurus", new DamageRule(e -> e.getSource().is(DamageTypeTags.IS_PROJECTILE), 0.0f));
        addRule("minecraft:enderman", new DamageRule(e -> e.getSource().is(DamageTypes.MAGIC) || e.getSource().is(DamageTypes.INDIRECT_MAGIC), 1.12f));
        addRule("minecraft:endermite", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        addRule("minecraft:witch", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        addRule("minecraft:cave_spider", new DamageRule(PhotoSpecialEffects::isMelee, 1.05f));
        addRule("minecraft:spider", new DamageRule(PhotoSpecialEffects::isMelee, 1.05f));
        addRule("minecraft:silverfish", new DamageRule(PhotoSpecialEffects::isMelee, 1.05f));
        addRule("minecraft:snow_golem", new DamageRule(e -> e.getSource().is(DamageTypes.IN_FIRE) || e.getSource().is(DamageTypes.ON_FIRE) || e.getSource().is(DamageTypes.LAVA) || e.getSource().is(DamageTypes.FIREBALL), 1.12f));
        addRule("minecraft:ghast", new DamageRule(e -> e.getSource().is(DamageTypes.IN_FIRE) || e.getSource().is(DamageTypes.ON_FIRE) || e.getSource().is(DamageTypes.LAVA) || e.getSource().is(DamageTypes.FIREBALL), 1.12f));
        addRule("minecraft:fox", new DamageRule(e -> true, 1.05f));
        // ── 暮色 ──
        addRule("twilightforest:hydra", new DamageRule(e -> e.getSource().is(DamageTypeTags.IS_PROJECTILE), 0.88f));
        addRule("twilightforest:knight_phantom", new DamageRule(e -> e.getSource().is(DamageTypes.WITHER) || e.getSource().is(DamageTypes.WITHER_SKULL), 0.75f));
        addRule("twilightforest:alpha_yeti", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("twilightforest:yeti", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("twilightforest:ice_crystal", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("twilightforest:stable_ice_core", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("twilightforest:unstable_ice_core", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("twilightforest:snow_guardian", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("twilightforest:snow_queen", new DamageRule(e -> e.getSource().is(DamageTypes.IN_FIRE) || e.getSource().is(DamageTypes.ON_FIRE) || e.getSource().is(DamageTypes.LAVA) || e.getSource().is(DamageTypes.FIREBALL), 1.12f));
        addRule("twilightforest:fire_beetle", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 1.12f));
        addRule("twilightforest:maze_slime", new DamageRule(e -> e.getSource().is(DamageTypes.FALL), 0.05f));
        addRule("twilightforest:carminite_broodling", new DamageRule(e -> e.getSource().is(DamageTypes.FALL), 0.05f));
        addRule("twilightforest:carminite_ghastling", new DamageRule(e -> e.getSource().is(DamageTypes.FALL), 0.05f));
        addRule("twilightforest:tiny_bird", new DamageRule(e -> e.getSource().is(DamageTypes.FALL), 0.05f));
        addRule("twilightforest:carminite_ghastguard", new DamageRule(e -> e.getSource().is(DamageTypes.EXPLOSION) || e.getSource().is(DamageTypes.PLAYER_EXPLOSION), 0.0f));
        addRule("twilightforest:carminite_golem", new DamageRule(e -> e.getSource().is(DamageTypes.EXPLOSION) || e.getSource().is(DamageTypes.PLAYER_EXPLOSION), 0.0f));
        addRule("twilightforest:lich", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        // ── 灾变 ──
        addRule("cataclysm:ignis", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 1.12f));
        addRule("cataclysm:ignited_revenant", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 1.12f));
        addRule("cataclysm:ignited_berserker", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 1.12f));
        addRule("cataclysm:ender_golem", new DamageRule(e -> e.getSource().is(DamageTypeTags.IS_PROJECTILE), 0.0f));
        addRule("cataclysm:draugr", new DamageRule(e -> e.getSource().is(DamageTypes.WITHER) || e.getSource().is(DamageTypes.WITHER_SKULL), 0.05f));
        addRule("cataclysm:elite_draugr", new DamageRule(e -> e.getSource().is(DamageTypes.WITHER) || e.getSource().is(DamageTypes.WITHER_SKULL), 0.05f));
        addRule("cataclysm:royal_draugr", new DamageRule(e -> e.getSource().is(DamageTypes.WITHER) || e.getSource().is(DamageTypes.WITHER_SKULL), 0.05f));
        addRule("cataclysm:the_prowler", new DamageRule(PhotoSpecialEffects::isRanged, 0.88f));
        addRule("cataclysm:deepling_priest", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        addRule("cataclysm:endermaptera", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        addRule("cataclysm:wadjet", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        addRule("cataclysm:lionfish", new DamageRule(PhotoSpecialEffects::isMelee, 1.05f));
        addRule("cataclysm:urchinkin", new DamageRule(PhotoSpecialEffects::isMelee, 1.05f));
        // ── 传奇怪物 ──
        addRule("legendary_monsters:the_obliterator", new DamageRule(e -> true, 0.9f));
        addRule("legendary_monsters:endersent", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        addRule("legendary_monsters:ancient_guardian", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        addRule("legendary_monsters:annihilation_pursuer", new DamageRule(PhotoSpecialEffects::isMelee, 0.88f));
        addRule("legendary_monsters:skeletosaurus", new DamageRule(e -> e.getSource().is(DamageTypeTags.IS_PROJECTILE), 0.0f));
        addRule("legendary_monsters:frostbitten_golem", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("legendary_monsters:frostbitten_golem", new DamageRule(e -> e.getSource().is(DamageTypes.IN_FIRE) || e.getSource().is(DamageTypes.ON_FIRE) || e.getSource().is(DamageTypes.LAVA) || e.getSource().is(DamageTypes.FIREBALL), 1.12f));
        addRule("legendary_monsters:flame_drifter", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 1.12f));
        addRule("legendary_monsters:flameborn_guard", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 1.12f));
        addRule("legendary_monsters:flameborn_warrior", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 1.12f));
        addRule("legendary_monsters:lava_eater", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 1.12f));
        addRule("legendary_monsters:bomber", new DamageRule(e -> e.getSource().is(DamageTypes.EXPLOSION) || e.getSource().is(DamageTypes.PLAYER_EXPLOSION), 0.0f));
        addRule("legendary_monsters:chorusling", new DamageRule(PhotoSpecialEffects::isMagic, 1.12f));
        // ── 永恒星光 ──
        addRule("eternal_starlight:freeze", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("eternal_starlight:permafrost", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("eternal_starlight:yeti", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("eternal_starlight:lunar_monstrosity", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        addRule("eternal_starlight:creteor", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        addRule("eternal_starlight:tiny_creteor", new DamageRule(PhotoSpecialEffects::isMagic, 0.88f));
        addRule("eternal_starlight:the_gatekeeper", new DamageRule(e -> true, 0.88f));
        addRule("eternal_starlight:solar_creeper", new DamageRule(e -> e.getSource().is(DamageTypes.EXPLOSION) || e.getSource().is(DamageTypes.PLAYER_EXPLOSION), 0.0f));
        // ── 首领崛起 ──
        addRule("block_factorys_bosses:yeti", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        addRule("block_factorys_bosses:underworld_knight", new DamageRule(e -> e.getSource().is(DamageTypes.WITHER) || e.getSource().is(DamageTypes.WITHER_SKULL), 0.05f));
        addRule("block_factorys_bosses:infernal_dragon", new DamageRule(e -> e.getSource().is(DamageTypeTags.IS_PROJECTILE), 0.0f));
        addRule("block_factorys_bosses:infernal_dragon", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 1.12f));
        addRule("block_factorys_bosses:flaming_skeleton_guard_fireball", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 1.12f));
        addRule("block_factorys_bosses:flaming_skeleton_guard_sword", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 1.12f));
        addRule("block_factorys_bosses:soul_knight_wither_skeleton", new DamageRule(e -> e.getSource().is(DamageTypes.WITHER) || e.getSource().is(DamageTypes.WITHER_SKULL), 0.05f));
        addRule("block_factorys_bosses:soul_skeleton", new DamageRule(e -> e.getSource().is(DamageTypes.WITHER) || e.getSource().is(DamageTypes.WITHER_SKULL), 0.05f));
    }

    // ── 属性修饰符（一 mob 可多属性） ──
    private static final Map<String, List<AttributeEntry>> ATTRIBUTES = new HashMap<>();
    private record AttributeEntry(Attribute attribute, String modName, double amount, AttributeModifier.Operation operation) {}

    private static Holder<Attribute> holder(Attribute a) {
        return BuiltInRegistries.ATTRIBUTE.wrapAsHolder(a);
    }
    private static void attr(String id, Attribute a, String name, double v, AttributeModifier.Operation op) {
        ATTRIBUTES.computeIfAbsent(id, k -> new ArrayList<>()).add(new AttributeEntry(a, name, v, op));
    }

    static {
        // ── Boss 被动属性 ──
        attr("cataclysm:ender_guardian", Attributes.MOVEMENT_SPEED.value(), "eg_speed", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:ender_guardian", Attributes.BLOCK_INTERACTION_RANGE.value(), "eg_block", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:ender_guardian", Attributes.ENTITY_INTERACTION_RANGE.value(), "eg_entity", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:ignis", Attributes.BURNING_TIME.value(), "ignis_burn", -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:netherite_monstrosity", Attributes.KNOCKBACK_RESISTANCE.value(), "nm_kb", 0.6, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:netherite_monstrosity", Attributes.ARMOR.value(), "nm_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:netherite_monstrosity", Attributes.MOVEMENT_SPEED.value(), "nm_speed", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:netherite_monstrosity", Attributes.JUMP_STRENGTH.value(), "nm_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:the_leviathan", NeoForgeMod.SWIM_SPEED.value(), "lev_swim", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:the_leviathan", Attributes.MOVEMENT_SPEED.value(), "lev_speed", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:ancient_remnant", Attributes.ARMOR.value(), "ar_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:ancient_remnant", Attributes.MOVEMENT_SPEED.value(), "ar_speed", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:maledictus", Attributes.ATTACK_SPEED.value(), "mal_aspd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:maledictus", Attributes.KNOCKBACK_RESISTANCE.value(), "mal_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:scylla", NeoForgeMod.SWIM_SPEED.value(), "scy_swim", 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:scylla", Attributes.JUMP_STRENGTH.value(), "scy_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:posessed_paladin", Attributes.ARMOR.value(), "pp_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:posessed_paladin", Attributes.KNOCKBACK_RESISTANCE.value(), "pp_kb", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:posessed_paladin", Attributes.MOVEMENT_SPEED.value(), "pp_speed", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:posessed_paladin", Attributes.JUMP_STRENGTH.value(), "pp_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:cloud_golem", Attributes.MOVEMENT_SPEED.value(), "cg_speed", 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:cloud_golem", Attributes.KNOCKBACK_RESISTANCE.value(), "cg_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);

        // ── 原版 战斗类 ──
        attr("minecraft:zombie", Attributes.MAX_HEALTH.value(), "zombie_photo", 6.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:zombie", Attributes.ATTACK_DAMAGE.value(), "zombie_atk", 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:zombie", Attributes.MOVEMENT_SPEED.value(), "zombie_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:husk", Attributes.ATTACK_DAMAGE.value(), "husk_atk", 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:husk", Attributes.MOVEMENT_SPEED.value(), "husk_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:drowned", Attributes.ATTACK_DAMAGE.value(), "drowned_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:drowned", Attributes.MOVEMENT_SPEED.value(), "drowned_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:skeleton", Attributes.ATTACK_SPEED.value(), "skeleton_aspd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:skeleton", Attributes.MAX_HEALTH.value(), "skeleton_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:bogged", Attributes.MAX_HEALTH.value(), "bogged_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:creeper", Attributes.ENTITY_INTERACTION_RANGE.value(), "creeper_range", -0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:endermite", Attributes.MOVEMENT_SPEED.value(), "endermite_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:endermite", Attributes.MAX_HEALTH.value(), "endermite_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:spider", Attributes.JUMP_STRENGTH.value(), "spider_photo", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:spider", Attributes.MOVEMENT_SPEED.value(), "spider_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:silverfish", Attributes.ATTACK_DAMAGE.value(), "sil_atk", 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:silverfish", Attributes.ATTACK_SPEED.value(), "sil_aspd", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:silverfish", Attributes.MOVEMENT_SPEED.value(), "sil_spd", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:silverfish", Attributes.MAX_HEALTH.value(), "sil_hp", -4.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:silverfish", Attributes.KNOCKBACK_RESISTANCE.value(), "sil_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:warden", Attributes.ATTACK_DAMAGE.value(), "warden_atk", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:warden", Attributes.KNOCKBACK_RESISTANCE.value(), "warden_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:warden", Attributes.ATTACK_KNOCKBACK.value(), "warden_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:warden", Attributes.MOVEMENT_SPEED.value(), "warden_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:warden", Attributes.JUMP_STRENGTH.value(), "warden_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:skeleton_horse", Attributes.MOVEMENT_SPEED.value(), "skh_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:skeleton_horse", Attributes.JUMP_STRENGTH.value(), "skh_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:zombie_horse", Attributes.MOVEMENT_SPEED.value(), "zomh_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:zombie_horse", Attributes.JUMP_STRENGTH.value(), "zomh_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:slime", Attributes.JUMP_STRENGTH.value(), "slime_photo", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:slime", Attributes.KNOCKBACK_RESISTANCE.value(), "slime_kb", -0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:slime", Attributes.ENTITY_INTERACTION_RANGE.value(), "slime_range", -0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:piglin", Attributes.ATTACK_DAMAGE.value(), "piglin_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:piglin_brute", Attributes.ATTACK_DAMAGE.value(), "pb_atk", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:piglin_brute", Attributes.KNOCKBACK_RESISTANCE.value(), "pb_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:piglin_brute", Attributes.ATTACK_KNOCKBACK.value(), "pb_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:piglin_brute", Attributes.MOVEMENT_SPEED.value(), "pb_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:piglin_brute", Attributes.JUMP_STRENGTH.value(), "pb_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:hoglin", Attributes.ATTACK_DAMAGE.value(), "hoglin_atk", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:hoglin", Attributes.ATTACK_KNOCKBACK.value(), "hoglin_knock", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:zoglin", Attributes.ATTACK_DAMAGE.value(), "zoglin_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:zoglin", Attributes.ATTACK_KNOCKBACK.value(), "zoglin_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:strider", Attributes.MOVEMENT_SPEED.value(), "strider_spd", 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:strider", Attributes.JUMP_STRENGTH.value(), "strider_jump", -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:strider", Attributes.ENTITY_INTERACTION_RANGE.value(), "strider_range", -0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:ravager", Attributes.ATTACK_DAMAGE.value(), "ravager_atk", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:ravager", Attributes.KNOCKBACK_RESISTANCE.value(), "ravager_kb", 0.8, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:ravager", Attributes.ATTACK_KNOCKBACK.value(), "ravager_knock", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:ravager", Attributes.MOVEMENT_SPEED.value(), "ravager_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:ravager", Attributes.JUMP_STRENGTH.value(), "ravager_jump", -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:pillager", Attributes.MAX_HEALTH.value(), "pillager_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:vex", Attributes.MOVEMENT_SPEED.value(), "vex_spd", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:vex", Attributes.ATTACK_DAMAGE.value(), "vex_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:vex", Attributes.ATTACK_SPEED.value(), "vex_aspd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:vex", Attributes.MAX_HEALTH.value(), "vex_hp", -4.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:vex", Attributes.KNOCKBACK_RESISTANCE.value(), "vex_kb", -0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:shulker", Attributes.ARMOR.value(), "shulker_armor", 6.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:shulker", Attributes.KNOCKBACK_RESISTANCE.value(), "shulker_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:shulker", Attributes.MOVEMENT_SPEED.value(), "shulker_spd", -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:shulker", Attributes.JUMP_STRENGTH.value(), "shulker_jump", -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:shulker", Attributes.BLOCK_INTERACTION_RANGE.value(), "shulker_block", -0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:shulker", Attributes.ENTITY_INTERACTION_RANGE.value(), "shulker_entity", -0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:elder_guardian", Attributes.MOVEMENT_SPEED.value(), "eg2_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // ── 原版 动物/中立 ──
        attr("minecraft:pig", Attributes.ATTACK_KNOCKBACK.value(), "pig_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:pig", Attributes.MOVEMENT_SPEED.value(), "pig_spd", -0.03, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:cow", Attributes.KNOCKBACK_RESISTANCE.value(), "cow_kb", 0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:cow", Attributes.ATTACK_KNOCKBACK.value(), "cow_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:mooshroom", Attributes.KNOCKBACK_RESISTANCE.value(), "moo_kb", 0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:mooshroom", Attributes.ATTACK_KNOCKBACK.value(), "moo_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:chicken", Attributes.JUMP_STRENGTH.value(), "chicken_jump", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:chicken", Attributes.KNOCKBACK_RESISTANCE.value(), "chicken_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:rabbit", Attributes.JUMP_STRENGTH.value(), "rabbit_jump", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:rabbit", Attributes.LUCK.value(), "rabbit_luck", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:rabbit", Attributes.MAX_HEALTH.value(), "rabbit_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:wolf", Attributes.ATTACK_DAMAGE.value(), "wolf_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:wolf", Attributes.MOVEMENT_SPEED.value(), "wolf_spd", 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:fox", Attributes.MOVEMENT_SPEED.value(), "fox_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:fox", Attributes.JUMP_STRENGTH.value(), "fox_jump", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:fox", Attributes.LUCK.value(), "fox_luck", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:cat", Attributes.JUMP_STRENGTH.value(), "cat_jump", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:cat", Attributes.LUCK.value(), "cat_luck", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:cat", Attributes.KNOCKBACK_RESISTANCE.value(), "cat_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:ocelot", Attributes.MOVEMENT_SPEED.value(), "ocelot_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:ocelot", Attributes.JUMP_STRENGTH.value(), "ocelot_jump", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:ocelot", Attributes.LUCK.value(), "ocelot_luck", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:ocelot", Attributes.MAX_HEALTH.value(), "ocelot_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:parrot", Attributes.LUCK.value(), "parrot_luck", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:parrot", Attributes.KNOCKBACK_RESISTANCE.value(), "parrot_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:bat", Attributes.KNOCKBACK_RESISTANCE.value(), "bat_kb", -0.4, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:bat", Attributes.MAX_HEALTH.value(), "bat_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:bee", Attributes.MOVEMENT_SPEED.value(), "bee_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:bee", Attributes.ATTACK_DAMAGE.value(), "bee_atk", 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:bee", Attributes.MAX_HEALTH.value(), "bee_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:allay", Attributes.LUCK.value(), "allay_luck", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:allay", Attributes.MAX_HEALTH.value(), "allay_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:armadillo", Attributes.ARMOR.value(), "arma_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:armadillo", Attributes.KNOCKBACK_RESISTANCE.value(), "arma_kb", 0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:armadillo", Attributes.MOVEMENT_SPEED.value(), "arma_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:armadillo", Attributes.BLOCK_INTERACTION_RANGE.value(), "arma_block", -0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:armadillo", Attributes.ENTITY_INTERACTION_RANGE.value(), "arma_entity", -0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:turtle", Attributes.ARMOR.value(), "turtle_armor", 4.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:turtle", Attributes.KNOCKBACK_RESISTANCE.value(), "turtle_kb", 0.4, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:turtle", Attributes.MOVEMENT_SPEED.value(), "turtle_spd", -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:turtle", Attributes.JUMP_STRENGTH.value(), "turtle_jump", -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:turtle", Attributes.BLOCK_INTERACTION_RANGE.value(), "turtle_block", -1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:turtle", Attributes.ENTITY_INTERACTION_RANGE.value(), "turtle_entity", -1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:axolotl", Attributes.MOVEMENT_SPEED.value(), "axo_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:dolphin", Attributes.LUCK.value(), "dolphin_luck", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:dolphin", Attributes.JUMP_STRENGTH.value(), "dolphin_jump", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:dolphin", Attributes.MOVEMENT_SPEED.value(), "dolphin_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:pufferfish", Attributes.ATTACK_KNOCKBACK.value(), "puff_knock", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:pufferfish", Attributes.MAX_HEALTH.value(), "puff_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:squid", Attributes.MOVEMENT_SPEED.value(), "squid_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:glow_squid", Attributes.MOVEMENT_SPEED.value(), "gsquid_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:goat", Attributes.ATTACK_KNOCKBACK.value(), "goat_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:goat", Attributes.JUMP_STRENGTH.value(), "goat_jump", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:goat", Attributes.KNOCKBACK_RESISTANCE.value(), "goat_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:horse", Attributes.MOVEMENT_SPEED.value(), "horse_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:horse", Attributes.JUMP_STRENGTH.value(), "horse_jump", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:donkey", Attributes.MOVEMENT_SPEED.value(), "donkey_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:donkey", Attributes.JUMP_STRENGTH.value(), "donkey_jump", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:mule", Attributes.MOVEMENT_SPEED.value(), "mule_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:mule", Attributes.JUMP_STRENGTH.value(), "mule_jump", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:polar_bear", Attributes.ATTACK_DAMAGE.value(), "pbear_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:polar_bear", Attributes.KNOCKBACK_RESISTANCE.value(), "pbear_kb", 0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:polar_bear", Attributes.MOVEMENT_SPEED.value(), "pbear_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:panda", Attributes.ARMOR.value(), "panda_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:panda", Attributes.KNOCKBACK_RESISTANCE.value(), "panda_kb", 0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:panda", Attributes.MOVEMENT_SPEED.value(), "panda_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:iron_golem", Attributes.ATTACK_DAMAGE.value(), "ig_atk", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:iron_golem", Attributes.ARMOR_TOUGHNESS.value(), "ig_tough", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:iron_golem", Attributes.KNOCKBACK_RESISTANCE.value(), "ig_kb", 0.8, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:iron_golem", Attributes.ATTACK_KNOCKBACK.value(), "ig_knock", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:iron_golem", Attributes.MOVEMENT_SPEED.value(), "ig_spd", -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:iron_golem", Attributes.JUMP_STRENGTH.value(), "ig_jump", -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:iron_golem", Attributes.ENTITY_INTERACTION_RANGE.value(), "ig_range", -0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:llama", Attributes.KNOCKBACK_RESISTANCE.value(), "llama_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:llama", Attributes.MOVEMENT_SPEED.value(), "llama_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:trader_llama", Attributes.KNOCKBACK_RESISTANCE.value(), "tllama_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:trader_llama", Attributes.MOVEMENT_SPEED.value(), "tllama_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:phantom", Attributes.KNOCKBACK_RESISTANCE.value(), "phantom_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:phantom", Attributes.BURNING_TIME.value(), "phantom_burn", 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:cod", Attributes.MOVEMENT_SPEED.value(), "cod_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:salmon", Attributes.MOVEMENT_SPEED.value(), "salmon_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:tropical_fish", Attributes.MOVEMENT_SPEED.value(), "tfish_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:tadpole", Attributes.MAX_HEALTH.value(), "tadpole_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:frog", Attributes.JUMP_STRENGTH.value(), "frog_jump", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:frog", Attributes.MAX_HEALTH.value(), "frog_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:camel", Attributes.MOVEMENT_SPEED.value(), "camel_spd", 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:camel", Attributes.JUMP_STRENGTH.value(), "camel_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:sniffer", Attributes.LUCK.value(), "sniff_luck", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:sniffer", Attributes.MOVEMENT_SPEED.value(), "sniff_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:villager", Attributes.LUCK.value(), "villager_luck", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:wandering_trader", Attributes.MOVEMENT_SPEED.value(), "wt_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("minecraft:wandering_trader", Attributes.LUCK.value(), "wt_luck", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("minecraft:wandering_trader", Attributes.MAX_HEALTH.value(), "wt_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);

        // ── 暮色 ──
        attr("twilightforest:alpha_yeti", Attributes.ATTACK_DAMAGE.value(), "ay_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:alpha_yeti", Attributes.MOVEMENT_SPEED.value(), "ay_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:alpha_yeti", Attributes.JUMP_STRENGTH.value(), "ay_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:yeti", Attributes.ATTACK_DAMAGE.value(), "tfyeti_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:yeti", Attributes.MOVEMENT_SPEED.value(), "tfyeti_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:yeti", Attributes.JUMP_STRENGTH.value(), "tfyeti_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:naga", Attributes.ARMOR.value(), "naga_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:naga", Attributes.MOVEMENT_SPEED.value(), "naga_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:minotaur", Attributes.ATTACK_DAMAGE.value(), "mino_atk", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:minotaur", Attributes.ARMOR.value(), "mino_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:minotaur", Attributes.ATTACK_KNOCKBACK.value(), "mino_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:minotaur", Attributes.MOVEMENT_SPEED.value(), "mino_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:minotaur", Attributes.JUMP_STRENGTH.value(), "mino_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:minoshroom", Attributes.ATTACK_DAMAGE.value(), "msh_atk", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:minoshroom", Attributes.ARMOR.value(), "msh_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:minoshroom", Attributes.ATTACK_KNOCKBACK.value(), "msh_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:minoshroom", Attributes.MOVEMENT_SPEED.value(), "msh_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:minoshroom", Attributes.JUMP_STRENGTH.value(), "msh_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:troll", Attributes.ATTACK_DAMAGE.value(), "troll_atk", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:troll", Attributes.ARMOR.value(), "troll_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:troll", Attributes.KNOCKBACK_RESISTANCE.value(), "troll_kb", 0.6, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:troll", Attributes.ATTACK_KNOCKBACK.value(), "troll_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:troll", Attributes.MOVEMENT_SPEED.value(), "troll_spd", -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:troll", Attributes.JUMP_STRENGTH.value(), "troll_jump", -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:troll", Attributes.ENTITY_INTERACTION_RANGE.value(), "troll_range", -0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:wraith", Attributes.MOVEMENT_SPEED.value(), "wraith_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:wraith", Attributes.ATTACK_DAMAGE.value(), "wraith_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:wraith", Attributes.SNEAKING_SPEED.value(), "wraith_sneak", 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:wraith", Attributes.KNOCKBACK_RESISTANCE.value(), "wraith_kb", -0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:armored_giant", Attributes.ARMOR.value(), "ag_armor", 4.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:armored_giant", Attributes.KNOCKBACK_RESISTANCE.value(), "ag_kb", 0.6, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:armored_giant", Attributes.MOVEMENT_SPEED.value(), "ag_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:armored_giant", Attributes.JUMP_STRENGTH.value(), "ag_jump", -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:helmet_crab", Attributes.ARMOR.value(), "hc_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:helmet_crab", Attributes.KNOCKBACK_RESISTANCE.value(), "hc_kb", 0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:helmet_crab", Attributes.MOVEMENT_SPEED.value(), "hc_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:quest_ram", Attributes.KNOCKBACK_RESISTANCE.value(), "qr_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:quest_ram", Attributes.ATTACK_KNOCKBACK.value(), "qr_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:quest_ram", Attributes.MOVEMENT_SPEED.value(), "qr_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:squirrel", Attributes.JUMP_STRENGTH.value(), "sq_jump", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:squirrel", Attributes.MOVEMENT_SPEED.value(), "sq_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:squirrel", Attributes.MAX_HEALTH.value(), "sq_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:hedge_spider", Attributes.JUMP_STRENGTH.value(), "hs_jump", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:swarm_spider", Attributes.JUMP_STRENGTH.value(), "ssp_jump", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:king_spider", Attributes.JUMP_STRENGTH.value(), "ks_jump", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:hostile_wolf", Attributes.ATTACK_DAMAGE.value(), "hw_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:hostile_wolf", Attributes.KNOCKBACK_RESISTANCE.value(), "hw_kb", -0.1, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:winter_wolf", Attributes.ATTACK_DAMAGE.value(), "ww_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:winter_wolf", Attributes.KNOCKBACK_RESISTANCE.value(), "ww_kb", -0.1, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:mist_wolf", Attributes.ATTACK_DAMAGE.value(), "mw_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:mist_wolf", Attributes.KNOCKBACK_RESISTANCE.value(), "mw_kb", -0.1, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:pinch_beetle", Attributes.ARMOR.value(), "pbe_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:pinch_beetle", Attributes.ATTACK_KNOCKBACK.value(), "pbe_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:pinch_beetle", Attributes.MOVEMENT_SPEED.value(), "pbe_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:plateau_boss", Attributes.ATTACK_DAMAGE.value(), "plb_atk", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:plateau_boss", Attributes.KNOCKBACK_RESISTANCE.value(), "plb_kb", 0.6, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:plateau_boss", Attributes.MOVEMENT_SPEED.value(), "plb_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:plateau_boss", Attributes.JUMP_STRENGTH.value(), "plb_jump", -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:redcap", Attributes.ATTACK_DAMAGE.value(), "red_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:redcap", Attributes.ARMOR.value(), "red_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:redcap", Attributes.MOVEMENT_SPEED.value(), "red_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:redcap_sapper", Attributes.ATTACK_DAMAGE.value(), "reds_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:redcap_sapper", Attributes.ARMOR.value(), "reds_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:redcap_sapper", Attributes.MOVEMENT_SPEED.value(), "reds_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:lower_goblin_knight", Attributes.ATTACK_DAMAGE.value(), "lgk_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:lower_goblin_knight", Attributes.ARMOR.value(), "lgk_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:lower_goblin_knight", Attributes.MOVEMENT_SPEED.value(), "lgk_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:upper_goblin_knight", Attributes.ATTACK_DAMAGE.value(), "ugk_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:upper_goblin_knight", Attributes.ARMOR.value(), "ugk_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:upper_goblin_knight", Attributes.MOVEMENT_SPEED.value(), "ugk_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:blockchain_goblin", Attributes.ATTACK_DAMAGE.value(), "bcg_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:blockchain_goblin", Attributes.KNOCKBACK_RESISTANCE.value(), "bcg_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:kobold", Attributes.MOVEMENT_SPEED.value(), "kobold_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:kobold", Attributes.MAX_HEALTH.value(), "kobold_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:slime_beetle", Attributes.JUMP_STRENGTH.value(), "sb_jump", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:slime_beetle", Attributes.KNOCKBACK_RESISTANCE.value(), "sb_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:maze_slime", Attributes.JUMP_STRENGTH.value(), "msl_jump", 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:maze_slime", Attributes.KNOCKBACK_RESISTANCE.value(), "msl_kb", -0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:boar", Attributes.ATTACK_DAMAGE.value(), "boar_atk", 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:boar", Attributes.ATTACK_KNOCKBACK.value(), "boar_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:deer", Attributes.MOVEMENT_SPEED.value(), "deer_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:dwarf_rabbit", Attributes.JUMP_STRENGTH.value(), "dr_jump", 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:dwarf_rabbit", Attributes.MAX_HEALTH.value(), "dr_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:giant_miner", Attributes.BLOCK_BREAK_SPEED.value(), "gm_break", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:giant_miner", Attributes.MOVEMENT_SPEED.value(), "gm_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:loyal_zombie", Attributes.MAX_HEALTH.value(), "lz_hp", 4.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:loyal_zombie", Attributes.MOVEMENT_SPEED.value(), "lz_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:mosquito_swarm", Attributes.MOVEMENT_SPEED.value(), "ms_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:mosquito_swarm", Attributes.MAX_HEALTH.value(), "ms_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:penguin", NeoForgeMod.SWIM_SPEED.value(), "peng_swim", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:raven", Attributes.MOVEMENT_SPEED.value(), "raven_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:raven", Attributes.KNOCKBACK_RESISTANCE.value(), "raven_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:rising_zombie", Attributes.ATTACK_DAMAGE.value(), "rz_atk", 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:rising_zombie", Attributes.MOVEMENT_SPEED.value(), "rz_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:tiny_bird", Attributes.KNOCKBACK_RESISTANCE.value(), "tb_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("twilightforest:towerwood_borer", Attributes.BLOCK_BREAK_SPEED.value(), "twb_break", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("twilightforest:towerwood_borer", Attributes.MAX_HEALTH.value(), "twb_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);

        // ── 灾变 ──
        attr("cataclysm:amethyst_crab", Attributes.ARMOR.value(), "ac_armor", 4.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:amethyst_crab", Attributes.ATTACK_DAMAGE.value(), "ac_atk", 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:amethyst_crab", Attributes.ATTACK_KNOCKBACK.value(), "ac_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:amethyst_crab", Attributes.ENTITY_INTERACTION_RANGE.value(), "ac_range", -0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:amethyst_crab", Attributes.JUMP_STRENGTH.value(), "ac_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:coral_golem", Attributes.ARMOR.value(), "cg_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:coral_golem", Attributes.KNOCKBACK_RESISTANCE.value(), "cg_kb", 0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:coral_golem", Attributes.MOVEMENT_SPEED.value(), "cg_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:deepling_brute", Attributes.ATTACK_DAMAGE.value(), "db_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:deepling_brute", Attributes.ARMOR.value(), "db_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:deepling_brute", Attributes.ATTACK_KNOCKBACK.value(), "db_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:deepling_brute", Attributes.MOVEMENT_SPEED.value(), "db_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:ender_golem", Attributes.ARMOR.value(), "eg_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:ender_golem", Attributes.KNOCKBACK_RESISTANCE.value(), "eg_kb", 0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:ender_golem", Attributes.MOVEMENT_SPEED.value(), "eg_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:ender_golem", Attributes.JUMP_STRENGTH.value(), "eg_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:koboleton", Attributes.ATTACK_DAMAGE.value(), "kob_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:koboleton", Attributes.MOVEMENT_SPEED.value(), "kob_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:koboleton", Attributes.KNOCKBACK_RESISTANCE.value(), "kob_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:kobolediator", Attributes.ATTACK_DAMAGE.value(), "kobd_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:kobolediator", Attributes.MOVEMENT_SPEED.value(), "kobd_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:kobolediator", Attributes.KNOCKBACK_RESISTANCE.value(), "kobd_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:the_prowler", Attributes.MOVEMENT_SPEED.value(), "tp_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:the_prowler", Attributes.ATTACK_KNOCKBACK.value(), "tp_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:the_prowler", Attributes.MAX_HEALTH.value(), "tp_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:urchinkin", Attributes.ATTACK_KNOCKBACK.value(), "urk_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:clawdian", Attributes.ATTACK_DAMAGE.value(), "claw_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:clawdian", Attributes.ARMOR.value(), "claw_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:clawdian", Attributes.ATTACK_KNOCKBACK.value(), "claw_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:clawdian", Attributes.MOVEMENT_SPEED.value(), "claw_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:cindaria", Attributes.ATTACK_DAMAGE.value(), "cind_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:cindaria", Attributes.ARMOR.value(), "cind_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:cindaria", Attributes.ATTACK_KNOCKBACK.value(), "cind_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:cindaria", Attributes.MOVEMENT_SPEED.value(), "cind_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:coralssus", Attributes.ATTACK_DAMAGE.value(), "cors_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:coralssus", Attributes.ARMOR.value(), "cors_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:coralssus", Attributes.ATTACK_KNOCKBACK.value(), "cors_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:coralssus", Attributes.MOVEMENT_SPEED.value(), "cors_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:aptrgangr", Attributes.ATTACK_DAMAGE.value(), "apt_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:aptrgangr", Attributes.ARMOR.value(), "apt_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:aptrgangr", Attributes.ATTACK_KNOCKBACK.value(), "apt_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:aptrgangr", Attributes.MOVEMENT_SPEED.value(), "apt_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:modern_remnant", Attributes.ARMOR.value(), "mr_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:modern_remnant", Attributes.KNOCKBACK_RESISTANCE.value(), "mr_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:modern_remnant", Attributes.MOVEMENT_SPEED.value(), "mr_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("cataclysm:netherite_ministrosity", Attributes.ARMOR.value(), "nm2_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:netherite_ministrosity", Attributes.KNOCKBACK_RESISTANCE.value(), "nm2_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("cataclysm:netherite_ministrosity", Attributes.MOVEMENT_SPEED.value(), "nm2_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // ── 传奇怪物 ──
        attr("legendary_monsters:overgrown_colossus", Attributes.MAX_HEALTH.value(), "oc_hp", 6.0, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:overgrown_colossus", Attributes.KNOCKBACK_RESISTANCE.value(), "oc_kb", 0.6, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:overgrown_colossus", Attributes.MOVEMENT_SPEED.value(), "oc_spd", -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:overgrown_colossus", Attributes.JUMP_STRENGTH.value(), "oc_jump", -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:frostbitten_golem", Attributes.ARMOR.value(), "fg_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:withered_abomination", Attributes.ATTACK_DAMAGE.value(), "wa_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:withered_abomination", Attributes.KNOCKBACK_RESISTANCE.value(), "wa_kb", 0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:withered_abomination", Attributes.MOVEMENT_SPEED.value(), "wa_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:skeletosaurus", Attributes.ATTACK_DAMAGE.value(), "skel_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:skeletosaurus", Attributes.JUMP_STRENGTH.value(), "skel_jump", -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:skeletosaurus", Attributes.MOVEMENT_SPEED.value(), "skel_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:dune_sentinel", Attributes.MOVEMENT_SPEED.value(), "ds_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:dune_sentinel", Attributes.ARMOR.value(), "ds_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:dune_sentinel", Attributes.ATTACK_KNOCKBACK.value(), "ds_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:dune_sentinel", Attributes.JUMP_STRENGTH.value(), "ds_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:ambusher", Attributes.MOVEMENT_SPEED.value(), "amb_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:ambusher", Attributes.ATTACK_DAMAGE.value(), "amb_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:ambusher", Attributes.LUCK.value(), "amb_luck", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:ambusher", Attributes.MAX_HEALTH.value(), "amb_hp", -4.0, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:beheaded_knight", Attributes.ATTACK_DAMAGE.value(), "bk_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:beheaded_knight", Attributes.ATTACK_KNOCKBACK.value(), "bk_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:beheaded_knight", Attributes.KNOCKBACK_RESISTANCE.value(), "bk_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:knights_armor", Attributes.ARMOR.value(), "ka_armor", 4.0, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:knights_armor", Attributes.KNOCKBACK_RESISTANCE.value(), "ka_kb", 0.6, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:knights_armor", Attributes.ATTACK_KNOCKBACK.value(), "ka_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:knights_armor", Attributes.MOVEMENT_SPEED.value(), "ka_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:knights_armor", Attributes.JUMP_STRENGTH.value(), "ka_jump", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:mossy_golem", Attributes.ARMOR.value(), "mg_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:mossy_golem", Attributes.KNOCKBACK_RESISTANCE.value(), "mg_kb", 0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:mossy_golem", Attributes.MOVEMENT_SPEED.value(), "mg_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:skeloraptor", Attributes.ATTACK_DAMAGE.value(), "skr_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:skeloraptor", Attributes.MOVEMENT_SPEED.value(), "skr_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:skeloraptor", Attributes.JUMP_STRENGTH.value(), "skr_jump", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:skeloraptor", Attributes.KNOCKBACK_RESISTANCE.value(), "skr_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:spiky_bug", Attributes.ATTACK_KNOCKBACK.value(), "spb_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:fractured", Attributes.ATTACK_DAMAGE.value(), "frac_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:fractured", Attributes.KNOCKBACK_RESISTANCE.value(), "frac_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:fractured_apostle", Attributes.ATTACK_DAMAGE.value(), "fra_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:fractured_apostle", Attributes.KNOCKBACK_RESISTANCE.value(), "fra_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:guard", Attributes.ATTACK_DAMAGE.value(), "grd_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:guard", Attributes.KNOCKBACK_RESISTANCE.value(), "grd_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:haunted_guard", Attributes.ATTACK_DAMAGE.value(), "hg_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:haunted_guard", Attributes.KNOCKBACK_RESISTANCE.value(), "hg_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:haunted_knight", Attributes.ATTACK_DAMAGE.value(), "hk_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:haunted_knight", Attributes.KNOCKBACK_RESISTANCE.value(), "hk_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:hovering_hurricane", Attributes.MOVEMENT_SPEED.value(), "hh_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:hovering_hurricane", Attributes.KNOCKBACK_RESISTANCE.value(), "hh_kb", -0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:resurrected_knight", Attributes.ATTACK_DAMAGE.value(), "rk_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:resurrected_knight", Attributes.KNOCKBACK_RESISTANCE.value(), "rk_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:stratling", Attributes.ATTACK_DAMAGE.value(), "stl_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:stratling", Attributes.MOVEMENT_SPEED.value(), "stl_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:wandering_eye", Attributes.ATTACK_DAMAGE.value(), "we_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:wandering_eye", Attributes.MAX_HEALTH.value(), "we_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:warped_fungussus", Attributes.MOVEMENT_SPEED.value(), "wf_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("legendary_monsters:bomber", Attributes.ENTITY_INTERACTION_RANGE.value(), "bom_range", -0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("legendary_monsters:shulker_mimic", Attributes.MOVEMENT_SPEED.value(), "sm_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // ── 永恒星光 ──
        attr("eternal_starlight:lunar_monstrosity", Attributes.ATTACK_DAMAGE.value(), "lm_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:lunar_monstrosity", Attributes.ATTACK_KNOCKBACK.value(), "lm_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:lunar_monstrosity", Attributes.MOVEMENT_SPEED.value(), "lm_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:the_gatekeeper", Attributes.ATTACK_KNOCKBACK.value(), "tgk_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:the_gatekeeper", Attributes.MOVEMENT_SPEED.value(), "tgk_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:starlight_golem", Attributes.ARMOR.value(), "sg_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:starlight_golem", Attributes.KNOCKBACK_RESISTANCE.value(), "sg_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:starlight_golem", Attributes.MOVEMENT_SPEED.value(), "sg_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:aethersent_golem", Attributes.ARMOR.value(), "asg_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:aethersent_golem", Attributes.KNOCKBACK_RESISTANCE.value(), "asg_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:aethersent_golem", Attributes.MOVEMENT_SPEED.value(), "asg_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:astral_golem", Attributes.ARMOR.value(), "ast_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:astral_golem", Attributes.KNOCKBACK_RESISTANCE.value(), "ast_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:astral_golem", Attributes.MOVEMENT_SPEED.value(), "ast_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:grimstone_golem", Attributes.ARMOR.value(), "grg_armor", 3.0, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:grimstone_golem", Attributes.KNOCKBACK_RESISTANCE.value(), "grg_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:grimstone_golem", Attributes.MOVEMENT_SPEED.value(), "grg_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:ent", Attributes.MAX_HEALTH.value(), "ent_hp", 6.0, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:ent", Attributes.KNOCKBACK_RESISTANCE.value(), "ent_kb", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:ent", Attributes.MOVEMENT_SPEED.value(), "ent_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:ent", Attributes.JUMP_STRENGTH.value(), "ent_jump", -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:stranghoul", Attributes.ATTACK_DAMAGE.value(), "str_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:stranghoul", Attributes.MOVEMENT_SPEED.value(), "str_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:stranghoul", Attributes.ENTITY_INTERACTION_RANGE.value(), "str_range", -0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:nightfall_spider", Attributes.JUMP_STRENGTH.value(), "ns_jump", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:seeker", Attributes.MOVEMENT_SPEED.value(), "seek_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:seeker", Attributes.KNOCKBACK_RESISTANCE.value(), "seek_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:tangled", Attributes.ATTACK_DAMAGE.value(), "tgl_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:tangled", Attributes.MOVEMENT_SPEED.value(), "tgl_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:tangled_skull", Attributes.ATTACK_DAMAGE.value(), "tgs_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:tangled_skull", Attributes.MOVEMENT_SPEED.value(), "tgs_spd", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:thirst_walker", Attributes.ATTACK_DAMAGE.value(), "tw_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:thirst_walker", Attributes.MOVEMENT_SPEED.value(), "tw_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:ratlin", Attributes.MOVEMENT_SPEED.value(), "rat_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:ratlin", Attributes.MAX_HEALTH.value(), "rat_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:zombified_ratlin", Attributes.MOVEMENT_SPEED.value(), "zrat_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:zombified_ratlin", Attributes.MAX_HEALTH.value(), "zrat_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:gleech", Attributes.MOVEMENT_SPEED.value(), "gle_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:gleech", Attributes.MAX_HEALTH.value(), "gle_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:boarwarf", Attributes.ATTACK_DAMAGE.value(), "bw_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:boarwarf", Attributes.ATTACK_KNOCKBACK.value(), "bw_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:boarwarf", Attributes.MOVEMENT_SPEED.value(), "bw_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:aurora_deer", Attributes.MOVEMENT_SPEED.value(), "ad_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:aurora_deer", Attributes.JUMP_STRENGTH.value(), "ad_jump", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:crystallized_moth", Attributes.KNOCKBACK_RESISTANCE.value(), "cm_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:shadow_snail", Attributes.ARMOR.value(), "ss_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:shadow_snail", Attributes.KNOCKBACK_RESISTANCE.value(), "ss_kb", 0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:shadow_snail", Attributes.MOVEMENT_SPEED.value(), "ss_spd", -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:shadow_snail", Attributes.ENTITY_INTERACTION_RANGE.value(), "ss_range", -0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:shimmer_lacewing", Attributes.MOVEMENT_SPEED.value(), "sl_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:shimmer_lacewing", Attributes.KNOCKBACK_RESISTANCE.value(), "sl_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:starfire_bird", Attributes.KNOCKBACK_RESISTANCE.value(), "sfb_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("eternal_starlight:yeti", Attributes.ATTACK_DAMAGE.value(), "esy_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("eternal_starlight:yeti", Attributes.MOVEMENT_SPEED.value(), "esy_spd", -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // ── 首领崛起 ──
        attr("block_factorys_bosses:kraken", Attributes.ATTACK_DAMAGE.value(), "kra_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:kraken", Attributes.ATTACK_KNOCKBACK.value(), "kra_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:infernal_dragon", Attributes.ATTACK_DAMAGE.value(), "id_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:infernal_dragon", Attributes.KNOCKBACK_RESISTANCE.value(), "id_kb", 0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:sandworm", Attributes.ARMOR.value(), "sw_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:sandworm", Attributes.BLOCK_BREAK_SPEED.value(), "sw_break", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:sandworm", Attributes.KNOCKBACK_RESISTANCE.value(), "sw_kb", 0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:sandworm", Attributes.JUMP_STRENGTH.value(), "sw_jump", -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:sandworm", Attributes.ENTITY_INTERACTION_RANGE.value(), "sw_range", -0.3, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:soul_knight_wither_skeleton", Attributes.ATTACK_DAMAGE.value(), "skw_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:soul_knight_wither_skeleton", Attributes.KNOCKBACK_RESISTANCE.value(), "skw_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:soul_skeleton", Attributes.ATTACK_DAMAGE.value(), "sos_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:soul_skeleton", Attributes.KNOCKBACK_RESISTANCE.value(), "sos_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:crossbow_pirate", Attributes.MAX_HEALTH.value(), "cp_hp", -2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:dragon_guard_sword", Attributes.ATTACK_DAMAGE.value(), "dgs_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:dragon_guard_sword", Attributes.ATTACK_KNOCKBACK.value(), "dgs_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:dragon_guard_sword", Attributes.MOVEMENT_SPEED.value(), "dgs_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:flaming_skeleton_guard_fireball", Attributes.ATTACK_DAMAGE.value(), "fsf_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:flaming_skeleton_guard_sword", Attributes.ATTACK_DAMAGE.value(), "fss_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:kraken_tentacle", Attributes.ATTACK_DAMAGE.value(), "kt_atk", 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:kraken_tentacle", Attributes.MOVEMENT_SPEED.value(), "kt_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:pile_of_bones", Attributes.ARMOR.value(), "pob_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:pile_of_bones", Attributes.KNOCKBACK_RESISTANCE.value(), "pob_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:pirate_captain", Attributes.ATTACK_DAMAGE.value(), "pc_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:pirate_captain", Attributes.LUCK.value(), "pc_luck", 1.0, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:pirate_captain", Attributes.MOVEMENT_SPEED.value(), "pc_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:pirate_rook", Attributes.ARMOR.value(), "pr_armor", 2.0, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:pirate_rook", Attributes.ATTACK_DAMAGE.value(), "pr_atk", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("block_factorys_bosses:pirate_rook", Attributes.ATTACK_KNOCKBACK.value(), "pr_knock", 0.5, AttributeModifier.Operation.ADD_VALUE);
        attr("block_factorys_bosses:pirate_rook", Attributes.MOVEMENT_SPEED.value(), "pr_spd", -0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // ── lensouls ──
        attr("lensouls:twitcher", Attributes.MOVEMENT_SPEED.value(), "twitch_spd", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("lensouls:twitcher", Attributes.JUMP_STRENGTH.value(), "twitch_jump", 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attr("lensouls:twitcher", Attributes.KNOCKBACK_RESISTANCE.value(), "twitch_kb", -0.2, AttributeModifier.Operation.ADD_VALUE);
    }

    // ── 玩家造成伤害增伤（kind）：远程/魔法/近战 ──
    private static final Map<String, float[]> ATTACK_KIND_BONUS = new HashMap<>();
    // [0]=melee [1]=ranged [2]=magic 倍率（1.12 = +12%）
    static {
        ATTACK_KIND_BONUS.put("cataclysm:the_harbinger", new float[]{0.88f, 1.12f, 1.0f});
        ATTACK_KIND_BONUS.put("minecraft:skeleton", new float[]{1.0f, 1.1f, 1.0f});
        ATTACK_KIND_BONUS.put("minecraft:stray", new float[]{1.0f, 1.1f, 1.0f});
        ATTACK_KIND_BONUS.put("minecraft:bogged", new float[]{1.0f, 1.1f, 1.0f});
        ATTACK_KIND_BONUS.put("minecraft:wither", new float[]{1.0f, 1.1f, 1.0f});
        ATTACK_KIND_BONUS.put("minecraft:pillager", new float[]{1.0f, 1.1f, 1.0f});
        ATTACK_KIND_BONUS.put("minecraft:evoker", new float[]{0.88f, 1.0f, 1.15f});
        ATTACK_KIND_BONUS.put("cataclysm:the_watcher", new float[]{0.88f, 1.15f, 1.0f});
        ATTACK_KIND_BONUS.put("legendary_monsters:wandering_eye", new float[]{1.0f, 1.1f, 1.0f});
        ATTACK_KIND_BONUS.put("twilightforest:adherent", new float[]{1.0f, 1.1f, 1.0f});
        ATTACK_KIND_BONUS.put("twilightforest:death_tome", new float[]{1.0f, 1.0f, 1.1f});
        ATTACK_KIND_BONUS.put("twilightforest:skeleton_druid", new float[]{1.0f, 1.0f, 1.1f});
        ATTACK_KIND_BONUS.put("twilightforest:lich", new float[]{0.88f, 1.0f, 1.12f});
        ATTACK_KIND_BONUS.put("twilightforest:lich_minion", new float[]{1.0f, 1.0f, 1.05f});
        ATTACK_KIND_BONUS.put("cataclysm:deepling_warlock", new float[]{1.0f, 1.0f, 1.1f});
        ATTACK_KIND_BONUS.put("cataclysm:wadjet", new float[]{1.0f, 1.0f, 1.1f});
        ATTACK_KIND_BONUS.put("eternal_starlight:lonestar_skeleton", new float[]{1.0f, 1.1f, 1.0f});
        ATTACK_KIND_BONUS.put("eternal_starlight:twilight_gaze", new float[]{1.0f, 1.1f, 1.0f});
        ATTACK_KIND_BONUS.put("eternal_starlight:the_gatekeeper", new float[]{1.0f, 1.0f, 1.1f});
        ATTACK_KIND_BONUS.put("block_factorys_bosses:crossbow_pirate", new float[]{1.0f, 1.1f, 1.0f});
        ATTACK_KIND_BONUS.put("block_factorys_bosses:pirate_captain", new float[]{1.0f, 1.05f, 1.0f});
    }

    // ========== 事件 ==========

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        List<String> gearEntities = collectGearEntities(player);
        reconcilePhotoAttributes(player, gearEntities);

        boolean hasFlightPhoto = false;
        for (String id : gearEntities) {
            if (FLYING_ENTITIES.contains(id)) { hasFlightPhoto = true; break; }
        }
        if (hasFlightPhoto) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            player.getPersistentData().putBoolean(FLIGHT_TAG, true);
        } else if (player.getPersistentData().getBoolean(FLIGHT_TAG)) {
            player.getPersistentData().remove(FLIGHT_TAG);
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }

        updatePhotoSlots(player, gearEntities);

        // 末影人：周围末影人不主动攻击
        if (player.tickCount % 10 == 0 && gearEntities.contains("minecraft:enderman")) {
            player.level().getEntities(player, player.getBoundingBox().inflate(16.0),
                            e -> e instanceof EnderMan)
                    .forEach(e -> {
                        EnderMan em = (EnderMan) e;
                        if (em.getTarget() == player) em.setTarget(null);
                    });
        }

        // 猫：驱散周围爬行者/幻翼
        if (player.tickCount % 20 == 0 && gearEntities.contains("minecraft:cat")) {
            player.level().getEntities(player, player.getBoundingBox().inflate(8.0),
                            e -> e.getType() == net.minecraft.world.entity.EntityType.CREEPER
                                    || e.getType() == net.minecraft.world.entity.EntityType.PHANTOM)
                    .forEach(e -> {
                        if (e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == player) {
                            mob.setTarget(null);
                        }
                    });
        }

        // allay：掉落物磁吸
        if (gearEntities.contains("minecraft:allay")) {
            player.level().getEntities(player, player.getBoundingBox().inflate(8.0),
                            e -> e instanceof net.minecraft.world.entity.item.ItemEntity ie
                                    && !ie.hasPickUpDelay())
                    .forEach(e -> {
                        e.setDeltaMovement(player.position().subtract(e.position()).normalize().scale(0.15));
                    });
        }

        // 猪：回饥饿
        if (gearEntities.contains("minecraft:pig") && player.getFoodData() != null
                && player.getFoodData().getFoodLevel() < 20 && player.tickCount % 40 == 0) {
            player.getFoodData().eat(1, 0.0f);
        }

        // 应用照片效果（药水 + 元素活性灌注）
        for (String stolen : gearEntities) {
            PhotographEffectRegistry.applyEffects(player, stolen);
        }
    }

    /** Curios 照片槽位扩展：vindicator 恒定 +1，弱照片组按各自分配值累加（无上限） */
    private static void updatePhotoSlots(ServerPlayer player, List<String> gearEntities) {
        int extra = 0;
        if (gearEntities.contains("minecraft:vindicator")) extra += 1;
        for (String id : gearEntities) {
            extra += WEAK_SLOT_BONUS.getOrDefault(id, 0);
        }

        int cur = player.getPersistentData().getInt(SLOT_TAG);
        if (cur == extra) return;

        // 打开 Curios 容器期间动态改槽会与服务端→客户端的槽位同步产生竞态：
        // 客户端容器槽列表仍持有旧的 CurioSlot，handler 却已缩小，
        // 导致 CuriosScreen.render() 访问越界索引崩溃（Slot N not in valid range）。
        // 关闭容器后下一 tick 检测到 cur != extra 会自动补做。
        if (player.containerMenu instanceof ICuriosMenu) {
            return;
        }

        final int targetExtra = extra;
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            if (cur > 0) {
                handler.removeSlotModifier("photograph", ResourceLocation.parse(SLOT_MOD_ID));
            }
            if (targetExtra > 0) {
                handler.addTransientSlotModifier("photograph", ResourceLocation.parse(SLOT_MOD_ID),
                        targetExtra, AttributeModifier.Operation.ADD_VALUE);
            }
        });
        player.getPersistentData().putInt(SLOT_TAG, extra);
    }

    /** 收集佩戴的照片实体 ID（仅照片栏位内物品生效；相册展开其内照片，背包中的相册不生效） */
    public static List<String> collectGearEntities(ServerPlayer player) {
        List<String> ids = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                IDynamicStackHandler stackHandler = stacksHandler.getStacks();
                for (int i = 0; i < stackHandler.getSlots(); i++) {
                    ItemStack stack = stackHandler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    if (stack.getItem() instanceof PhotoAlbumItem) {
                        // 相册：等效装备其内全部照片（去重 + 边界安全）
                        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                        for (ItemStack photo : contents.nonEmptyItems()) {
                            String stolen = PhotographEffectRegistry.getStolenEntity(photo);
                            if (stolen == null) stolen = PhotographEffectRegistry.getElementEntity(photo);
                            if (stolen != null && !ids.contains(stolen)) ids.add(stolen);
                        }
                        continue;
                    }
                    String stolen = PhotographEffectRegistry.getStolenEntity(stack);
                    if (stolen == null) stolen = PhotographEffectRegistry.getElementEntity(stack);
                    if (stolen != null && !ids.contains(stolen)) ids.add(stolen);
                }
            }
        });
        return ids;
    }

    /** 已装备照片中，主体为 Boss（is_boss 标记或实体命中首领清单）的去重种类数（仅照片栏位；含栏位内相册的照片） */
    public static int countBossPhotos(ServerPlayer player) {
        Set<String> seen = new HashSet<>();
        int n = 0;
        var handlerOpt = CuriosApi.getCuriosInventory(player);
        if (handlerOpt.isPresent()) {
            for (var sh : handlerOpt.get().getCurios().values()) {
                var stacks = sh.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    if (stack.getItem() instanceof PhotoAlbumItem) {
                        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                        for (ItemStack photo : contents.nonEmptyItems()) {
                            if (isBossPhotoDedup(photo, seen)) n++;
                        }
                    } else if (isBossPhotoDedup(stack, seen)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** Boss 照片且实体 id 未统计过（同 Boss 多张只算一次） */
    private static boolean isBossPhotoDedup(ItemStack stack, Set<String> seen) {
        if (!PhotographEffectRegistry.isBossPhoto(stack)) return false;
        String ent = PhotographEffectRegistry.getPhotoEntity(stack);
        return ent != null ? seen.add(ent) : true;
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;

        // ── 玩家受击：减伤/元素弱点 ──
        if (event.getEntity() instanceof ServerPlayer player) {
            List<String> gear = collectGearEntities(player);
            for (String id : gear) {
                List<DamageRule> rules = DAMAGE_RULES.get(id);
                if (rules == null) continue;
                for (DamageRule rule : rules) {
                    if (rule.matcher().test(event)) {
                        event.setNewDamage(event.getNewDamage() * rule.multiplier());
                    }
                }
            }
            // naga 荆棘
            if (gear.contains("twilightforest:naga")) {
                Entity attacker = event.getSource().getEntity();
                if (attacker instanceof LivingEntity le && attacker.distanceToSqr(player) < 16.0
                        && player.getRandom().nextFloat() < 0.15f) {
                    attacker.hurt(player.damageSources().thorns(player), 1.0f + player.getRandom().nextInt(4));
                }
            }
        }

        // ── 玩家攻击：kind 增伤 + 飞行惩罚 + 焰魔烙印 ──
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            if (player.getPersistentData().getBoolean(FLIGHT_TAG) && player.getAbilities().flying) {
                event.setNewDamage(event.getNewDamage() * 0.1f);
            }
            List<String> gear = collectGearEntities(player);
            for (String id : gear) {
                float[] kind = ATTACK_KIND_BONUS.get(id);
                if (kind != null) {
                    float mult = 1.0f;
                    if (isMelee(event)) mult = kind[0];
                    else if (isRanged(event)) mult = kind[1];
                    else if (isMagic(event)) mult = kind[2];
                    if (mult != 1.0f) event.setNewDamage(event.getNewDamage() * mult);
                }
            }
            // 焰魔：炽焰烙印（自有减甲减韧，绕过灾变效果，可叠加）
            if (gear.contains("cataclysm:ignis") && player.getRandom().nextFloat() < 0.2f) {
                com.plumejade.lensouls.handler.IgnisBrandHandler.applyIgnisArmorBreak(event.getEntity());
            }
        }
    }

    /** 效果免疫（黑暗/中毒/诅咒/负面时长） */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        net.minecraft.world.effect.MobEffectInstance inst = event.getEffectInstance();
        if (inst == null) return;

        boolean beneficial = inst.getEffect().value().isBeneficial();
        String effectId = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value()).toString();
        List<String> gearEntities = collectGearEntities(player);
        boolean witch = gearEntities.contains("minecraft:witch");

        if (gearEntities.contains("minecraft:warden") && effectId.equals("minecraft:darkness")) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (gearEntities.contains("minecraft:cave_spider") && effectId.equals("minecraft:poison")) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (gearEntities.contains("minecraft:bogged") && effectId.equals("minecraft:poison")) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (gearEntities.contains("minecraft:husk") && effectId.equals("minecraft:hunger")) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (gearEntities.contains("cataclysm:maledictus") && CURSE_EFFECTS.contains(effectId)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (gearEntities.contains("legendary_monsters:withered_abomination") && effectId.equals("minecraft:wither")) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (gearEntities.contains("minecraft:wither") && effectId.equals("minecraft:wither")) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (!handlingWitch && witch && !beneficial && inst.getDuration() > 1) {
            handlingWitch = true;
            try {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        inst.getEffect(), inst.getDuration() / 2, inst.getAmplifier(),
                        inst.isAmbient(), inst.isVisible(), inst.showIcon()));
            } finally {
                handlingWitch = false;
            }
        }
    }

    private static boolean handlingWitch = false;
    private static final Set<String> CURSE_EFFECTS = Set.of(
            "cataclysm:abyssal_curse",
            "cataclysm:curse_of_desert",
            "legendary_monsters:curse_of_desert"
    );

    /** 弹射物偏转（shulker_mimic） */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof ServerPlayer player
                && hasEntityInGear(player, id -> "legendary_monsters:shulker_mimic".equals(id))) {
            net.minecraft.world.phys.Vec3 vel = event.getProjectile().getDeltaMovement();
            if (vel.lengthSqr() > 0.001) {
                event.getProjectile().setDeltaMovement(vel.scale(-1.0));
            }
            event.setCanceled(true);
        }
    }

    /**
     * 构建某实体照片佩戴时应提供的属性修饰符（Curios 佩戴时驱动）。
     * 标准属性按（实体, 条目名）派生稳定 UUID（同种实体只生效一份、不同种可叠加）；
     * 元素弱点按（实体, 元素）派生稳定 UUID（同样去重、跨种叠加）。
     */
    public static Multimap<Holder<Attribute>, AttributeModifier> buildAttributeModifiers(String entityId) {
        Multimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
        List<AttributeEntry> list = ATTRIBUTES.get(entityId);
        if (list != null) {
            for (AttributeEntry ae : list) {
                map.put(holder(ae.attribute()),
                        new AttributeModifier(MOD_BASE.withPath(entityId.replace(':', '_') + "_" + ae.modName()), ae.amount(), ae.operation()));
            }
        }
        var weak = DataPackLoader.getAllWeaknesses(ResourceLocation.parse(entityId));
        if (weak != null) {
            addWeakModifier(map, ModAttributes.FIRE_WEAKNESS, weak.containsKey(ElementDamage.FIRE), entityId, "fire");
            addWeakModifier(map, ModAttributes.WATER_WEAKNESS, weak.containsKey(ElementDamage.WATER), entityId, "water");
            addWeakModifier(map, ModAttributes.EARTH_WEAKNESS, weak.containsKey(ElementDamage.EARTH), entityId, "earth");
            addWeakModifier(map, ModAttributes.ENDER_WEAKNESS, weak.containsKey(ElementDamage.ENDER), entityId, "ender");
        }
        return map;
    }

    private static void addWeakModifier(Multimap<Holder<Attribute>, AttributeModifier> map,
                                        DeferredHolder<Attribute, Attribute> attr, boolean present,
                                        String entityId, String element) {
        if (!present) return;
        String path = "weak_" + entityId.replace(':', '_') + "_" + element;
        map.put(attr, new AttributeModifier(MOD_BASE.withPath(path), 0.12, AttributeModifier.Operation.ADD_VALUE));
    }

    /**
     * 生成照片属性加成描述行（与 {@link #buildAttributeModifiers} 同源，tooltip 复用）。
     * 使用属性修饰符的官方译名（跳跃力量/游泳速度…），按正负着色，逐行换行。
     */
    public static List<Component> describeAttributes(String entityId) {
        return describeAttributes(entityId, null);
    }

    /**
     * 生成照片饰品佩戴时的属性/元素弱点摘要。skipText 为非 null 时，
     * 若静态描述已提及某属性/元素，则跳过该行，避免 tooltip 重复。
     */
    public static List<Component> describeAttributes(String entityId, String skipText) {
        List<Component> lines = new ArrayList<>();
        List<AttributeEntry> list = ATTRIBUTES.get(entityId);
        if (list != null) {
            for (AttributeEntry ae : list) {
                Component nameComp = Component.translatable(ae.attribute().getDescriptionId());
                String name = nameComp.getString();
                if (skipText != null && skipText.contains(name)) {
                    continue;
                }
                lines.add(formatAttribute(ae.attribute(), ae.amount(), ae.operation()));
            }
        }
        var weak = DataPackLoader.getAllWeaknesses(ResourceLocation.parse(entityId));
        if (weak != null) {
            for (ElementDamage el : ElementDamage.values()) {
                if (weak.containsKey(el)) {
                    MutableComponent text = Component.translatable("tooltip.lensouls.weakness." + el.getSerializedName());
                    if (skipText != null && skipText.contains(text.getString())) {
                        continue;
                    }
                    lines.add(text.withStyle(ChatFormatting.RED));
                }
            }
        }
        return lines;
    }

    private static Component formatAttribute(Attribute attribute, double amount, AttributeModifier.Operation op) {
        Component name = Component.translatable(attribute.getDescriptionId());
        String value;
        if (op == AttributeModifier.Operation.ADD_VALUE) {
            value = (amount == Math.floor(amount) && !Double.isInfinite(amount))
                    ? String.format(Locale.ROOT, "%+.0f", amount)
                    : String.format(Locale.ROOT, "%+.2f", amount);
        } else {
            value = String.format(Locale.ROOT, "%+.0f%%", amount * 100);
        }
        ChatFormatting color = amount >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
        return Component.literal("").append(name).append(Component.literal(" " + value)).withStyle(color);
    }

    public static boolean hasEntityInGear(ServerPlayer player, Predicate<String> predicate) {
        for (String entity : collectGearEntities(player)) {
            if (predicate.test(entity)) return true;
        }
        return false;
    }

    /** 佩戴照片提供的该元素追伤加成（照片 mob attacker_element 等级 ×3%，供 DamageHandler 查询） */
    public static float getPhotoElementBonus(ServerPlayer player, com.plumejade.lensouls.damage.ElementDamage element) {
        float bonus = 0f;
        for (String id : collectGearEntities(player)) {
            int lvl = com.plumejade.lensouls.config.AttackerElementLoader.getLevel(
                    ResourceLocation.parse(id), element);
            if (lvl > 0) bonus += lvl * 0.03f;
        }
        return bonus;
    }

    // ========== 属性集中对账（按去重实体集合统一施加/回收，避免 Curios 按共享 UUID 在部分移除时误删）==========

    private static void reconcilePhotoAttributes(ServerPlayer player, List<String> gear) {
        Multimap<Holder<Attribute>, AttributeModifier> desired = HashMultimap.create();
        for (String id : gear) desired.putAll(buildAttributeModifiers(id));

        Multimap<Holder<Attribute>, AttributeModifier> prev =
                APPLIED_ATTRS.getOrDefault(player.getUUID(), HashMultimap.create());

        for (var e : desired.entries()) {
            AttributeInstance inst = player.getAttribute(e.getKey());
            if (inst != null && inst.getModifier(e.getValue().id()) == null) inst.addTransientModifier(e.getValue());
        }
        for (var e : prev.entries()) {
            if (!desired.containsEntry(e.getKey(), e.getValue())) {
                AttributeInstance inst = player.getAttribute(e.getKey());
                if (inst != null) inst.removeModifier(e.getValue().id());
            }
        }
        APPLIED_ATTRS.put(player.getUUID(), desired);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Multimap<Holder<Attribute>, AttributeModifier> prev = APPLIED_ATTRS.remove(player.getUUID());
        if (prev != null) {
            for (var e : prev.entries()) {
                AttributeInstance inst = player.getAttribute(e.getKey());
                if (inst != null) inst.removeModifier(e.getValue().id());
            }
        }
    }
}
