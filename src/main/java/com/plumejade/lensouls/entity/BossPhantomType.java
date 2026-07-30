package com.plumejade.lensouls.entity;

import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.effect.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BOSS 虚影幻灵类型枚举。
 * <p>
 * 映射六个 BOSS 镜魂到对应的幻灵参数：所属模组、实体注册名、元素、倍率、主色调。
 * 用于运行时检测模组是否加载、选择幻灵渲染风格和技能特效 tick。
 */
public enum BossPhantomType {

    // ========== 实体元数据 ==========
    // 格式: (modId, entityRegistryName, element, damageMultiplier, applySlowness, color, skillTick,
    //        spectatorBack, spectatorUp, skillDamage, skillRadius,
    //        className, modEntitiesClass, entityTypeFieldName, texturePath)

    // 常规 BOSS（观察位：往后 7.5 格，往上 8.5 格）
    IGNIS("cataclysm", "ignis", ElementDamage.FIRE, 3.0f, false, 0xFF4500, 30, 7.5, 8.5, 8.0f, 6.0f,
            "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity",
            "com.github.L_Ender.cataclysm.init.ModEntities", "IGNIS",
            "cataclysm:textures/entity/ignis/ignis_idle_0.png"),
    CLOUD_GOLEM("legendary_monsters", "cloud_golem", ElementDamage.WATER, 1.5f, true, 0x87CEEB, 28, 7.5, 8.5, 6.0f, 5.0f,
            "net.miauczel.legendary_monsters.entity.AnimatedMonster.IAnimatedBoss.CloudGolem.Cloud_GolemEntity",
            "net.miauczel.legendary_monsters.entity.ModEntities", "Cloud_golem",
            "legendary_monsters:textures/entity/cloud_golem/cloud_golem.png"),
    POSSESSED_PALADIN("legendary_monsters", "posessed_paladin", ElementDamage.EARTH, 1.5f, false, 0x8B4513, 32, 7.5, 8.5, 10.0f, 5.0f,
            "net.miauczel.legendary_monsters.entity.AnimatedMonster.IAnimatedBoss.PossessedPaladin.PossessedPaladinEntity",
            "net.miauczel.legendary_monsters.entity.ModEntities", "Posessed_Paladin",
            "legendary_monsters:textures/entity/posessed_paladin/new_posessed_paladin.png"),
    ENDER_GUARDIAN("cataclysm", "ender_guardian", ElementDamage.ENDER, 2.0f, false, 0x660099, 30, 7.5, 8.5, 12.0f, 7.0f,
            "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ender_Guardian_Entity",
            "com.github.L_Ender.cataclysm.init.ModEntities", "ENDER_GUARDIAN",
            "cataclysm:textures/entity/ender_guardian/new_ender_guardian.png"),
    // 大体型 BOSS（观察位：往后 12 格，往上 14 格）
    OBLITERATOR("legendary_monsters", "the_obliterator", ElementDamage.ENDER, 3.0f, false, 0x9933CC, 35, 12.0, 14.0, 15.0f, 8.0f,
            "net.miauczel.legendary_monsters.entity.AnimatedMonster.IAnimatedBoss.TheObliterator.TheObliteratorEntity",
            "net.miauczel.legendary_monsters.entity.ModEntities", "THE_OBLITERATOR",
            "legendary_monsters:textures/entity/the_warped_one/the_warped_one.png"),
    NETHERITE_MONSTROSITY("cataclysm", "netherite_monstrosity", ElementDamage.EARTH, 1.2f, false, 0xCC6600, 34, 12.0, 14.0, 20.0f, 8.0f,
            "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.NewNetherite_Monstrosity.Netherite_Monstrosity_Entity",
            "com.github.L_Ender.cataclysm.init.ModEntities", "NETHERITE_MONSTROSITY",
            "cataclysm:textures/entity/monstrosity/netherite_monstrosity.png"),

    // ========== 新 BOSS 镜魂（Twilight Forest — 借体驱动） ==========

    HYDRA("twilightforest", "hydra", ElementDamage.FIRE, 1.0f, false, 0x498cff, 30,
            12.0, 14.0, 6.0f, 5.0f,
            "twilightforest.entity.boss.Hydra",
            "twilightforest.init.TFEntities", "HYDRA",
            "twilightforest:textures/entity/hydra4.png"),
    KNIGHT_PHANTOM("twilightforest", "knight_phantom", ElementDamage.ENDER, 1.0f, false, 0x56ff91, 30,
            7.5, 8.5, 6.0f, 5.0f,
            "twilightforest.entity.boss.KnightPhantom",
            "twilightforest.init.TFEntities", "KNIGHT_PHANTOM",
            "twilightforest:textures/entity/knightphantom.png"),
    ALPHA_YETI("twilightforest", "alpha_yeti", ElementDamage.WATER, 1.0f, false, 0x56ff91, 30,
            7.5, 8.5, 6.0f, 5.0f,
            "twilightforest.entity.boss.AlphaYeti",
            "twilightforest.init.TFEntities", "ALPHA_YETI",
            "twilightforest:textures/entity/yetialpha.png"),
    NAGA("twilightforest", "naga", ElementDamage.EARTH, 1.0f, false, 0x56ff91, 30,
            7.5, 8.5, 6.0f, 5.0f,
            "twilightforest.entity.boss.Naga",
            "twilightforest.init.TFEntities", "NAGA",
            "twilightforest:textures/entity/nagahead.png"),

    // ========== 新 BOSS 镜魂（Legendary Monsters — 借体驱动） ==========

    LAVA_EATER("legendary_monsters", "lava_eater", ElementDamage.FIRE, 1.2f, false, 0x56ff91, 28,
            7.5, 8.5, 8.0f, 5.0f,
            "net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.Lava_eaterEntity",
            "net.miauczel.legendary_monsters.entity.ModEntities", "Lava_eater",
            "legendary_monsters:textures/entity/lava_eater.png"),

    // ========== 新 BOSS 镜魂（Cataclysm — 借体驱动） ==========

    THE_LEVIATHAN("cataclysm", "the_leviathan", ElementDamage.ENDER, 2.5f, false, 0x56ff91, 35,
            12.0, 14.0, 15.0f, 8.0f,
            "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Leviathan.The_Leviathan_Entity",
            "com.github.L_Ender.cataclysm.init.ModEntities", "THE_LEVIATHAN",
            "cataclysm:textures/entity/leviathan/the_leviathan.png"),
    SCYLLA("cataclysm", "scylla", ElementDamage.WATER, 1.8f, false, 0x90c8f3, 32,
            7.5, 8.5, 10.0f, 6.0f,
            "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.Scylla.Scylla_Entity",
            "com.github.L_Ender.cataclysm.init.ModEntities", "SCYLLA",
            "cataclysm:textures/entity/scylla/scylla_no_snake.png");

    // ========== 原始字段 ==========

    private final String modId;
    private final String entityRegistryName;
    private final ElementDamage element;
    private final float damageMultiplier;
    private final boolean applySlowness;
    private final int color;
    private final int skillTick;
    private final double spectatorBack;
    private final double spectatorUp;
    private final float skillDamage;
    private final float skillRadius;

    // ========== 借体驱动元数据 ==========

    private final String className;
    private final String modEntitiesClass;
    private final String entityTypeFieldName;
    private final String texturePath;

    // ========== 静态查询映射 ==========

    /** 类名 → BossPhantomType 映射 */
    private static final Map<String, BossPhantomType> CLASS_NAME_TO_TYPE = new ConcurrentHashMap<>();
    /** 类名 → 纹理 ResourceLocation 映射 */
    private static final Map<String, ResourceLocation> CLASS_NAME_TO_TEXTURE = new HashMap<>();
    /** 类名 → 元素色 ARGB 映射 */
    private static final Map<String, Integer> CLASS_NAME_TO_COLOR = new HashMap<>();

    private static volatile boolean mapsInitialized = false;

    private static void initMaps() {
        if (mapsInitialized) return;
        synchronized (BossPhantomType.class) {
            if (mapsInitialized) return;
            for (BossPhantomType t : values()) {
                if (t.className.isEmpty()) continue;
                CLASS_NAME_TO_TYPE.put(t.className, t);
                CLASS_NAME_TO_TEXTURE.put(t.className, ResourceLocation.parse(t.texturePath));
                CLASS_NAME_TO_COLOR.put(t.className, t.color);
            }
            mapsInitialized = true;
        }
    }

    // ========== 构造 & 查询 ==========

    BossPhantomType(String modId, String entityRegistryName, ElementDamage element,
                    float damageMultiplier, boolean applySlowness, int color, int skillTick,
                    double spectatorBack, double spectatorUp, float skillDamage, float skillRadius,
                    String className, String modEntitiesClass, String entityTypeFieldName, String texturePath) {
        this.modId = modId;
        this.entityRegistryName = entityRegistryName;
        this.element = element;
        this.damageMultiplier = damageMultiplier;
        this.applySlowness = applySlowness;
        this.color = color;
        this.skillTick = skillTick;
        this.spectatorBack = spectatorBack;
        this.spectatorUp = spectatorUp;
        this.skillDamage = skillDamage;
        this.skillRadius = skillRadius;
        this.className = className;
        this.modEntitiesClass = modEntitiesClass;
        this.entityTypeFieldName = entityTypeFieldName;
        this.texturePath = texturePath;
    }

    public String getModId()                           { return modId; }
    public String getEntityRegistryName()              { return entityRegistryName; }
    public ElementDamage getElement()                  { return element; }
    public float getDamageMultiplier()                 { return damageMultiplier; }
    public boolean shouldApplySlowness()               { return applySlowness; }
    public int getColor()                              { return color; }
    public int getSkillTick()                          { return skillTick; }
    public double getSpectatorBack()                   { return spectatorBack; }
    public double getSpectatorUp()                     { return spectatorUp; }
    public float getSkillDamage()                      { return skillDamage; }
    public float getSkillRadius()                      { return skillRadius; }

    // ========== 借体驱动元数据查询 ==========

    public String getClassName()                { return className; }
    public String getModEntitiesClass()         { return modEntitiesClass; }
    public String getEntityTypeFieldName()      { return entityTypeFieldName; }
    public String getTexturePath()              { return texturePath; }

    /** 该 BOSS 的所属模组是否已加载 */
    public boolean isModLoaded() {
        return ModList.get().isLoaded(modId);
    }

    /** 获取对应元素的隐藏效果 Holder */
    public Holder<MobEffect> getEffectHolder() {
        return switch (element) {
            case FIRE      -> ModEffects.FIRE_INFUSION;
            case WATER     -> ModEffects.WATER_INFUSION;
            case EARTH     -> ModEffects.EARTH_INFUSION;
            case ENDER     -> ModEffects.ENDER_INFUSION;
            case PROJECTILE -> ModEffects.FIRE_INFUSION;
        };
    }

    public int getAmplifier() {
        if (damageMultiplier >= 2.0f) return 3;
        if (damageMultiplier >= 1.5f) return 2;
        if (damageMultiplier >= 1.2f) return 1;
        return 0;
    }

    // ========== 静态工具方法 ==========

    /**
     * 判断指定类名是否为已注册的幻灵实体。
     */
    public static boolean isPhantomClassName(String className) {
        initMaps();
        return CLASS_NAME_TO_TYPE.containsKey(className);
    }

    /**
     * 根据幻灵实体类名获取对应纹理。
     */
    public static ResourceLocation getTextureForClass(String className) {
        initMaps();
        return CLASS_NAME_TO_TEXTURE.get(className);
    }

    /**
     * 根据幻灵实体类名获取元素色 (ARGB, alpha=255)。
     */
    public static int getColorForClass(String className) {
        initMaps();
        Integer c = CLASS_NAME_TO_COLOR.get(className);
        return c != null ? (0xFF << 24) | c : 0xFFFFFFFF;
    }

    public static BossPhantomType getTypeForClass(String className) {
        initMaps();
        return CLASS_NAME_TO_TYPE.get(className);
    }

    // ========== 实体初始化钩子 ==========

    /**
     * 借体实体构建后执行类型特定的初始化。
     * Ignis 需 blockingProgress + setIsBlocking。
     * KnightPhantom 需强制进入攻击态。
     * Hydra 需设 renderFakeHeads = true 让头作为主模型几何体渲染。
     */
    public void initEntity(Entity entity, ServerLevel level) {
        if (this == IGNIS) {
            initIgnis(entity);
        }
        if (this == KNIGHT_PHANTOM) {
            initKnightPhantom(entity);
        }
        if (this == HYDRA) {
            initHydra(entity);
        }
    }

    private static void initIgnis(Entity entity) {
        try {
            Class<?> ignisClass = Class.forName(
                    "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity");
            java.lang.reflect.Field bf = ignisClass.getField("blockingProgress");
            bf.set(entity, 10.0f);
            ignisClass.getMethod("setIsBlocking", boolean.class).invoke(entity, true);
        } catch (Exception e) {
            com.plumejade.lensouls.LenSouls.LOGGER.error("[幻灵] Ignis 初始化失败", e);
        }
    }

    private static void initKnightPhantom(Entity entity) {
        try {
            Class<?> clazz = Class.forName("twilightforest.entity.boss.KnightPhantom");
            // 装备剑
            if (entity instanceof net.minecraft.world.entity.LivingEntity le) {
                le.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new net.minecraft.world.item.ItemStack(
                                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                        net.minecraft.resources.ResourceLocation.parse("twilightforest:knightmetal_sword"))));
            }
            // 设置 currentFormation = ATTACK_PLAYER_ATTACK（ordinal=11）
            java.lang.reflect.Field formationField = clazz.getDeclaredField("currentFormation");
            formationField.setAccessible(true);
            Class<?> formationEnum = Class.forName("twilightforest.entity.boss.KnightPhantom$Formation");
            Object attackFormation = Enum.valueOf((Class<Enum>) formationEnum, "ATTACK_PLAYER_ATTACK");
            formationField.set(entity, attackFormation);
            // 重置 ticksProgress
            java.lang.reflect.Field ticksField = clazz.getDeclaredField("ticksProgress");
            ticksField.setAccessible(true);
            ticksField.setInt(entity, 0);
            // 设置 FLAG_CHARGING = true（SynchedEntityData，控制渲染尺寸+攻击力加成）
            var dataAccessorField = clazz.getDeclaredField("FLAG_CHARGING");
            dataAccessorField.setAccessible(true);
            var dataAccessor = (net.minecraft.network.syncher.EntityDataAccessor<Boolean>) dataAccessorField.get(null);
            entity.getEntityData().set(dataAccessor, true);
        } catch (Exception e) {
            com.plumejade.lensouls.LenSouls.LOGGER.error("[幻灵] KnightPhantom init 失败", e);
        }
    }

    private static void initHydra(Entity entity) {
        try {
            Class<?> clazz = Class.forName("twilightforest.entity.boss.Hydra");
            java.lang.reflect.Field f = clazz.getField("renderFakeHeads");
            f.setBoolean(entity, true);
        } catch (Exception e) {
            com.plumejade.lensouls.LenSouls.LOGGER.error("[幻灵] Hydra init 失败", e);
        }
    }

    // ========== 工厂方法 ==========

    public static BossPhantomType fromSoulItem(float damageMultiplier, boolean applySlowness, ElementDamage element) {
        for (BossPhantomType type : values()) {
            if (type.element == element
                    && Math.abs(type.damageMultiplier - damageMultiplier) < 0.01f
                    && type.applySlowness == applySlowness) {
                return type;
            }
        }
        return null;
    }

    /** 根据物品 descriptionId（如 item.lensouls.ignis_soul）查找 BOSS 类型 */
    public static BossPhantomType fromDescriptionId(String descId) {
        for (BossPhantomType type : values()) {
            String expected = "item.lensouls." + type.name().toLowerCase() + "_soul";
            if (expected.equals(descId)) return type;
        }
        return null;
    }
}
