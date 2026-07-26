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

    // ---- 镜魂配置 ----
    public static final ModConfigSpec.IntValue DEFAULT_DURATION = BUILDER
            .comment("Mirror soul infusion duration (seconds)")
            .defineInRange("defaultDuration", 30, 1, 3600);

    public static final ModConfigSpec.IntValue DEFAULT_COOLDOWN = BUILDER
            .comment("Basic soul cooldown (seconds)")
            .defineInRange("defaultCooldown", 60, 1, 3600);

    public static final ModConfigSpec.IntValue BOSS_COOLDOWN = BUILDER
            .comment("Boss soul cooldown (seconds)")
            .defineInRange("bossCooldown", 120, 1, 3600);

    // ---- 照片配置 ----
    public static final ModConfigSpec.DoubleValue PHOTO_BONUS = BUILDER
            .comment("Photo damage bonus multiplier (e.g. 1.2 = +120% damage)")
            .defineInRange("photoBonus", 1.2, 0.0, 10.0);

    // ---- 次元枪 (Dimensional Gun) ----
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
            .defineInRange("dgKillTarget", 200, 1, 10000);
    public static final ModConfigSpec.DoubleValue DG_MAX_DAMAGE = BUILDER
            .comment("Dimensional Gun: Max damage at max kills")
            .defineInRange("dgMaxDamage", 40.0, 1.0, 500.0);
    public static final ModConfigSpec.DoubleValue DG_MAX_ARMOR_PEN = BUILDER
            .comment("Dimensional Gun: Max armor penetration ratio (0-100%)")
            .defineInRange("dgMaxArmorPen", 80.0, 0.0, 100.0);
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

    // ---- 引力枪 (Gravity Gun) ----
    public static final ModConfigSpec.IntValue GG_COOLDOWN = BUILDER
            .comment("Gravity Gun: cooldown after each shot (ticks, 20 = 1s)")
            .defineInRange("ggCooldown", 4, 0, 100);

    public static final ModConfigSpec.DoubleValue GG_PULL_FORCE = BUILDER
            .comment("Gravity Gun: pull strength multiplier")
            .defineInRange("ggPullForce", 1.0, 0.1, 5.0);

    // ---- BOSS 韧性条 ----
    public static final ModConfigSpec.IntValue TOUGH_BAR_WIDTH = BUILDER
            .comment("Boss toughness bar size (pixels, 1:1 ratio)")
            .defineInRange("toughBarWidth", 32, 8, 200);

    public static final ModConfigSpec.IntValue TOUGH_BAR_HEIGHT = BUILDER
            .comment("Boss toughness bar height (pixels, matches width for 1:1)")
            .defineInRange("toughBarHeight", 32, 8, 200);

    public static final ModConfigSpec.DoubleValue TOUGH_BAR_VERTICAL_OFFSET = BUILDER
            .comment("Boss toughness bar vertical offset above head (blocks)")
            .defineInRange("toughBarVerticalOffset", 0.5, 0.0, 5.0);

    public static final ModConfigSpec.DoubleValue TOUGH_DAMAGE_REDUCTION = BUILDER
            .comment("Boss damage reduction when fully shielded (0-1, 0.8 = 80%)")
            .defineInRange("toughDamageReduction", 0.8, 0.0, 0.99);

    public static final ModConfigSpec.IntValue TOUGH_RECOVERY_SECONDS = BUILDER
            .comment("Seconds for toughness to fully recover after being chipped")
            .defineInRange("toughRecoverySeconds", 100, 10, 600);

    public static final ModConfigSpec.IntValue TOUGH_STUN_DURATION_TICKS = BUILDER
            .comment("Stun duration when toughness broken (ticks, 20 = 1s)")
            .defineInRange("toughStunDurationTicks", 200, 40, 600);

    public static final ModConfigSpec.DoubleValue TOUGH_PHOTOS_PER_20000HP = BUILDER
            .comment("Photos needed to break toughness for a 20000 HP boss")
            .defineInRange("toughPhotosPer20000HP", 10.0, 1.0, 100.0);

    // ---- 获取方式配置（默认开启，方便整合包魔改） ----
    public static final ModConfigSpec.BooleanValue ENABLE_BASIC_SOUL_DROP = BUILDER
            .comment("Enable basic soul drops from mobs (10% chance)")
            .define("enableBasicSoulDrop", true);

    public static final ModConfigSpec.BooleanValue ENABLE_BOSS_SOUL_DROP = BUILDER
            .comment("Enable boss soul drops from corresponding bosses (100% chance)")
            .define("enableBossSoulDrop", true);

    public static final ModConfigSpec.BooleanValue ENABLE_ENCHANTMENT_LOOT = BUILDER
            .comment("Enable Soul Photography enchantment in dungeon loot and villager trades")
            .define("enableEnchantmentLoot", true);

    public static final ModConfigSpec.BooleanValue ENABLE_DIMENSIONAL_GUN_RECIPE = BUILDER
            .comment("Enable Dimensional Gun crafting recipe")
            .define("enableDimensionalGunRecipe", true);

    public static final ModConfigSpec.BooleanValue ENABLE_GRAVITY_GUN_RECIPE = BUILDER
            .comment("Enable Gravity Gun crafting recipe")
            .define("enableGravityGunRecipe", true);

    public static final ModConfigSpec.BooleanValue ENABLE_CONVERTER_RECIPE = BUILDER
            .comment("Enable Converter crafting recipe")
            .define("enableConverterRecipe", true);

    public static final ModConfigSpec.BooleanValue ENABLE_SKILL_BALL_BOSS_LOOT = BUILDER
            .comment("Enable Skill Ball drops from bosses (50% chance)")
            .define("enableSkillBallBossLoot", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}
