package com.plumejade.lensouls;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置——TOML 配置文件。
 * <p>
 * 通过 {@code ModConfigSpec} 提供类型安全的配置访问。
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ==================== 镜魂 ====================
    static { BUILDER.push("souls"); }

    public static final ModConfigSpec.IntValue DEFAULT_DURATION = BUILDER
            .comment("Mirror soul infusion duration (seconds)")
            .defineInRange("defaultDuration", 30, 1, 3600);

    public static final ModConfigSpec.IntValue DEFAULT_COOLDOWN = BUILDER
            .comment("Basic soul cooldown (seconds)")
            .defineInRange("defaultCooldown", 60, 1, 3600);

    public static final ModConfigSpec.IntValue BOSS_COOLDOWN = BUILDER
            .comment("Boss soul cooldown (seconds)")
            .defineInRange("bossCooldown", 120, 1, 3600);

    static { BUILDER.pop(); }

    // ==================== 照片 ====================
    static { BUILDER.push("photos"); }

    public static final ModConfigSpec.DoubleValue PHOTO_BONUS = BUILDER
            .comment("Photo damage bonus multiplier (e.g. 1.2 = +120% damage)")
            .defineInRange("photoBonus", 1.2, 0.0, 10.0);

    static { BUILDER.pop(); }

    // ==================== 次元枪 ====================
    static { BUILDER.push("dimensionalGun"); }

    public static final ModConfigSpec.IntValue DG_BASE_MAX_AMMO = BUILDER
            .comment("Dimensional Gun: Base max ammo")
            .defineInRange("dgBaseMaxAmmo", 10, 1, 100);
    public static final ModConfigSpec.IntValue DG_BASE_REGEN_TIME = BUILDER
            .comment("Dimensional Gun: Full regen time from 0 (seconds)")
            .defineInRange("dgBaseRegenTime", 60, 1, 3600);
    public static final ModConfigSpec.DoubleValue DG_BASE_DAMAGE = BUILDER
            .comment("Dimensional Gun: Base damage per hit")
            .defineInRange("dgBaseDamage", 5.0, 1.0, 100.0);
    public static final ModConfigSpec.DoubleValue DG_BASE_ARMOR_PEN = BUILDER
            .comment("Dimensional Gun: Base armor penetration ratio (0-100%)")
            .defineInRange("dgBaseArmorPen", 0.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DG_KILL_TARGET = BUILDER
            .comment("Dimensional Gun: Kills required for max level")
            .defineInRange("dgKillTarget", 800, 1, 10000);
    public static final ModConfigSpec.DoubleValue DG_MAX_DAMAGE = BUILDER
            .comment("Dimensional Gun: Max damage at max kills")
            .defineInRange("dgMaxDamage", 10.0, 1.0, 500.0);
    public static final ModConfigSpec.DoubleValue DG_MAX_ARMOR_PEN = BUILDER
            .comment("Dimensional Gun: Max armor penetration ratio (0-100%)")
            .defineInRange("dgMaxArmorPen", 60.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue DG_MAX_AMMO = BUILDER
            .comment("Dimensional Gun: Max ammo at max kills")
            .defineInRange("dgMaxAmmo", 25, 1, 100);
    public static final ModConfigSpec.IntValue DG_MIN_REGEN_TIME = BUILDER
            .comment("Dimensional Gun: Min full regen time at max kills (seconds)")
            .defineInRange("dgMinRegenTime", 10, 1, 3600);
    public static final ModConfigSpec.IntValue DG_BASE_CHARGE_TIME = BUILDER
            .comment("Dimensional Gun: Base charge time (ticks, 20 = 1s)")
            .defineInRange("dgBaseChargeTime", 10, 1, 100);
    public static final ModConfigSpec.IntValue DG_MIN_CHARGE_TIME = BUILDER
            .comment("Dimensional Gun: Min charge time at max kills (ticks)")
            .defineInRange("dgMinChargeTime", 4, 1, 100);
    public static final ModConfigSpec.DoubleValue DG_ACCURACY_OFFSET = BUILDER
            .comment("Dimensional Gun: Max spread at min charge (blocks offset / 10 blocks distance)")
            .defineInRange("dgAccuracyOffset", 0.8, 0.0, 5.0);
    public static final ModConfigSpec.IntValue DG_FIRE_RATE = BUILDER
            .comment("Dimensional Gun: Full-auto fire interval (ticks)")
            .defineInRange("dgFireRate", 5, 1, 40);
    public static final ModConfigSpec.IntValue DG_HELL_FIRE_DURATION = BUILDER
            .comment("Hell bullet: Fire duration (seconds)")
            .defineInRange("dgHellFireDuration", 5, 1, 60);
    public static final ModConfigSpec.DoubleValue DG_ENDER_PULL_FORCE = BUILDER
            .comment("Ender bullet: Pull force")
            .defineInRange("dgEnderPullForce", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.IntValue DG_ENDER_PULL_DURATION = BUILDER
            .comment("Ender bullet: Pull duration (ticks)")
            .defineInRange("dgEnderPullDuration", 10, 1, 200);
    public static final ModConfigSpec.DoubleValue DG_OVERWORLD_HEAL = BUILDER
            .comment("Overworld bullet: Heal amount")
            .defineInRange("dgOverworldHeal", 2.0, 0.0, 40.0);
    public static final ModConfigSpec.DoubleValue DG_ELEMENT_MULTIPLIER = BUILDER
            .comment("Dimensional Gun: Element damage multiplier (stacks with soul)")
            .defineInRange("dgElementMultiplier", 1.0, 0.0, 10.0);

    static { BUILDER.pop(); }

    // ==================== 引力枪 ====================
    static { BUILDER.push("gravityGun"); }

    public static final ModConfigSpec.IntValue GG_COOLDOWN = BUILDER
            .comment("Gravity Gun: cooldown after each shot (ticks, 20 = 1s)")
            .defineInRange("ggCooldown", 4, 0, 100);
    public static final ModConfigSpec.DoubleValue GG_PULL_FORCE = BUILDER
            .comment("Gravity Gun: pull strength multiplier")
            .defineInRange("ggPullForce", 1.0, 0.1, 5.0);

    static { BUILDER.pop(); }

    // ==================== BOSS 韧性 ====================
    static { BUILDER.push("toughness"); }

    public static final ModConfigSpec.DoubleValue TOUGH_DAMAGE_REDUCTION = BUILDER
            .comment("Boss damage reduction when fully shielded (0-1, 0.8 = 80%)")
            .defineInRange("toughDamageReduction", 0.8, 0.0, 0.99);

    public static final ModConfigSpec.IntValue TOUGH_RECOVERY_SECONDS = BUILDER
            .comment("Seconds for toughness to fully recover after being chipped")
            .defineInRange("toughRecoverySeconds", 100, 10, 600);

    public static final ModConfigSpec.IntValue TOUGH_STUN_DURATION_TICKS = BUILDER
            .comment("Stun duration when toughness broken (ticks, 20 = 1s)")
            .defineInRange("toughStunDurationTicks", 200, 40, 600);

    public static final ModConfigSpec.IntValue TOUGHNESS_DEFAULT_HITS = BUILDER
            .comment("Default photos needed to break toughness")
            .defineInRange("toughnessDefaultHits", 5, 1, 100);

    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> TOUGHNESS_HITS_OVERRIDES = BUILDER
            .comment("Per-entity override: \"modid:entityid:count\"",
                    "Example: \"cataclysm:ignis:8\" (Ignis needs 8 hits); \"legendary_monsters:cloud_golem:3\" (Cloud Golem needs 3 hits)")
            .defineListAllowEmpty("toughnessHitsOverrides", java.util.List.of(
                    "legendary_monsters:overgrown_colossus:8",
                    "legendary_monsters:dune_sentinel:8",
                    "legendary_monsters:skeletosaurus:8",
                    "legendary_monsters:lava_eater:8",
                    "legendary_monsters:frostbitten_golem:8",
                    "legendary_monsters:ancient_guardian:8",
                    "block_factorys_bosses:yeti:8",
                    "legendary_monsters:withered_abomination:8",
                    "cataclysm:maledictus:9",
                    "cataclysm:netherite_monstrosity:9",
                    "cataclysm:scylla:9",
                    "cataclysm:ancient_remnant:9",
                    "cataclysm:the_harbinger:9",
                    "legendary_monsters:shulker_mimic:10",
                    "legendary_monsters:endersent:10",
                    "legendary_monsters:annihilation_pursuer:10",
                    "legendary_monsters:posessed_paladin:10",
                    "legendary_monsters:cloud_golem:10",
                    "minecraft:wither:10",
                    "minecraft:warden:10",
                    "cataclysm:the_leviathan:12",
                    "cataclysm:ender_guardian:12",
                    "minecraft:ender_dragon:12",
                    "cataclysm:ignis:20",
                    "block_factorys_bosses:underworld_knight:20",
                    "legendary_monsters:the_obliterator:20"
            ), () -> "", o -> o instanceof String);

    static { BUILDER.pop(); }

    // ==================== 韧性条渲染 ====================
    static { BUILDER.push("toughnessBar"); }

    public static final ModConfigSpec.IntValue TOUGH_BAR_WIDTH = BUILDER
            .comment("Boss toughness bar width (pixels)")
            .defineInRange("toughBarWidth", 32, 8, 200);

    public static final ModConfigSpec.IntValue TOUGH_BAR_HEIGHT = BUILDER
            .comment("Boss toughness bar height (pixels)")
            .defineInRange("toughBarHeight", 32, 8, 200);

    public static final ModConfigSpec.DoubleValue TOUGH_BAR_VERTICAL_OFFSET = BUILDER
            .comment("Boss toughness bar vertical offset above head (blocks)")
            .defineInRange("toughBarVerticalOffset", 0.5, 0.0, 5.0);

    static { BUILDER.pop(); }

    // ==================== 韧性目标过滤 ====================
    static { BUILDER.push("toughnessFilter"); }

    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> TOUGHNESS_BLACKLIST = BUILDER
            .comment("Entities that NEVER trigger toughness. Format: \"modid:entityid\"",
                    "Example: \"minecraft:iron_golem\" (Iron Golem excluded); \"minecraft:snow_golem\" (Snow Golem excluded)")
            .defineListAllowEmpty("toughnessBlacklist", java.util.List.of(
                    "block_factorys_bosses:kraken",
                    "block_factorys_bosses:underworld_knight",
                    "block_factorys_bosses:sandworm",
                    "block_factorys_bosses:infernal_dragon",
                    "twilightforest:lich",
                    "twilightforest:knight_phantom",
                    "twilightforest:ur_ghast",
                    "twilightforest:hydra",
                    "eternal_starlight:lunar_monstrosity",
                    "eternal_starlight:starlight_golem",
                    "minecraft:wither",
                    "minecraft:ender_dragon"
            ), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> TOUGHNESS_WHITELIST = BUILDER
            .comment("Entities that ALWAYS trigger toughness. Format: \"modid:entityid\"",
                    "Example: \"cataclysm:ignis\" (Ignis always has toughness); \"legendary_monsters:the_obliterator\" (Obliterator always has toughness)")
            .defineListAllowEmpty("toughnessWhitelist", java.util.List.of(), () -> "", o -> o instanceof String);

    static { BUILDER.pop(); }

    // ==================== 获取方式 ====================
    static { BUILDER.push("acquisition"); }

    public static final ModConfigSpec.BooleanValue ENABLE_BASIC_SOUL_DROP = BUILDER
            .comment("Basic soul drops from mobs (10% chance)")
            .define("enableBasicSoulDrop", true);

    public static final ModConfigSpec.BooleanValue ENABLE_BOSS_SOUL_DROP = BUILDER
            .comment("Boss soul drops from corresponding bosses (100% chance)")
            .define("enableBossSoulDrop", true);

    public static final ModConfigSpec.BooleanValue ENABLE_ENCHANTMENT_LOOT = BUILDER
            .comment("Soul Photography enchantment in dungeon loot and villager trades")
            .define("enableEnchantmentLoot", true);

    public static final ModConfigSpec.BooleanValue ENABLE_DIMENSIONAL_GUN_RECIPE = BUILDER
            .comment("Dimensional Gun crafting recipe")
            .define("enableDimensionalGunRecipe", true);

    public static final ModConfigSpec.BooleanValue ENABLE_GRAVITY_GUN_RECIPE = BUILDER
            .comment("Gravity Gun crafting recipe")
            .define("enableGravityGunRecipe", true);

    public static final ModConfigSpec.BooleanValue ENABLE_CONVERTER_RECIPE = BUILDER
            .comment("Converter crafting recipe")
            .define("enableConverterRecipe", true);

    public static final ModConfigSpec.BooleanValue ENABLE_SKILL_BALL_BOSS_LOOT = BUILDER
            .comment("Skill Ball drops from bosses (50% chance)")
            .define("enableSkillBallBossLoot", true);

    static { BUILDER.pop(); }

    static final ModConfigSpec SPEC = BUILDER.build();
}
