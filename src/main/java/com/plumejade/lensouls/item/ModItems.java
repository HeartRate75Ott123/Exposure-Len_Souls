package com.plumejade.lensouls.item;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.damage.ElementDamage;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组物品注册表。
 */
public class ModItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LenSouls.MODID);

    // ---- 基础元素镜魂 ----
    public static final DeferredItem<Item> FIRE_SOUL = ITEMS.register("fire_soul",
            () -> new LensoulItem(ElementDamage.FIRE, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> WATER_SOUL = ITEMS.register("water_soul",
            () -> new LensoulItem(ElementDamage.WATER, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> EARTH_SOUL = ITEMS.register("earth_soul",
            () -> new LensoulItem(ElementDamage.EARTH, new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> ENDER_SOUL = ITEMS.register("ender_soul",
            () -> new LensoulItem(ElementDamage.ENDER, new Item.Properties().stacksTo(1)));

    // ---- BOSS 专属镜魂（冷却 120 秒，等级对齐星级） ----
    /** 焰魔镜魂（火 — 5星级 → 等级 V） */
    public static final DeferredItem<Item> IGNIS_SOUL = ITEMS.register("ignis_soul",
            () -> new LensoulItem(ElementDamage.FIRE, 3.0f, false, 120, new Item.Properties().stacksTo(1)));

    /** 云筑魔像镜魂（冰/水，攻击减速 — 3星级 → 等级 III） */
    public static final DeferredItem<Item> CLOUD_GOLEM_SOUL = ITEMS.register("cloud_golem_soul",
            () -> new LensoulItem(ElementDamage.WATER, 1.5f, true, 120, new Item.Properties().stacksTo(1)));

    /** 堕落圣骑镜魂（土 — 3星级 → 等级 III） */
    public static final DeferredItem<Item> POSSESSED_PALADIN_SOUL = ITEMS.register("possessed_paladin_soul",
            () -> new LensoulItem(ElementDamage.EARTH, 1.5f, false, 120, new Item.Properties().stacksTo(1)));

    /** 湮灭构造体镜魂（末影 — 5星级 → 等级 V） */
    public static final DeferredItem<Item> OBLITERATOR_SOUL = ITEMS.register("obliterator_soul",
            () -> new LensoulItem(ElementDamage.ENDER, 3.0f, false, 120, new Item.Properties().stacksTo(1)));

    /** 末影守卫镜魂（末影 — 4星级 → 等级 IV） */
    public static final DeferredItem<Item> ENDER_GUARDIAN_SOUL = ITEMS.register("ender_guardian_soul",
            () -> new LensoulItem(ElementDamage.ENDER, 2.0f, false, 120, new Item.Properties().stacksTo(1)));

    /** 下界合金巨兽镜魂（土 — 2星级 → 等级 II） */
    public static final DeferredItem<Item> NETHERITE_MONSTROSITY_SOUL = ITEMS.register("netherite_monstrosity_soul",
            () -> new LensoulItem(ElementDamage.EARTH, 1.2f, false, 120, new Item.Properties().stacksTo(1)));

    // ---- 暮色 BOSS 镜魂 ----
    public static final DeferredItem<Item> HYDRA_SOUL = ITEMS.register("hydra_soul",
            () -> new LensoulItem(ElementDamage.FIRE, 1.0f, false, 120, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> KNIGHT_PHANTOM_SOUL = ITEMS.register("knight_phantom_soul",
            () -> new LensoulItem(ElementDamage.ENDER, 1.0f, false, 120, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ALPHA_YETI_SOUL = ITEMS.register("alpha_yeti_soul",
            () -> new LensoulItem(ElementDamage.WATER, 1.0f, false, 120, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> NAGA_SOUL = ITEMS.register("naga_soul",
            () -> new LensoulItem(ElementDamage.EARTH, 1.0f, false, 120, new Item.Properties().stacksTo(1)));
    // ---- 传奇怪物 ----
    public static final DeferredItem<Item> LAVA_EATER_SOUL = ITEMS.register("lava_eater_soul",
            () -> new LensoulItem(ElementDamage.FIRE, 1.2f, false, 120, new Item.Properties().stacksTo(1)));
    // ---- 灾变 ----
    public static final DeferredItem<Item> THE_LEVIATHAN_SOUL = ITEMS.register("the_leviathan_soul",
            () -> new LensoulItem(ElementDamage.ENDER, 2.5f, false, 120, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SCYLLA_SOUL = ITEMS.register("scylla_soul",
            () -> new LensoulItem(ElementDamage.WATER, 1.8f, false, 120, new Item.Properties().stacksTo(1)));

    // ---- 转换器 ----
    public static final DeferredItem<Item> CONVERTER = ITEMS.register("converter",
            () -> new ConverterItem(new Item.Properties().stacksTo(1)));

    // ---- 能力球 ----
    /** 随机能力球：右键获得随机能力 */
    public static final DeferredItem<Item> SKILL_BALL = ITEMS.register("skill_ball",
            () -> new SkillBallItem(new Item.Properties().stacksTo(64)));

    /** 弱点透镜能力球 */
    public static final DeferredItem<Item> WEAKNESS_LENS_BALL = ITEMS.register("weakness_lens_ball",
            () -> new SkillBallItem(AbilityType.WEAKNESS_LENS, new Item.Properties().stacksTo(64)));

    /** 空间扭曲能力球 */
    public static final DeferredItem<Item> SPATIAL_WARP_BALL = ITEMS.register("spatial_warp_ball",
            () -> new SkillBallItem(AbilityType.SPATIAL_WARP, new Item.Properties().stacksTo(64)));

    /** 时空回溯能力球 */
    public static final DeferredItem<Item> TEMPORAL_RECALL_BALL = ITEMS.register("temporal_recall_ball",
            () -> new SkillBallItem(AbilityType.TEMPORAL_RECALL, new Item.Properties().stacksTo(64)));

    /** 时间定格能力球 */
    public static final DeferredItem<Item> TIME_STOP_BALL = ITEMS.register("time_stop_ball",
            () -> new SkillBallItem(AbilityType.TIME_STOP, new Item.Properties().stacksTo(64)));

    /** 要害打击能力球 */
    public static final DeferredItem<Item> VITAL_STRIKE_BALL = ITEMS.register("vital_strike_ball",
            () -> new SkillBallItem(AbilityType.VITAL_STRIKE, new Item.Properties().stacksTo(64)));

    /** 夺魂索命能力球 */
    public static final DeferredItem<Item> SOUL_SEVER_BALL = ITEMS.register("soul_sever_ball",
            () -> new SkillBallItem(AbilityType.SOUL_SEVER, new Item.Properties().stacksTo(64)));

    /** 能力窃取能力球 */
    public static final DeferredItem<Item> ABILITY_STEAL_BALL = ITEMS.register("ability_steal_ball",
            () -> new SkillBallItem(AbilityType.ABILITY_STEAL, new Item.Properties().stacksTo(64)));

    // ---- 次元枪 ----
    public static final DeferredItem<Item> DIMENSIONAL_GUN = ITEMS.register("dimensional_gun",
            () -> new DimensionalGunItem(new Item.Properties().stacksTo(1).fireResistant()));

    // ---- 引力枪 ----
    public static final DeferredItem<Item> GRAVITY_GUN = ITEMS.register("gravity_gun",
            () -> new GravityGunItem(new Item.Properties().stacksTo(1)));

    // ---- 相机镜头配件 ----
    public static final DeferredItem<LensItem> LENS_TIER_1 = ITEMS.register("lens_tier_1",
            () -> new LensItem(1, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LensItem> LENS_TIER_2 = ITEMS.register("lens_tier_2",
            () -> new LensItem(2, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LensItem> LENS_TIER_3 = ITEMS.register("lens_tier_3",
            () -> new LensItem(3, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LensItem> LENS_TIER_4 = ITEMS.register("lens_tier_4",
            () -> new LensItem(4, new Item.Properties().stacksTo(1)));

    // ---- 实体照片饰品 ----
    public static final DeferredItem<EntityPhotographItem> ENTITY_PHOTOGRAPH =
            ITEMS.register("entity_photograph", () -> new EntityPhotographItem(new Item.Properties().stacksTo(1)));

    // ---- 回复药水 ----
    public static final DeferredItem<HealPotionItem> HEAL_POTION = ITEMS.register("heal_potion",
            () -> new HealPotionItem(new Item.Properties()));

    // ---- 复制之魂 ----
    public static final DeferredItem<CopySoulItem> COPY_SOUL = ITEMS.register("copy_soul",
            () -> new CopySoulItem(new Item.Properties()));

    // ---- 羽·元素觉醒者（Curios 任意槽位饰品） ----
    public static final DeferredItem<FeatherElementRiseItem> FEATHER_ELEMENTRISE =
            ITEMS.register("feather_elementrise", () -> new FeatherElementRiseItem(new Item.Properties()));

    // ---- 子弹物品（仅用于渲染弹射物模型） ----
    public static final DeferredItem<Item> OVERWORLD_BULLET = ITEMS.register("overworld_bullet",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> HELL_BULLET = ITEMS.register("hell_bullet",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> ENDER_BULLET = ITEMS.register("ender_bullet",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> DRAG_BULLET = ITEMS.register("drag_bullet",
            () -> new Item(new Item.Properties()));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
