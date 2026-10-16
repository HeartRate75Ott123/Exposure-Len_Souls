package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.feather.CurseDef;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import com.plumejade.lensouls.feather.FeatherEquip;
import com.plumejade.lensouls.item.CopySoulItem;
import com.plumejade.lensouls.item.ModItems;
import com.plumejade.lensouls.mixin.EntityInvulnerableTimeAccessor;
import com.plumejade.lensouls.network.AbyssCountdownPacket;
import com.plumejade.lensouls.network.TwistSyncPacket;
import com.plumejade.lensouls.particle.ModParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 羽·折翼沉渊效果处理器。
 * <p>
 * 佩戴检测：Curios 任意槽位（findFirstCurio 遍历所有槽）。
 * 机制：
 * <ul>
 *   <li>虚无的承诺：手持终末立方体（legendary_monsters:the_tesseract）时强制切创造，松开/登出恢复原模式</li>
 *   <li>神秘雪球：怪物伤你时环形发射 8 个雪球（3 点魔法伤害）</li>
 *   <li>安魂曲：实时按游玩天数 maxHealth -1（最低 1），全局受伤 +13%/天（反转后 +1/天、-13%/天）</li>
 *   <li>愚钝：经验减半</li>
 *   <li>自闭：16 格内生物加入世界 → 0.5 魔法伤害 + 护甲层(×0.75/层, 3s)</li>
 *   <li>厄运：挖方块 0.8% 生成 15 蠹虫；每分钟 2 个不重复 debuff（20s, 等级3-20）</li>
 *   <li>祸之可能性：充能 15min（扭曲值越高越短，下限 2min），充能完毕且身边 7 格内 5+ 怪物时引动末影龙之吼 + 3s 倒计时，随后召唤 5 只（僵尸/骷髅/洞穴蜘蛛其一）</li>
 *   <li>挫败：受击攻击面板 ×0.94/层（5s）</li>
 *   <li>食之无味：由 FoodDataMixin 阻断食物回血</li>
 *   <li>失忆：由 CopySoulItem 封印禁复制之魂合成（配方层拦截，掉落保留）</li>
 *   <li>疯狂：合成 +1 扭曲值（上限 200），受击 +0.2%/点、造成 +1%/点，死亡归零，击杀怪物 3% 降 1</li>
 *   <li>恶意：元素附加伤害 +12%（DamageHandler 内应用）</li>
 * </ul>
 * <p>
 * <b>反转条件 / 反转态</b>（12 条 · 可反转 11；e1「虚无的承诺」无反转，见
 * {@link CurseDefs#ABYSS} 的 noReverse）：逐条走 {@link CurseManager#on}（诅咒态）/
 * {@link CurseManager#rev}（反转态）门控，条件进度用 {@link CurseManager#tick} 累加、
 * 达标自动反转。叠加口径见设计文档 §2：<b>诅咒态乘算、反转态加算</b>（e8 的「每层 ×1.06」
 * 按文案本身乘算）。条目索引 0 基，与 lang 的 {@code eN} 一致：
 * <pre>
 * e1  虚无的承诺(0)  无反转
 * e2  神秘雪球(1)    反转条件=累计受到 100 次敌对雪球伤害 → 伤害 1 点、数量 3 个
 * e3  安魂曲(2)      条件=天数&lt;5 且生命上限&gt;20 → 每天最大生命 +1、受伤 −13%/天
 * e4  愚钝(3)        条件=一次性获取 2000 点经验 → 经验 ×2
 * e5  自闭(4)        条件=16 格内同 tick 加入 50 只生物且非旁观、存活 → 护甲每层 +5%
 * e6  厄运(5)        条件=累计挖掘 200 个方块 → 不再生蠹虫、每分钟改给 2 个正面效果
 * e7  祸之可能性(6)  条件=累计击杀召唤物 100 只 → 不再召唤，改为 7 格落雷 15 点魔法伤害
 * e8  挫败(7)        条件=挫败层数 ≥3 时击杀 1 只 BOSS → 每层 ×1.06
 * e9  食之无味(8)    条件=累计食用 50 种不同食物 → 进食回血 ×2
 * e10 失忆(9)        条件=空手击杀湮灭构造体 → 复制之魂解禁
 * e11 疯狂(10)       条件=移速&gt;4 时一击杀死最大生命 ≥6 万的生物 → 每点受伤 −0.2%
 * e12 恶意(11)       条件=承受一次 ≥50 点元素附加伤害并存活 → 受到的元素附加伤害 −12%
 * </pre>
 * 生命周期防护：登录同步/清残留、登出恢复创造并清内存层、死亡清层+扭曲归零，
 * 崩溃重进由每 tick 自愈 + 持久化原模式键兜底。
 */
public class FeatherAbyssHandler {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("lensouls.abyss");

    public static final String KEY_TWIST = "lensouls:abyss_twist";
    public static final String KEY_GM_BEFORE = "lensouls:abyss_gm_before";
    private static final String KEY_DOOM_TIMER = "lensouls:abyss_doom_timer";
    private static final String KEY_CHARGE_STATE = "lensouls:abyss_charge_state"; // 0 充能中 1 已充能待触发 2 倒计时中
    private static final String KEY_CHARGE_NEXT = "lensouls:abyss_charge_next";    // 充能完成 gameTime
    private static final String KEY_CHARGE_INTERVAL = "lensouls:abyss_charge_interval"; // 本次充能使用的间隔 tick（扭曲值变化时按进度比例迁移）
    private static final String KEY_COUNTDOWN_NEXT = "lensouls:abyss_countdown_next";
    private static final String SNOWBALL_TAG = "lensouls:feather_snowball";

    /** 疯狂：扭曲值上限 200 */
    public static final int MAX_TWIST = 200;
    /** 安魂曲：每过一天受到的伤害 +13%（按天累加） */
    public static final float REQUIEM_TAKEN_PER_DAY = 0.13f;
    /** 厄运：挖方块触发概率（0.8%） */
    public static final float DOOM_CHANCE = 0.008f;
    /** 厄运：每分钟 debuff 间隔（1200 tick） */
    public static final int DOOM_INTERVAL = 1200;
    /** 厄运：debuff 时长 20s */
    public static final int DOOM_DURATION = 400;
    /** 祸之可能性：充能基础间隔 15min（扭曲值越高越短，下限 2min） */
    public static final int CHARGE_INTERVAL_BASE = 18000;
    /** 祸之可能性：充能最短间隔 2min */
    public static final int CHARGE_INTERVAL_MIN = 2400;
    /** 祸之可能性：充能完毕到生成的 3s 倒计时 */
    public static final int COUNTDOWN_TICKS = 60;
    /** 祸之可能性：召唤落点方形半边长（以玩家为中心 ±4 格） */
    public static final double SUMMON_HALF = 4.0;
    /** 祸之可能性：粒子显形后延迟 1s 于落点生成怪物 */
    public static final int SUMMON_DELAY_TICKS = 20;
    /** 自闭：护甲层时长 3s */
    private static final long ARMOR_STACK_TICKS = 60L;
    /** 挫败：攻击层时长 5s */
    private static final long ATTACK_STACK_TICKS = 100L;

    private static final ResourceLocation REQUIEM_MODIFIER = ResourceLocation.parse("lensouls:abyss_requiem_health");
    private static final ResourceLocation ARMOR_MODIFIER = ResourceLocation.parse("lensouls:abyss_armor");
    private static final ResourceLocation ATTACK_MODIFIER = ResourceLocation.parse("lensouls:abyss_attack");

    /** 内存层（自闭护甲层 / 挫败攻击层）：UUID → 到期 gameTime 列表 */
    private static final Map<UUID, List<Long>> ARMOR_STACKS = new HashMap<>();
    private static final Map<UUID, List<Long>> ATTACK_STACKS = new HashMap<>();

    private static final Holder<MobEffect>[] DOOM_DEBUFFS = new Holder[]{
            MobEffects.HUNGER, MobEffects.BLINDNESS, MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.DIG_SLOWDOWN, MobEffects.POISON, MobEffects.WEAKNESS, MobEffects.LEVITATION};

    private static final EntityType<?>[] DISASTER_TYPES = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CAVE_SPIDER};

    // ===================== ④ 反转条件 / 反转态（12 条，e1 无反转） =====================

    /** 本款诅咒定义（掩码偏移 8） */
    private static final CurseDef DEF = CurseDefs.ABYSS;

    // ---- 条目索引（0 基，与 lang 的 eN 一一对应）----
    private static final int E1_COMMITMENT = 0;      // 虚无的承诺（无反转）
    private static final int E2_SNOWBALL = 1;        // 神秘雪球
    private static final int E3_REQUIEM = 2;         // 安魂曲
    private static final int E4_DULLNESS = 3;        // 愚钝
    private static final int E5_AUTISM = 4;          // 自闭
    private static final int E6_DOOM = 5;            // 厄运
    private static final int E7_CALAMITY = 6;        // 祸之可能性
    private static final int E8_FRUSTRATION = 7;     // 挫败
    private static final int E9_TASTELESS = 8;       // 食之无味
    private static final int E10_AMNESIA = 9;        // 失忆
    private static final int E11_MADNESS = 10;       // 疯狂
    private static final int E12_MALICE = 11;        // 恶意

    // ---- 反转条件目标值（lang 的 .cond 文案）----
    /** e2：累计受到 100 次该种敌对雪球的伤害 */
    private static final long E2_HIT_GOAL = 100L;
    /** e3：生命上限突破 20 */
    private static final float E3_MAX_HEALTH_GOAL = 20.0f;
    /** e3：游戏天数 ≥ 该值后过期，无法再反转 */
    private static final int E3_DAY_LIMIT = 5;
    /** e4：一次性获取 2000 点经验 */
    private static final int E4_XP_GOAL = 2000;
    /** e5：16 格内一次性（同一 tick）加入 50 只生物 */
    private static final long E5_BATCH_GOAL = 50L;
    /** e6：累计挖掘 200 个方块 */
    private static final long E6_BLOCK_GOAL = 200L;
    /** e7：累计击杀由祸之可能性召唤的怪物 100 只 */
    private static final long E7_SUMMON_KILL_GOAL = 100L;
    /** e8：挫败层数 ≥3 */
    private static final int E8_STACK_GOAL = 3;
    /** e9：累计食用 50 种不同食物 */
    private static final int E9_FOOD_KIND_GOAL = 50;
    /** e10：湮灭构造体实体 id（空手击杀即可反转） */
    private static final String ANNIHILATION_PURSUER = "legendary_monsters:annihilation_pursuer";
    /** e11：目标最大生命 ≥6 万 */
    private static final float E11_TARGET_MAX_HEALTH = 60000.0f;
    /** e11：移速阈值 4（格/秒 = 水平速度 × 20） */
    private static final double E11_SPEED_THRESHOLD = 4.0;
    /** e11：「移速大于 4」的判定窗口（tick）：致命一击落在最近 1 秒内的移动状态下即算 */
    private static final long E11_SPEED_WINDOW = 20L;
    /** e12：一次元素附加伤害 ≥50 点 */
    private static final float E12_ELEMENT_BONUS_GOAL = 50.0f;

    // ---- 反转态效果数值（lang 的 .after 文案）----
    /** e2：雪球伤害 3 点（诅咒态）/ 1 点（反转态） */
    public static final float SNOWBALL_DAMAGE_CURSE = 3.0f;
    public static final float SNOWBALL_DAMAGE_REVERSED = 1.0f;
    /** e2：雪球数量 8 个（诅咒态）/ 3 个（反转态） */
    public static final int SNOWBALL_COUNT_CURSE = 8;
    public static final int SNOWBALL_COUNT_REVERSED = 3;
    /** e5 反转：护甲每层 +5%（**加算**，§2；诅咒态为 ×0.75/层 乘算） */
    public static final double ARMOR_STACK_REVERSED_BONUS = 0.05;
    /** e6 反转：每分钟改为获得 2 个正面效果（20 秒） */
    private static final int DOOM_BOON_COUNT = 2;
    /** e7 反转：范围落雷半径 7 格 */
    public static final double CALAMITY_LIGHTNING_RADIUS = 7.0;
    /** e7 反转：落雷对每只怪物 15 点魔法伤害 */
    public static final float CALAMITY_LIGHTNING_DAMAGE = 15.0f;
    /** e8：每层 ×0.94（诅咒态）/ ×1.06（反转态）——按文案「每层 ×」乘算 */
    public static final double ATTACK_STACK_CURSE = 0.94;
    public static final double ATTACK_STACK_REVERSED = 1.06;
    /** e11：每点扭曲值造成伤害 +1%（诅咒态与反转态相同） */
    public static final float TWIST_DEALT_PER_POINT = 0.01f;
    /** e11：每点扭曲值受到伤害 +0.2%（诅咒态）/ −0.2%（反转态） */
    public static final float TWIST_TAKEN_PER_POINT = 0.002f;
    /** e12：受到的元素附加伤害 +12%（诅咒态，由 DamageHandler 施加）/ −12%（反转态） */
    public static final float ELEMENT_TAKEN_CURSE = 1.12f;
    public static final float ELEMENT_TAKEN_REVERSED = 0.88f;

    /** e7：召唤物标记（用于「击杀由祸之可能性召唤的怪物」计数） */
    private static final String KEY_SUMMON_TAG = "lensouls:abyss_summon";
    /** e7：召唤物归属玩家 UUID */
    private static final String KEY_SUMMON_OWNER = "lensouls:abyss_summon_owner";
    /** e9：已食用过的食物种类（持久化，跨死亡保留） */
    private static final String KEY_FOOD_KINDS = "lensouls:abyss_food_kinds";
    /** e2：反转态雪球标记（1 点伤害 / 3 个） */
    private static final String KEY_SNOWBALL_WEAK = "lensouls:abyss_snowball_weak";

    /** 「且存活」类条件的待结算条目（e5 / e12）：UUID → 条目索引列表 */
    private static final Map<UUID, List<Integer>> PENDING_REVERSE = new HashMap<>();
    /** 待结算的最早时刻（gameTime）：晚 2 tick 结算，使「存活」成立 */
    private static final Map<UUID, Long> PENDING_AT = new HashMap<>();
    /** e11：一击必杀候选（目标实体 id → 致命一击的 gameTime） */
    private static final Map<Integer, Long> ONESHOT_KILL = new HashMap<>();
    /** e11：最近一次「移速 > 4」的到期 gameTime */
    private static final Map<UUID, Long> FAST_MOVE_UNTIL = new HashMap<>();
    /** e5：同一 tick 内加入的生物计数（UUID → {gameTime, count}） */
    private static final Map<UUID, long[]> JOIN_BATCH = new HashMap<>();
    /** e12：本次受伤我们施加的倍率（玩家实体 id → 倍率），供 LOWEST 阶段还原元素附加伤害 */
    private static final Map<Integer, Float> TAKEN_FACTOR = new HashMap<>();

    /** e6 厄运 反转：每分钟改给的正面效果池（轮流抽 2 个不重复） */
    private static final Holder<MobEffect>[] DOOM_BOONS = new Holder[]{
            MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED, MobEffects.DAMAGE_BOOST, MobEffects.JUMP,
            MobEffects.REGENERATION, MobEffects.DAMAGE_RESISTANCE, MobEffects.FIRE_RESISTANCE,
            MobEffects.NIGHT_VISION};

    /**
     * 佩戴检测：只认本模组羽毛界面的 7 个槽位（见 {@link FeatherEquip}，已脱离 Curios）。
     * <p>同种物品只生效一次（布尔语义，重复持有不叠加）。
     * <p>等价于 {@code CurseManager.isActive(player, CurseDefs.ABYSS)}；本方法保留是因为
     * {@code DamageHandler} / {@code CopySoulSealHandler} / {@code FoodDataMixin} / HUD 仍在调用它。
     */
    public static boolean hasAbyss(Player player) {
        return FeatherEquip.has(player, ModItems.FEATHER_ABYSS.get());
    }

    /** 跨死亡持久化子键（NeoForge 复活只复制 PlayerPersisted 子键） */
    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void writeBack(Player player, CompoundTag tag) {
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    // ========== 疯狂：扭曲值（上限 200，复用扭曲值 HUD） ==========

    public static int getTwist(Player player) {
        if (player == null) return 0;
        return Math.max(0, Math.min(MAX_TWIST, persisted(player).getInt(KEY_TWIST)));
    }

    public static void setTwist(ServerPlayer player, int value) {
        int clamped = Math.max(0, Math.min(MAX_TWIST, value));
        CompoundTag tag = persisted(player);
        tag.putInt(KEY_TWIST, clamped);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
        // 复用现有扭曲值 HUD：按上限 200 等比例换算为 0-100 显示值
        TwistSyncPacket.send(player, Math.round(clamped / 2.0f));
    }

    public static void addTwist(ServerPlayer player, int delta) {
        setTwist(player, getTwist(player) + delta);
    }

    // ========== 每 tick 驱动 ==========

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            tick(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ 每 tick 结算异常", t);
        }
    }

    /**
     * 每 tick 主循环：诅咒态 / 反转态一律逐条走 {@link CurseManager#on} / {@link CurseManager#rev}
     * 门控（条目索引见类 javadoc）。
     */
    private static void tick(ServerPlayer player) {
        boolean has = hasAbyss(player);
        // e1 虚无的承诺（无反转）：每 tick 响应手持终末立方体
        updateAutoCreative(player, has && CurseManager.on(player, DEF, E1_COMMITMENT));
        if (has) {
            // e3 安魂曲：每天 −1（诅咒态）/ +1（反转态）最大生命上限
            applyRequiem(player, CurseManager.rev(player, DEF, E3_REQUIEM));
            // e11 条件：记录「移速 > 4」的时间窗
            trackFastMovement(player);
            // e5 / e12 的「且存活」待结算
            resolvePending(player);
        } else {
            removeRequiem(player);
            clearTransient(player.getUUID());
        }
        if (player.tickCount % 20 != 0) return;
        if (has) {
            refreshStackModifiers(player);
            advanceTimers(player);
            // e3 条件：游戏天数 <5 时生命上限突破 20（过期无法反转）
            checkRequiemCondition(player);
        } else {
            removeStackModifiers(player);
            resetTimers(player);
        }
    }

    /** e3 条件：游戏天数小于 5 天时，生命上限突破 20；过期（≥5 天）无法反转 */
    private static void checkRequiemCondition(ServerPlayer player) {
        if (!CurseManager.on(player, DEF, E3_REQUIEM)) return;
        if (getSaveDays(player) >= E3_DAY_LIMIT) return;
        if (player.getMaxHealth() > E3_MAX_HEALTH_GOAL) {
            CurseManager.reach(player, DEF, E3_REQUIEM);
        }
    }

    // ========== 虚无的承诺：手持超立方体切创造 ==========

    private static boolean isHoldingTesseract(Player player) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("legendary_monsters", "the_tesseract");
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) return false;
        return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
    }

    /** 每 tick 自愈：手持+佩戴 → 创造（持久化原模式）；否则恢复原模式 */
    private static void updateAutoCreative(ServerPlayer player, boolean has) {
        boolean holding = isHoldingTesseract(player);
        GameType current = player.gameMode.getGameModeForPlayer();
        CompoundTag tag = persisted(player);
        if (has && holding) {
            if (!current.isCreative()) {
                tag.putInt(KEY_GM_BEFORE, current.getId());
                writeBack(player, tag);
                player.setGameMode(GameType.CREATIVE);
            }
        } else if (tag.contains(KEY_GM_BEFORE)) {
            int prev = tag.getInt(KEY_GM_BEFORE);
            GameType prevType = GameType.byId(prev);
            tag.remove(KEY_GM_BEFORE);
            writeBack(player, tag);
            if (prevType != null && current != prevType) {
                player.setGameMode(prevType);
            }
        }
    }

    // ========== 安魂曲：maxHealth ∓1/天（下限1）+ 受到的伤害每天 +13%（反转 −13%） ==========

    /** 安魂曲天数：镜像 InControl——主世界昼夜转换计数（SavedData 持久化，睡一觉 +1） */
    private static int getSaveDays(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return 0;
        return FeatherAbyssDayData.getData(server).getDaycounter();
    }

    /** 每 tick 推进安魂曲天数计数（主世界昼夜转换，睡一觉/过一夜 +1）+ 推进祸之可能性收束队列 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        try {
            MinecraftServer server = event.getServer();
            if (server == null) return;
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld == null) return;
            FeatherAbyssDayData.getData(server).tick(overworld);
            processSummonScheduler();
            pruneOneShotCandidates(overworld.getGameTime());
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ 服务器刻结算异常", t);
        }
    }

    /**
     * e3 安魂曲：按「已过天数」调整最大生命上限（ADD_VALUE 瞬态修饰符，不动基值）。
     * <ul>
     *   <li>诅咒态：每天 <b>−1</b>（下限 1）；</li>
     *   <li>反转态：每天 <b>+1</b>（同一修饰符取正值）。</li>
     * </ul>
     * 扣量 = min(天数, 基础上限 − 1)。用「自然上限」做锚：当前 value 已含本修饰符，
     * 故自然 = value − amount；这样扣量随其它来源的基值变动自适应，且从不触碰 setBaseValue。
     * 仅在天数/状态导致目标值变化时重建修饰符；死亡重生后本修饰符丢失，但本方法
     * 每 tick 由佩戴状态驱动重建——无需任何持久化标记/缓存。
     */
    private static void applyRequiem(ServerPlayer player, boolean reversed) {
        int days = getSaveDays(player);
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
        if (max == null) {
            LOGGER.info("[AbyssRequiem] maxHealth attribute null");
            return;
        }
        AttributeModifier mod = max.getModifier(REQUIEM_MODIFIER);
        double current = mod == null ? 0.0 : mod.amount();
        // 自然上限 = 当前 value − 本修饰符的贡献
        double natural = max.getValue() - current;
        double want;
        if (reversed) {
            want = Math.max(0.0, days);                                        // 反转：每天 +1
        } else {
            want = -Math.max(0.0, Math.min(days, Math.floor(natural - 1.0)));   // 诅咒：每天 −1，最终上限 ≥1
        }

        if (Math.abs(want) < 0.001) {
            if (mod != null) {
                max.removeModifier(REQUIEM_MODIFIER);
                LOGGER.info("[AbyssRequiem] removed days={} reversed={}", days, reversed);
            }
            return;
        }
        if (mod == null || Math.abs(mod.amount() - want) > 0.001) {
            max.removeModifier(REQUIEM_MODIFIER);
            max.addTransientModifier(
                    new AttributeModifier(REQUIEM_MODIFIER, want, AttributeModifier.Operation.ADD_VALUE));
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
            LOGGER.info("[AbyssRequiem] apply days={} natural={} amount={} reversed={}",
                    days, natural, want, reversed);
        }
    }

    /** 取下羽毛：移除扣血修饰符，恢复基础最大生命（不动基值）。 */
    private static void removeRequiem(ServerPlayer player) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
        if (max != null && max.getModifier(REQUIEM_MODIFIER) != null) {
            max.removeModifier(REQUIEM_MODIFIER);
        }
    }

    // ========== 自闭 / 挫败：分层乘算属性 ==========

    private static void addArmorStack(ServerPlayer player) {
        ARMOR_STACKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                .add(player.level().getGameTime() + ARMOR_STACK_TICKS);
    }

    private static void addAttackStack(ServerPlayer player) {
        ATTACK_STACKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                .add(player.level().getGameTime() + ATTACK_STACK_TICKS);
    }

    private static void refreshStackModifiers(ServerPlayer player) {
        long now = player.level().getGameTime();
        List<Long> armor = prune(ARMOR_STACKS, player.getUUID(), now);
        List<Long> attack = prune(ATTACK_STACKS, player.getUUID(), now);
        // e5 自闭：诅咒态 ×0.75/层（乘算）→ 反转态「护甲不再衰减，改为每层 +5%」（加算，§2）
        if (CurseManager.rev(player, DEF, E5_AUTISM)) {
            applyAddStack(player, Attributes.ARMOR, ARMOR_MODIFIER, ARMOR_STACK_REVERSED_BONUS, armor.size());
        } else {
            applyMultStack(player, Attributes.ARMOR, ARMOR_MODIFIER, 0.75, armor.size());
        }
        // e8 挫败：诅咒态 ×0.94/层 → 反转态 ×1.06/层（文案即「每层 ×」，同样按乘算）
        double attackBase = CurseManager.rev(player, DEF, E8_FRUSTRATION)
                ? ATTACK_STACK_REVERSED : ATTACK_STACK_CURSE;
        applyMultStack(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER, attackBase, attack.size());
    }

    /** 当前（未过期的）挫败攻击层数 —— e8 反转条件「挫败层数 ≥3」用 */
    private static int attackStackCount(ServerPlayer player) {
        return prune(ATTACK_STACKS, player.getUUID(), player.level().getGameTime()).size();
    }

    private static List<Long> prune(Map<UUID, List<Long>> map, UUID uuid, long now) {
        List<Long> list = map.get(uuid);
        if (list != null) {
            list.removeIf(t -> t <= now);
            if (list.isEmpty()) map.remove(uuid);
        }
        return list == null ? List.of() : list;
    }

    private static void applyMultStack(ServerPlayer player, Holder<Attribute> attr,
                                       ResourceLocation id, double base, int count) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        if (count <= 0) {
            inst.removeModifier(id);
            return;
        }
        double value = Math.pow(base, count) - 1.0;
        AttributeModifier mod = inst.getModifier(id);
        if (mod == null || Math.abs(mod.amount() - value) > 0.0001) {
            inst.removeModifier(id);
            inst.addTransientModifier(
                    new AttributeModifier(id, value, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** **加算**分层（§2 反转态口径）：总数 = perLayer × 层数，写成一个 ADD_MULTIPLIED_TOTAL */
    private static void applyAddStack(ServerPlayer player, Holder<Attribute> attr,
                                      ResourceLocation id, double perLayer, int count) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        if (count <= 0) {
            inst.removeModifier(id);
            return;
        }
        double value = perLayer * count;
        AttributeModifier mod = inst.getModifier(id);
        if (mod == null || Math.abs(mod.amount() - value) > 0.0001) {
            inst.removeModifier(id);
            inst.addTransientModifier(
                    new AttributeModifier(id, value, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeStackModifiers(ServerPlayer player) {
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.removeModifier(ARMOR_MODIFIER);
        AttributeInstance atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.removeModifier(ATTACK_MODIFIER);
        clearStackState(player.getUUID());
    }

    private static void clearStackState(UUID uuid) {
        ARMOR_STACKS.remove(uuid);
        ATTACK_STACKS.remove(uuid);
    }

    // ========== 伤害事件：神秘雪球 / 安魂曲 / 疯狂 / 挫败 ==========

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        try {
            damageTaken(event);
            damageDealt(event);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ 伤害结算异常", t);
        }
    }

    /** 玩家受击：e3 安魂曲 / e2 神秘雪球 / e8 挫败 / e11 疯狂（扭曲值） */
    private static void damageTaken(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !hasAbyss(player)) return;

        // ── 受伤侧一律乘算（§2），逐条先按诅咒态 / 反转态取符号，最后合成一个倍率 ──
        float factor = 1.0f;
        // e3 安魂曲：受到的伤害 +13%/天（诅咒态）/ −13%/天（反转态）
        int saveDays = getSaveDays(player);
        if (CurseManager.on(player, DEF, E3_REQUIEM)) {
            factor *= 1.0f + REQUIEM_TAKEN_PER_DAY * saveDays;
        } else if (CurseManager.rev(player, DEF, E3_REQUIEM)) {
            factor *= Math.max(0.0f, 1.0f - REQUIEM_TAKEN_PER_DAY * saveDays);
        }
        // e11 疯狂：每点扭曲值受到伤害 +0.2%（诅咒态）/ −0.2%（反转态）
        int twist = getTwist(player);
        if (twist > 0) {
            if (CurseManager.on(player, DEF, E11_MADNESS)) {
                factor *= 1.0f + twist * TWIST_TAKEN_PER_POINT;
            } else if (CurseManager.rev(player, DEF, E11_MADNESS)) {
                factor *= Math.max(0.0f, 1.0f - twist * TWIST_TAKEN_PER_POINT);
            }
        }
        if (factor != 1.0f) {
            event.setNewDamage(event.getNewDamage() * factor);
        }
        // e12 需要在 LOWEST 阶段把这个倍率还原掉，才能反推出「元素附加伤害」本身
        TAKEN_FACTOR.put(player.getId(), factor);

        // e2 神秘雪球：以攻击佩戴者的怪物为中心，环形发射雪球（诅咒态 8 个 / 反转态 3 个）
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity livingAttacker && !(attacker instanceof Player)) {
            if (CurseManager.on(player, DEF, E2_SNOWBALL)) {
                spawnSnowballRing(livingAttacker, false);
            } else if (CurseManager.rev(player, DEF, E2_SNOWBALL)) {
                spawnSnowballRing(livingAttacker, true);
            }
        }
        // e8 挫败：受击加攻击层（诅咒态与反转态都叠层，只是倍率不同）
        if (CurseManager.on(player, DEF, E8_FRUSTRATION) || CurseManager.rev(player, DEF, E8_FRUSTRATION)) {
            addAttackStack(player);
        }
    }

    /** 玩家造成伤害：e11 疯狂（每点扭曲值 +1%，两态相同）+ e11 条件「一击必杀」预判 */
    private static void damageDealt(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || !hasAbyss(attacker)) return;
        int twist = getTwist(attacker);
        if (twist > 0) {
            event.setNewDamage(event.getNewDamage() * (1.0f + twist * TWIST_DEALT_PER_POINT));
        }
        checkOneShotKill(attacker, event);
    }

    /**
     * e11 条件预判：移速 &gt; 4 时对最大生命 ≥6 万的生物打出**致死一击**即记候选，
     * 真正的反转在 {@link #onMobKilled}（必须确实死亡）里落地。
     */
    private static void checkOneShotKill(ServerPlayer player, LivingDamageEvent.Pre event) {
        if (!CurseManager.on(player, DEF, E11_MADNESS)) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;
        if (target.getMaxHealth() < E11_TARGET_MAX_HEALTH) return;
        if (!movedFastRecently(player)) return;
        if (event.getNewDamage() < target.getHealth()) return;   // 必须是一击致死
        ONESHOT_KILL.put(target.getId(), player.level().getGameTime());
    }

    /**
     * e12 恶意（LOWEST）：诅咒态「受到的元素附加伤害 +12%」由 {@code DamageHandler}
     * （{@code totalBonusMultiplier *= 1.12f}，本任务范围外）直接加进 {@code newDamage}，
     * 反转态要变成 −12%，只能在元素附加已经计入之后把多出来的那部分扣回去。
     * <p>
     * 还原口径：{@code DamageHandler} 是 HIGHEST，它的加伤基数是**当时的 newDamage**
     * （= 护甲/魔咒/抗性结算完、ABSORPTION 之前）。1.21.1 里
     * {@code LivingDamageEvent.Pre} 的 {@code getOriginalDamage()} 是**护甲前**的原值，
     * 所以这里用「原值 − 已记录的各项 reduction」把它还原出来：
     * <pre>
     * base  = originalDamage − INVULNERABILITY − ARMOR − ENCHANTMENTS − MOB_EFFECTS − INNATE_RESISTANCE
     * bonus = newDamage / factor − base        （本次元素附加伤害，已含 ×1.12）
     * 反转后只把元素附加那部分换成 ×0.88：newDamage −= bonus × factor × (1 − 0.88/1.12)
     * </pre>
     * 只对差值做减法（不重写 newDamage），其它处理器的加减仍然保留。
     * <p>
     * 条件（诅咒态）：「承受一次 ≥50 点的元素附加伤害并存活」——存活交给
     * {@link #markPendingReverse} 在 2 tick 后与死亡清除一并判定。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagedLast(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            Float recorded = TAKEN_FACTOR.remove(player.getId());
            float factor = recorded == null ? 1.0f : recorded;
            boolean curse = CurseManager.on(player, DEF, E12_MALICE);
            boolean reversed = CurseManager.rev(player, DEF, E12_MALICE);
            if (!curse && !reversed) return;
            if (factor <= 0.0f) return;
            float current = event.getNewDamage();
            if (current <= 0.0f) return;

            DamageContainer container = event.getContainer();
            float base = container.getOriginalDamage()
                    - container.getReduction(DamageContainer.Reduction.INVULNERABILITY)
                    - container.getReduction(DamageContainer.Reduction.ARMOR)
                    - container.getReduction(DamageContainer.Reduction.ENCHANTMENTS)
                    - container.getReduction(DamageContainer.Reduction.MOB_EFFECTS)
                    - container.getReduction(DamageContainer.Reduction.INNATE_RESISTANCE);
            if (base <= 0.0f) return;

            float bonus = current / factor - base;      // 本次元素附加伤害（含 +12%）
            if (bonus <= 0.0f) return;

            if (curse) {
                if (bonus >= E12_ELEMENT_BONUS_GOAL) {
                    markPendingReverse(player, E12_MALICE);
                }
                return;
            }
            // 反转：+12% → −12%（只对元素附加那部分做差减）
            float delta = bonus * factor * (1.0f - ELEMENT_TAKEN_REVERSED / ELEMENT_TAKEN_CURSE);
            event.setNewDamage(Math.max(0.0f, current - delta));
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ e12 元素附加结算异常", t);
        }
    }

    /** 以攻击者（怪物）为中心发射一圈敌对雪球（标记 + owner），命中伤害由 onProjectileImpact 结算 */
    private static void spawnSnowballRing(LivingEntity center, boolean reversed) {
        Level level = center.level();
        int count = reversed ? SNOWBALL_COUNT_REVERSED : SNOWBALL_COUNT_CURSE;
        for (int i = 0; i < count; i++) {
            Snowball snowball = new Snowball(level, center);
            snowball.setPos(center.getX(), center.getEyeY() - 0.2, center.getZ());
            double angle = (i / (double) count) * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            snowball.shoot(dir.x, dir.y + 0.1, dir.z, 0.6f, 1.0f);
            snowball.getPersistentData().putBoolean(SNOWBALL_TAG, true);
            if (reversed) {
                snowball.getPersistentData().putBoolean(KEY_SNOWBALL_WEAK, true);
            }
            level.addFreshEntity(snowball);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        try {
            Projectile projectile = event.getProjectile();
            if (projectile.level().isClientSide) return;
            if (!projectile.getPersistentData().getBoolean(SNOWBALL_TAG)) return;
            HitResult hit = event.getRayTraceResult();
            if (!(hit instanceof EntityHitResult entityHit)) return;
            Entity target = entityHit.getEntity();
            if (!(target instanceof LivingEntity) || target == projectile.getOwner()) return;

            // e2 反转：雪球伤害 3 点 → 1 点（发射时按佩戴者当时的状态写标记）
            float damage = projectile.getPersistentData().getBoolean(KEY_SNOWBALL_WEAK)
                    ? SNOWBALL_DAMAGE_REVERSED : SNOWBALL_DAMAGE_CURSE;
            // 清除无敌帧（命中玩家时同样生效，保证伤害必中）
            ((EntityInvulnerableTimeAccessor) (Object) target).lensouls$setInvulnerableTime(0);
            target.hurt(projectile.level().damageSources().magic(), damage);

            // e2 条件：累计受到 100 次该种敌对雪球的伤害
            if (target instanceof ServerPlayer player && hasAbyss(player)
                    && CurseManager.on(player, DEF, E2_SNOWBALL)) {
                CurseManager.tick(player, DEF, E2_SNOWBALL, 1L, E2_HIT_GOAL);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ e2 雪球结算异常", t);
        }
    }

    // ========== 愚钝：经验减半 / 反转 ×2 ==========

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            if (!hasAbyss(player)) return;
            int amount = event.getAmount();
            if (amount <= 0) return;
            // e4 条件：一次性获取 2000 点经验（按**来源给的原值**判定，不受本条减半影响）
            if (CurseManager.on(player, DEF, E4_DULLNESS) && amount >= E4_XP_GOAL) {
                CurseManager.reach(player, DEF, E4_DULLNESS);
            }
            if (CurseManager.on(player, DEF, E4_DULLNESS)) {
                // 诅咒态：经验减半但至少保留 1（避免小数值直接归零完全阻止获取）
                event.setAmount(Math.max(1, (int) Math.ceil(amount / 2.0)));
            } else if (CurseManager.rev(player, DEF, E4_DULLNESS)) {
                // 反转态：经验获取 ×2
                event.setAmount(amount * 2);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ e4 经验结算异常", t);
        }
    }

    // ========== 食之无味：禁食回血 / 反转进食回血 ×2 ==========

    /**
     * e9 食之无味。诅咒态的「无法通过食物回血」由 {@code FoodDataMixin}（按 {@link #hasAbyss}
     * 拦截 {@code FoodData.tick} 里的 {@code Player#heal}）实现，本任务范围外、保持原样；
     * 这里补的是**反转态**：进食（含食物组件的物品用完）时按营养值回血 ×2。
     * <p>
     * 同一次进食也用来累计 e9 的条件「累计食用 50 种不同食物」（物品 id 去重，持久化）。
     */
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            if (!hasAbyss(player)) return;
            ItemStack stack = event.getItem();
            if (stack.isEmpty()) return;
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) return;

            // e9 条件：累计食用 50 种不同食物
            if (CurseManager.on(player, DEF, E9_TASTELESS)) {
                recordFoodKind(player, stack);
            }
            // e9 反转：进食回血 ×2
            if (CurseManager.rev(player, DEF, E9_TASTELESS) && food.nutrition() > 0) {
                player.heal(food.nutrition() * 2.0f);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ e9 进食结算异常", t);
        }
    }

    /** e9 条件：把食物种类记进持久化列表（同种只记一次），并上报进度 / 达标反转 */
    private static void recordFoodKind(ServerPlayer player, ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return;
        String key = id.toString();
        CompoundTag tag = persisted(player);
        ListTag list = tag.getList(KEY_FOOD_KINDS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (key.equals(list.getString(i))) return;   // 同种食物只计一次
        }
        list.add(StringTag.valueOf(key));
        tag.put(KEY_FOOD_KINDS, list);
        writeBack(player, tag);

        int n = list.size();
        CurseManager.setProgress(player, DEF, E9_TASTELESS, n);
        if (n >= E9_FOOD_KIND_GOAL) {
            CurseManager.reach(player, DEF, E9_TASTELESS);
        }
    }

    // ========== 自闭 / 失忆：生物加入世界 / 复制之魂解禁 ==========

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity) || entity instanceof Player) return;
        try {
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                    entity.getBoundingBox().inflate(16))) {
                if (!hasAbyss(player)) continue;
                boolean curse = CurseManager.on(player, DEF, E5_AUTISM);
                boolean reversed = CurseManager.rev(player, DEF, E5_AUTISM);
                if (!curse && !reversed) continue;
                // 每次自闭魔法伤害都清除玩家无敌帧，保证 0.5 点必中（不被上次受击的无敌帧吞掉）
                ((EntityInvulnerableTimeAccessor) (Object) player).lensouls$setInvulnerableTime(0);
                player.hurt(player.level().damageSources().magic(), 0.5f);
                addArmorStack(player);
                // e5 条件：16 格内一次性（同一 tick）加入 50 只生物，且非旁观者、存活
                if (curse && !player.isSpectator()) {
                    countJoinBatch(player);
                }
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ e5 自闭结算异常", t);
        }
    }

    /**
     * e5 条件计数：`一次性加入` 按**同一 gameTime** 归为一批；达到 50 只即登记待结算
     * （「且存活」由 {@link #resolvePending} 在 2 tick 后判定，`死亡即失败`）。
     */
    private static void countJoinBatch(ServerPlayer player) {
        long now = player.level().getGameTime();
        long[] state = JOIN_BATCH.get(player.getUUID());
        long count = (state != null && state[0] == now) ? state[1] + 1L : 1L;
        JOIN_BATCH.put(player.getUUID(), new long[]{now, count});
        if (count >= E5_BATCH_GOAL) {
            markPendingReverse(player, E5_AUTISM);
        }
    }

    // ========== 厄运：挖掘 + 每分钟 debuff ==========

    /**
     * e6 挖掘计数 + 蠹虫。
     * <p>
     * <b>连锁采集（FTB Ultimine）口径</b>：Ultimine 的批量破坏走
     * {@code ServerPlayerGameMode#destroyBlock(pos)}（1.21.1 源码 `tryBreakBlock` 的默认分支，
     * 见 FTB-Ultimine `1.21.1/main` 的 `FTBUltimine.tryBreakBlock`），
     * 而 NeoForge 的 `ServerPlayerGameMode.destroyBlock` 会逐块 `CommonHooks.fireBlockBreak` →
     * **每个被连锁破坏的方块都会派发一次 {@code BlockEvent.BreakEvent}**，所以这里无需特殊处理，
     * 一次连锁 N 块就计 N 次。
     * <p>
     * 唯一例外是 Ultimine 的「加速腐烂树叶」分支（装 Accelerated Decay 时的树叶衰变特判，
     * 走 {@code Level#destroyBlock}）—— 那属于树叶自然衰变而非玩家挖掘，不计入是正确行为。
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        try {
            if (!hasAbyss(player)) return;
            // e6 条件：累计挖掘 200 个方块
            if (CurseManager.on(player, DEF, E6_DOOM)) {
                CurseManager.tick(player, DEF, E6_DOOM, 1L, E6_BLOCK_GOAL);
            }
            // e6 诅咒态：0.8% 概率生成 15 只蠹虫（反转态「挖方块不再生怪」⇒ 只在诅咒态生成）
            if (!CurseManager.on(player, DEF, E6_DOOM)) return;
            if (player.getRandom().nextFloat() >= DOOM_CHANCE) return;
            BlockPos pos = event.getPos();
            for (int i = 0; i < 15; i++) {
                Entity silverfish = EntityType.SILVERFISH.create(level);
                if (silverfish == null) continue;
                silverfish.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                level.addFreshEntity(silverfish);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ e6 挖掘结算异常", t);
        }
    }

    private static void applyDoomDebuffs(ServerPlayer player) {
        int i1 = player.getRandom().nextInt(DOOM_DEBUFFS.length);
        int i2 = (i1 + 1 + player.getRandom().nextInt(DOOM_DEBUFFS.length - 1)) % DOOM_DEBUFFS.length;
        // 等级 3~20 随机 → amplifier 2..19
        int amp1 = 2 + player.getRandom().nextInt(18);
        int amp2 = 2 + player.getRandom().nextInt(18);
        player.addEffect(new MobEffectInstance(DOOM_DEBUFFS[i1], DOOM_DURATION, amp1));
        player.addEffect(new MobEffectInstance(DOOM_DEBUFFS[i2], DOOM_DURATION, amp2));
    }

    /** e6 反转：每分钟改为获得 2 个正面效果（20 秒；文案未写等级 ⇒ 取 I 级） */
    private static void applyDoomBoons(ServerPlayer player) {
        int i1 = player.getRandom().nextInt(DOOM_BOONS.length);
        int i2 = (i1 + 1 + player.getRandom().nextInt(DOOM_BOONS.length - 1)) % DOOM_BOONS.length;
        for (int i = 0; i < DOOM_BOON_COUNT; i++) {
            int index = i == 0 ? i1 : i2;
            player.addEffect(new MobEffectInstance(DOOM_BOONS[index], DOOM_DURATION, 0));
        }
    }

    // ========== 祸之可能性：充能 → 触发 → 召唤 ==========

    /** 单次召唤计划：粒子已显，延迟后于落点生成怪物 */
    private static final class SummonPlan {
        final long time;
        final ServerLevel level;
        final List<Vec3> positions;
        final EntityType<?> type;
        /** 召唤者（用于 e7 条件「击杀由祸之可能性召唤的怪物」的归属判定） */
        final UUID owner;

        SummonPlan(long time, ServerLevel level, List<Vec3> positions, EntityType<?> type, UUID owner) {
            this.time = time;
            this.level = level;
            this.positions = positions;
            this.type = type;
            this.owner = owner;
        }
    }

    /** 测试指令用：延迟触发一次完整召唤（不污染充能计时器） */
    private static final class DelayedSummon {
        final long time;
        final ServerPlayer player;

        DelayedSummon(long time, ServerPlayer player) {
            this.time = time;
            this.player = player;
        }
    }

    private static final List<SummonPlan> PENDING_SUMMONS = new ArrayList<>();
    private static final List<DelayedSummon> DELAYED_SUMMONS = new ArrayList<>();

    /** 充能间隔（tick）：扭曲值越高越短，下限 2min */
    private static int chargeInterval(int twist) {
        float t = Math.max(0, Math.min(MAX_TWIST, twist)) / (float) MAX_TWIST;
        int interval = (int) (CHARGE_INTERVAL_BASE - t * (CHARGE_INTERVAL_BASE - CHARGE_INTERVAL_MIN));
        return Math.max(CHARGE_INTERVAL_MIN, interval);
    }

    /** 身边 radius 格内是否存在不少于 min 只怪物 */
    private static boolean hasEnoughNearbyMobs(ServerPlayer player, double radius, int min) {
        AABB box = new AABB(player.blockPosition()).inflate(radius);
        return player.level().getEntitiesOfClass(Monster.class, box).size() >= min;
    }

    /**
     * 祸之可能性收束：<b>诅咒态</b>召唤 5 只怪物；<b>反转态</b>改为范围伤害落雷
     * （见 {@link #strikeCalamityLightning}）。
     */
    private static void performCalamity(ServerPlayer player) {
        if (CurseManager.rev(player, DEF, E7_CALAMITY)) {
            strikeCalamityLightning(player);
        } else {
            performSummon(player);
        }
    }

    /**
     * e7 反转：不再召唤怪物，改为范围伤害落雷 —— 对 7 格内怪物造成 15 点魔法伤害。
     * <p>视觉落雷用 {@code setVisualOnly(true)}（只放闪电与音效，不引燃、不另算伤害）；
     * 实际伤害由本方法逐只结算（魔法伤害类型，记在玩家名下）。
     */
    private static void strikeCalamityLightning(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(player.getX(), player.getY(), player.getZ());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        AABB box = new AABB(player.blockPosition()).inflate(CALAMITY_LIGHTNING_RADIUS);
        int struck = 0;
        for (Monster mob : level.getEntitiesOfClass(Monster.class, box)) {
            // 清无敌帧，保证每只落雷都吃到 15 点
            ((EntityInvulnerableTimeAccessor) (Object) mob).lensouls$setInvulnerableTime(0);
            if (mob.hurt(player.damageSources().indirectMagic(player, player), CALAMITY_LIGHTNING_DAMAGE)) {
                struck++;
            }
        }
        LOGGER.info("[AbyssCalamity] 落雷（反转）at {} 命中 {} 只", player.blockPosition(), struck);
    }

    /** 执行一次召唤：生成 5 个精灵粒子落点，1s 后于落点生成 5 只怪物（其一类型，带召唤者标记） */
    private static void performSummon(ServerPlayer player) {
        EntityType<?> type = DISASTER_TYPES[player.getRandom().nextInt(DISASTER_TYPES.length)];
        ServerLevel level = player.serverLevel();
        double py = player.getY() + 2.0;
        List<Vec3> positions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            double x = player.getX() + (player.getRandom().nextDouble() * 2 - 1) * SUMMON_HALF;
            double z = player.getZ() + (player.getRandom().nextDouble() * 2 - 1) * SUMMON_HALF;
            Vec3 p = new Vec3(x, py, z);
            positions.add(p);
            level.sendParticles(ModParticleTypes.ABYSS_SUMMON.get(), x, py, z, 1, 0, 0, 0, 0);
        }
        PENDING_SUMMONS.add(new SummonPlan(level.getGameTime() + SUMMON_DELAY_TICKS, level, positions, type,
                player.getUUID()));
        LOGGER.info("[AbyssSummon] queued {}x {} at gameTime={}", positions.size(), type, level.getGameTime() + SUMMON_DELAY_TICKS);
    }

    private static void spawnAtPositions(SummonPlan plan) {
        int spawned = 0;
        for (Vec3 p : plan.positions) {
            Entity e = plan.type.create(plan.level);
            if (e == null) continue;
            e.setPos(p.x, p.y, p.z);
            // e7 条件用标记：这只怪物是「祸之可能性召唤物」，归属召唤者
            e.getPersistentData().putBoolean(KEY_SUMMON_TAG, true);
            if (plan.owner != null) {
                e.getPersistentData().putUUID(KEY_SUMMON_OWNER, plan.owner);
            }
            plan.level.addFreshEntity(e);
            spawned++;
        }
        LOGGER.info("[AbyssSummon] spawned {}x {}", spawned, plan.type);
    }

    /**
     * 测试指令：直接从「触发倒计时」阶段开始（龙吼 + 3s 倒计时 + 收束），不污染充能计时器。
     * 收束按条目状态走 {@link #performCalamity}（诅咒态召唤 / 反转态落雷）。
     */
    public static void triggerTestCalamity(ServerPlayer player) {
        if (!hasAbyss(player)) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.0f);
        AbyssCountdownPacket.send(player);
        DELAYED_SUMMONS.add(new DelayedSummon(player.level().getGameTime() + COUNTDOWN_TICKS, player));
        LOGGER.info("[AbyssSummon] triggerTestCalamity by {}, countdown until gameTime={}", player.getName().getString(), player.level().getGameTime() + COUNTDOWN_TICKS);
    }

    /** 每服务器刻推进延迟召唤与测试召唤队列（DelayedSummon = 测试指令的倒计时收束） */
    private static void processSummonScheduler() {
        if (!PENDING_SUMMONS.isEmpty()) {
            Iterator<SummonPlan> it = PENDING_SUMMONS.iterator();
            while (it.hasNext()) {
                SummonPlan plan = it.next();
                if (plan.level.getGameTime() >= plan.time) {
                    spawnAtPositions(plan);
                    it.remove();
                }
            }
        }
        if (!DELAYED_SUMMONS.isEmpty()) {
            Iterator<DelayedSummon> it = DELAYED_SUMMONS.iterator();
            while (it.hasNext()) {
                DelayedSummon d = it.next();
                ServerPlayer p = d.player;
                if (p == null || !p.isAlive() || !hasAbyss(p)) {
                    LOGGER.info("[AbyssSummon] delayed summon dropped (player={}, alive={})", p == null ? "null" : p.getName().getString(), p != null && p.isAlive());
                    it.remove();
                    continue;
                }
                if (p.level().getGameTime() >= d.time) {
                    LOGGER.info("[AbyssSummon] delayed summon fired at gameTime={}", p.level().getGameTime());
                    performCalamity(p);
                    it.remove();
                }
            }
        }
    }

    // ========== 计时器（持久化，掉线不丢） ==========

    /** [AbyssCharge] 日志降频：interval 变化 >10% 或距上次 ≥200 tick 才打 */
    private static int lastLoggedChargeInterval = 0;
    private static long lastChargeLogTick = 0;

    /**
     * 祸之可能性充能推进（state==0）。
     * <p>
     * 充能间隔随扭曲值动态变化：扭曲值越高间隔越短。为保证「已充能进度」不浪费，
     * 间隔变化时按进度比例迁移剩余时间——新的完成时间 = now + (1 - 进度) × 新间隔。
     * 扭曲值升高 → 间隔缩短 → 剩余时间压缩（充能加速）；扭曲值降低 → 相应拉长。
     */
    private static boolean advanceCharge(ServerPlayer player, CompoundTag tag) {
        long now = player.level().getGameTime();
        long chargeNext = tag.getLong(KEY_CHARGE_NEXT);
        if (chargeNext <= 0L) {
            int interval = chargeInterval(getTwist(player));
            tag.putLong(KEY_CHARGE_NEXT, now + interval);
            tag.putInt(KEY_CHARGE_INTERVAL, interval);
            LOGGER.info("[AbyssCharge] init twist={} interval={} next={}", getTwist(player), interval, now + interval);
            return true;
        }
        if (now >= chargeNext) {
            tag.putInt(KEY_CHARGE_STATE, 1);
            return true;
        }
        int oldInterval = tag.getInt(KEY_CHARGE_INTERVAL);
        int newInterval = chargeInterval(getTwist(player));
        if (oldInterval <= 0) {
            // 旧存档无 interval 记录：无进度可迁移，直接按当前扭曲值重设充能
            tag.putLong(KEY_CHARGE_NEXT, now + newInterval);
            tag.putInt(KEY_CHARGE_INTERVAL, newInterval);
            LOGGER.info("[AbyssCharge] legacy reset twist={} interval={} next={}", getTwist(player), newInterval, now + newInterval);
            return true;
        }
        if (newInterval != oldInterval) {
            long chargeStart = chargeNext - oldInterval;
            double progress = Math.max(0.0, Math.min(1.0, (double) (now - chargeStart) / oldInterval));
            long newNext = now + Math.round((1.0 - progress) * newInterval);
            tag.putLong(KEY_CHARGE_NEXT, newNext);
            tag.putInt(KEY_CHARGE_INTERVAL, newInterval);
            if (Math.abs(newInterval - lastLoggedChargeInterval) * 10 > oldInterval || now - lastChargeLogTick >= 200) {
                LOGGER.info("[AbyssCharge] twist={} interval {}->{} progress={} next {}->{}",
                        getTwist(player), oldInterval, newInterval, progress, chargeNext, newNext);
                lastLoggedChargeInterval = newInterval;
                lastChargeLogTick = now;
            }
            return true;
        }
        return false;
    }

    private static void advanceTimers(ServerPlayer player) {
        long now = player.level().getGameTime();
        CompoundTag tag = persisted(player);
        boolean dirty = false;
        long doomNext = tag.getLong(KEY_DOOM_TIMER);
        if (doomNext <= 0L) {
            tag.putLong(KEY_DOOM_TIMER, now + DOOM_INTERVAL);
            dirty = true;
        } else if (now >= doomNext) {
            // e6 厄运：诅咒态抽 2 个不重复 debuff；反转态改为 2 个正面效果
            if (CurseManager.rev(player, DEF, E6_DOOM)) {
                applyDoomBoons(player);
            } else {
                applyDoomDebuffs(player);
            }
            tag.putLong(KEY_DOOM_TIMER, now + DOOM_INTERVAL);
            dirty = true;
        }

        // 祸之可能性 状态机：0 充能中 → 1 已充能待触发 → 2 倒计时中
        int state = tag.getInt(KEY_CHARGE_STATE);
        if (state == 0) {
            dirty |= advanceCharge(player, tag);
        } else if (state == 1) {
            if (hasEnoughNearbyMobs(player, 7.0, 5)) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.0f);
                AbyssCountdownPacket.send(player);
                tag.putInt(KEY_CHARGE_STATE, 2);
                tag.putLong(KEY_COUNTDOWN_NEXT, now + COUNTDOWN_TICKS);
                dirty = true;
            }
        } else if (state == 2) {
            long cdNext = tag.getLong(KEY_COUNTDOWN_NEXT);
            if (now >= cdNext) {
                // e7 收束：诅咒态召唤 5 只 / 反转态 7 格范围落雷
                performCalamity(player);
                int interval = chargeInterval(getTwist(player));
                tag.putInt(KEY_CHARGE_STATE, 0);
                tag.putLong(KEY_CHARGE_NEXT, now + interval);
                tag.putInt(KEY_CHARGE_INTERVAL, interval);
                dirty = true;
            }
        }
        if (dirty) writeBack(player, tag);
    }

    /** 摘下羽毛：重置计时器（重新戴上从新周期开始） */
    private static void resetTimers(ServerPlayer player) {
        CompoundTag tag = persisted(player);
        boolean dirty = false;
        if (tag.contains(KEY_DOOM_TIMER)) { tag.remove(KEY_DOOM_TIMER); dirty = true; }
        if (tag.contains(KEY_CHARGE_STATE)) { tag.remove(KEY_CHARGE_STATE); dirty = true; }
        if (tag.contains(KEY_CHARGE_NEXT)) { tag.remove(KEY_CHARGE_NEXT); dirty = true; }
        if (tag.contains(KEY_CHARGE_INTERVAL)) { tag.remove(KEY_CHARGE_INTERVAL); dirty = true; }
        if (tag.contains(KEY_COUNTDOWN_NEXT)) { tag.remove(KEY_COUNTDOWN_NEXT); dirty = true; }
        if (dirty) writeBack(player, tag);
    }

    // ========== 疯狂：合成 +1 ==========

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer player && hasAbyss(player)) {
                addTwist(player, 1);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ 合成结算异常", t);
        }
    }

    // ========== 生命周期防护 ==========

    /** 死亡：清内存层 + 扭曲归零（PlayerPersisted 写入，复活复制后生效）+ 清「且存活」类待结算 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        clearStackState(player.getUUID());
        // e5 / e12 的条件都写了「并存活」⇒ 死亡即判失败
        clearTransient(player.getUUID());
        if (hasAbyss(player)) {
            setTwist(player, 0);
        }
    }

    /**
     * 击杀结算：
     * <ul>
     *   <li>疯狂：击杀怪物 3% 概率降 1 点扭曲值（诅咒态与反转态都保留）；</li>
     *   <li>e7 条件：累计击杀**由祸之可能性召唤**的怪物 100 只；</li>
     *   <li>e8 条件：挫败层数 ≥3 时击杀 1 只 BOSS；</li>
     *   <li>e11 条件：移速 &gt; 4 时一击杀死最大生命 ≥6 万的生物。</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onMobKilled(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player) return;
        try {
            ServerPlayer player = killerOf(event);
            if (player == null || !hasAbyss(player)) {
                ONESHOT_KILL.remove(dead.getId());
                return;
            }
            // 疯狂：击杀怪物 3% 概率 −1 扭曲值
            if (dead instanceof Monster && player.getRandom().nextFloat() < 0.03f) {
                addTwist(player, -1);
            }
            // e7 条件：累计击杀由祸之可能性召唤的怪物 100 只（归属校验）
            if (dead.getPersistentData().getBoolean(KEY_SUMMON_TAG)
                    && dead.getPersistentData().hasUUID(KEY_SUMMON_OWNER)
                    && player.getUUID().equals(dead.getPersistentData().getUUID(KEY_SUMMON_OWNER))
                    && CurseManager.on(player, DEF, E7_CALAMITY)) {
                CurseManager.tick(player, DEF, E7_CALAMITY, 1L, E7_SUMMON_KILL_GOAL);
            }
            // e8 条件：在挫败层数 ≥3 时击杀 1 只 BOSS
            if (CurseManager.on(player, DEF, E8_FRUSTRATION)
                    && attackStackCount(player) >= E8_STACK_GOAL
                    && com.plumejade.lensouls.boss.ToughnessDamageHandler.isBoss(dead)) {
                CurseManager.reach(player, DEF, E8_FRUSTRATION);
            }
            // e10 条件：空手击杀湮灭构造体（主副手均为空即可——不必最终击杀的那一击来自空手）
            if (CurseManager.on(player, DEF, E10_AMNESIA)
                    && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
                ResourceLocation deadId = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
                if (deadId != null && ANNIHILATION_PURSUER.equals(deadId.toString())) {
                    CurseManager.reach(player, DEF, E10_AMNESIA);
                }
            }
            // e11 条件：移速 > 4 时一击杀死最大生命 ≥6 万的生物（候选由 checkOneShotKill 登记）
            Long oneShot = ONESHOT_KILL.remove(dead.getId());
            if (oneShot != null && CurseManager.on(player, DEF, E11_MADNESS)
                    && player.level().getGameTime() - oneShot <= 5L) {
                CurseManager.reach(player, DEF, E11_MADNESS);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ 击杀结算异常", t);
        }
    }

    /** 击杀者解析：直接击杀 ‖ 弹射物 owner */
    private static ServerPlayer killerOf(LivingDeathEvent event) {
        Entity killer = event.getSource().getEntity();
        if (killer instanceof ServerPlayer sp) return sp;
        if (killer instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer sp) return sp;
        return null;
    }

    /**
     * 死亡重生：安魂曲无需缓存恢复——羽毛 ALWAYS_KEEP 死亡不掉落，
     * 重生后 {@link #onPlayerTick} 检测佩戴即按实时天数重建扣血。
     * 此处仅在重生瞬间立即补挂，消除 modifier 重建前的短暂满上限间隙。
     * 取下羽毛（has=false）由 onPlayerTick 走 removeRequiem，恢复正常最大生命。
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        if (!event.isWasDeath()) return;
        applyRequiem(newPlayer, CurseManager.rev(newPlayer, DEF, E3_REQUIEM));
    }

    /** 登录：疯狂值同步 + 清残留内存层（安魂曲由 onPlayerTick 按佩戴状态维护） */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        clearStackState(player.getUUID());
        clearTransient(player.getUUID());
        if (hasAbyss(player)) {
            TwistSyncPacket.send(player, Math.round(getTwist(player) / 2.0f));
        }
        // 卡创造兜底：下一 tick 的 updateAutoCreative 会按当前手持状态自愈
    }

    /** 登出：恢复被强制切换的创造模式 + 清内存层 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        clearStackState(player.getUUID());
        clearTransient(player.getUUID());
        CompoundTag tag = persisted(player);
        if (tag.contains(KEY_GM_BEFORE)) {
            int prev = tag.getInt(KEY_GM_BEFORE);
            GameType prevType = GameType.byId(prev);
            tag.remove(KEY_GM_BEFORE);
            writeBack(player, tag);
            if (prevType != null && player.gameMode.getGameModeForPlayer() != prevType) {
                player.setGameMode(prevType);
            }
        }
    }

    // ========== 反转条件 / 反转态的公共支撑 ==========

    // ---- e11 条件：移速 > 4 的时间窗 ----

    /** 水平速度（格/秒 = 格/tick × 20）超过 4 即记下 1 秒窗口 */
    private static void trackFastMovement(ServerPlayer player) {
        double blocksPerSecond = player.getDeltaMovement().horizontalDistance() * 20.0;
        if (blocksPerSecond > E11_SPEED_THRESHOLD) {
            FAST_MOVE_UNTIL.put(player.getUUID(),
                    player.level().getGameTime() + E11_SPEED_WINDOW);
        }
    }

    /** 最近 1 秒内是否有过「移速 > 4」 */
    private static boolean movedFastRecently(ServerPlayer player) {
        Long until = FAST_MOVE_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() <= until;
    }

    /**
     * e11：清掉超过 60 tick 仍未死亡的一击必杀候选。
     * <p>候选按**目标实体 id** 存（{@link #checkOneShotKill}），而实体 id 会随新实体递增，
     * 长时间不下线时会缓慢累积，所以这里定期回收（正常路径由击杀时的 remove 清掉）。
     */
    private static void pruneOneShotCandidates(long now) {
        if (ONESHOT_KILL.isEmpty()) return;
        ONESHOT_KILL.values().removeIf(t -> now - t > 60L);
    }

    // ---- e5 / e12「且存活」待结算 ----

    /**
     * 登记一条「达成条件但要求存活」的反转（e5 一次性 50 只生物 / e12 ≥50 点元素附加伤害）。
     * 延后 2 tick 结算：期间若玩家死亡（{@link #onDeath} 会清表）则判失败。
     */
    private static void markPendingReverse(ServerPlayer player, int entry) {
        List<Integer> pending = PENDING_REVERSE.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        if (!pending.contains(entry)) pending.add(entry);
        PENDING_AT.put(player.getUUID(), player.level().getGameTime() + 2L);
    }

    /** 结算「且存活」类条件：到点且仍存活才反转 */
    private static void resolvePending(ServerPlayer player) {
        Long at = PENDING_AT.get(player.getUUID());
        if (at == null || player.level().getGameTime() < at) return;
        List<Integer> pending = PENDING_REVERSE.remove(player.getUUID());
        PENDING_AT.remove(player.getUUID());
        if (pending == null || !player.isAlive()) return;
        for (int entry : pending) {
            CurseManager.reach(player, DEF, entry);
        }
    }

    // ---- e10 失忆 反转：复制之魂解禁 ----

    /**
     * e10「复制之魂解禁」。封印由 {@code CopySoulItem#inventoryTick} 与
     * {@code CopySoulSealHandler}（本任务范围外）按 {@link #hasAbyss} 反复打上，
     * 所以只能在**每 tick 的 {@code PlayerTickEvent.Post} 最末**（LOWEST，晚于该处理器
     * 自己的 Post 扫描）把封印摘掉。
     * <p>
     * 时机依据（1.21.1 源码）：`MinecraftServer.tickChildren` 先跑完所有 level tick
     * （玩家的物品栏 tick 与 PlayerTickEvent.Post 都在这里），**之后**才 `connection.tick()`
     * 处理数据包 —— 也就是说玩家点击合成格时，物品栏里的复制之魂一定是「未封印」状态；
     * 而同 tick 的 inventoryTick 即便重新打标，也会被这里的清理覆盖。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTickLast(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            if (!CurseManager.rev(player, DEF, E10_AMNESIA)) return;
            stripCopySoulSeals(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ④ e10 解封异常", t);
        }
    }

    /** 摘掉随身复制之魂的封印组件（就地改 ItemStack，组件变化由框架同步） */
    private static void stripCopySoulSeals(ServerPlayer player) {
        int stripped = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof CopySoulItem)) continue;
            if (stack.has(ModDataComponents.COPY_SOUL_SEALED.get())) {
                stack.remove(ModDataComponents.COPY_SOUL_SEALED.get());
                stripped++;
            }
        }
        if (stripped > 0) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    // ---- 生命周期：清瞬时状态 ----

    /**
     * 清该玩家的瞬时计时/待结算表。
     * <p>真正的进度与反转位在 {@code CurseStateData}（玩家附件，{@code copyOnDeath}），
     * 不在这里的清理范围内。静态 Map 按 UUID 存 → 换存档 UUID 不变会串档，故登出/死亡必清。
     */
    private static void clearTransient(UUID id) {
        if (id == null) return;
        PENDING_REVERSE.remove(id);
        PENDING_AT.remove(id);
        FAST_MOVE_UNTIL.remove(id);
        JOIN_BATCH.remove(id);
    }
}
