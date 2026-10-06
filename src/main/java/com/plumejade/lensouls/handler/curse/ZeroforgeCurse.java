package com.plumejade.lensouls.handler.curse;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.component.SoulCooldownData;
import com.plumejade.lensouls.config.DamageTypeElementLoader;
import com.plumejade.lensouls.config.ItemElementActivityLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.effect.ElementInfusionEffect;
import com.plumejade.lensouls.effect.SoulDotEffect;
import com.plumejade.lensouls.entity.GunBulletEntity;
import com.plumejade.lensouls.feather.CurseDef;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import com.plumejade.lensouls.item.ModItems;
import com.plumejade.lensouls.reinforce.ReinforceInventoryScanner;
import com.plumejade.lensouls.timer.TimerService;
import com.plumejade.lensouls.util.CameraVisibility;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ⑦ 羽·零号誓炉（机誓 · 失控之律）—— <b>7 条诅咒 + 7 条祝福</b>共 14 条效果。
 * <p>
 * <b>统一写法</b>：每条都先过 {@link CurseManager#on} / {@link CurseManager#rev}
 * （= 该款被选进槽位 ∧ 该条未反转 / 已反转），条件进度用 {@link CurseManager#tick} 累加，达标即反转。
 * <p>
 * ⚠ <b>e1..e7 是诅咒</b>（达成条件即「反转」）；<b>e8..e14 是祝福</b>（达成条件即「解锁」，不是反转）。
 * 两边的实现写法<b>完全相同</b>：{@code on} = 未解锁、{@code rev} = 已解锁；
 * 界面文案才区分「已反转 / 已解锁」（见 {@code CurseDefs.ZEROFORGE} 的 {@code sectionAt = 7}）。
 * <p>
 * <b>叠加口径</b>（设计文档 §2「叠加口径（定稿）」）：诅咒态**乘算**、反转 / 祝福态**加算**；
 * 点数与资源类（栏位 ±N、冷却 ±N%、掉落 ±N）直接加算。
 * <p>
 * 条目序号（0 基）与 lang 的 {@code eN} 一致：
 * e1 燃魂=0 e2 反噬=1 e3 锈契=2 e4 断供=3 e5 封库=4 e6 叛主=5 e7 易手=6
 * e8 神愤=7 e9 阅历=8 e10 灵通=9 e11 博识=10 e12 天卷=11 e13 坚毅=12 e14 银环=13。
 */
public final class ZeroforgeCurse {

    private static final CurseDef D = CurseDefs.ZEROFORGE;

    // ==================== 通用常量 ====================

    /** 慢速维护间隔（tick）：1 秒一次 */
    private static final int MAINTAIN_INTERVAL = 20;

    /** e1 燃魂：吞噬间隔 30 秒 */
    private static final int DEVOUR_INTERVAL_TICKS = 600;
    /** e1 燃魂：无魂状态需存活 30 分钟 */
    private static final long NO_SOUL_GOAL_TICKS = 36_000L;
    /** e1：「当前有无复制之魂」缓存刷新间隔（tick）——三容器扫描不便宜，别每 tick 查 */
    private static final int SOUL_CACHE_INTERVAL = 40;

    /** e2 反噬：自伤冷却 5 秒 */
    private static final long SELF_HURT_COOLDOWN_TICKS = 100L;
    /** e2 反噬：低血量判定线（20%） */
    private static final float LOW_HEALTH_RATIO = 0.20f;
    /** e2 反噬：低血量击杀目标数 */
    private static final long LOW_KILL_GOAL = 200L;

    /** e3 锈契：攻击丢失概率 15% */
    private static final float MISS_CHANCE = 0.15f;
    /** e3 锈契：累计丢失次数目标 */
    private static final long MISS_GOAL = 100L;

    /** e4 断供：饱食度未满时伤害 -70%（乘算） */
    private static final float STARVED_DAMAGE_MULTIPLIER = 0.30f;
    /** e4 断供 反转：饱食度满时伤害 +10%（加算） */
    private static final float FED_BONUS = 0.10f;
    /** 原版饱食度满值 */
    private static final int MAX_FOOD = 20;

    /** e6 叛主：敌对雪球概率 2% */
    private static final float DRONE_SNOWBALL_CHANCE = 0.02f;
    /** e6 叛主：雪球伤害 10 点魔法 */
    private static final float DRONE_SNOWBALL_DAMAGE = 10.0f;
    /** e6 叛主：雪球标记（命中判定用） */
    private static final String SNOWBALL_TAG = "lensouls:zeroforge_drone_snowball";
    /** e6 叛主：累计承受 50 次 */
    private static final long SNOWBALL_HIT_GOAL = 50L;
    /** e6 反转：无人机伤害 +100%（乘算） */
    private static final float DRONE_DAMAGE_MULTIPLIER = 2.0f;

    /** e7 易手：常规换手间隔 8 秒 */
    private static final long SWAP_INTERVAL_TICKS = 160L;
    /** e7 易手：Y < 20 时换手间隔 4 秒 */
    private static final long SWAP_INTERVAL_LOW_Y_TICKS = 80L;
    /** e7 易手：Y < 20 判定线 */
    private static final double LOW_Y_THRESHOLD = 20.0D;
    /** e7 易手：每次交换丢出其中一件的概率 3% */
    private static final float HAND_DROP_CHANCE = 0.03f;
    /** e7 条件：在 Y < 0 处击杀 200 只 */
    private static final long LOW_Y_KILL_GOAL = 200L;
    /** e7 反转：每次换手 5% 概率获得力量 X */
    private static final float STRENGTH_CHANCE = 0.05f;
    /** e7 反转：力量 X 持续 10 秒 */
    private static final int STRENGTH_DURATION_TICKS = 200;
    /** e7 反转：力量 X 触发后冷却 7 秒 */
    private static final long STRENGTH_COOLDOWN_TICKS = 140L;

    /** e8 神愤：四种活性药水需达到的等级（amplifier 9 = 10 级） */
    private static final int INFUSION_TARGET_AMPLIFIER = 9;
    /** e8 神愤：基础元素附加伤害加成 +17%（乘算，加在元素追伤上） */
    private static final float ELEMENT_BONUS_BASE = 0.17f;
    /** e8 神愤 解锁：+25% */
    private static final float ELEMENT_BONUS_UNLOCKED = 0.25f;

    /** e9 阅历：经验 ×3 */
    private static final float XP_MULTIPLIER_BASE = 3.0f;
    /** e9 阅历 解锁：经验 ×5 */
    private static final float XP_MULTIPLIER_UNLOCKED = 5.0f;
    /** e9 条件：经验等级达到 30 级 */
    private static final int XP_LEVEL_GOAL = 30;

    /** e10 灵通：发光目标受到伤害 +7%（乘算） */
    private static final float GLOW_DAMAGE_BONUS = 0.07f;
    /** e10 条件：用「见微知著」解锁 10 种生物图鉴 */
    private static final long GLIMPSE_GOAL = 10L;
    /** e10：累积记录到的生物种类持久化上限 */
    private static final int GLIMPSE_CAP = 512;
    /** e10：视野内发光扫描 —— 半径（格）与视锥半角（度） */
    private static final double GLOW_RANGE = 48.0D;
    private static final double GLOW_HALF_ANGLE = 60.0D;

    /** e11 博识：对击杀过的生物 +15% */
    private static final float KNOWN_KILL_BONUS = 0.15f;
    /** e11 博识 解锁：+30% */
    private static final float KNOWN_KILL_BONUS_UNLOCKED = 0.30f;
    /** e11 条件：累计击杀 50 种不同生物 */
    private static final int SPECIES_GOAL = 50;
    /** e11：击杀过的生物种类持久化上限（防无界增长） */
    private static final int SPECIES_CAP = 1024;

    /** e12 天卷：首饰（head）栏位 +2 */
    private static final double HEAD_SLOTS_BASE = 2.0D;
    /** e12 天卷 解锁：+3 */
    private static final double HEAD_SLOTS_UNLOCKED = 3.0D;
    /** e12 条件：累计装备过 12 件不同饰品 */
    private static final int ACCESSORY_GOAL = 12;
    /** e12：饰品记录持久化上限 */
    private static final int ACCESSORY_CAP = 512;

    /** e13 坚毅：获得负面效果时 20% 概率回复 10 点生命 */
    private static final float DEBUFF_HEAL_CHANCE_BASE = 0.20f;
    /** e13 坚毅 解锁：70% */
    private static final float DEBUFF_HEAL_CHANCE_UNLOCKED = 0.70f;
    /** e13：回复量 10 点 */
    private static final float DEBUFF_HEAL_AMOUNT = 10.0f;
    /** e13 条件：累计获得 500 次负面效果 */
    private static final long DEBUFF_GOAL = 500L;

    /** e14 银环：镜魂冷却 -30% */
    private static final float SOUL_COOLDOWN_REDUCE_BASE = 0.30f;
    /** e14 银环 解锁：-50% */
    private static final float SOUL_COOLDOWN_REDUCE_UNLOCKED = 0.50f;
    /** e14 条件：累计用过 6 种不同镜魂 */
    private static final int SOUL_KIND_GOAL = 6;
    /** e14：镜魂种类记录持久化上限 */
    private static final int SOUL_KIND_CAP = 128;

    // ==================== 持久化键（PlayerPersisted 子键：跨死亡 / 掉线保留） ====================

    /** e11：击杀过的生物种类（字符串列表） */
    private static final String KEY_SPECIES = "lensouls:zeroforge_species";
    /** e10：见微知著照片里累积记录到的生物种类（字符串列表） */
    private static final String KEY_GLIMPSE = "lensouls:zeroforge_glimpsed";
    /** e12：曾装备过的饰品（字符串列表） */
    private static final String KEY_ACCESSORIES = "lensouls:zeroforge_accessories";
    /** e14：用过 / 见过的镜魂种类（字符串列表） */
    private static final String KEY_SOUL_KINDS = "lensouls:zeroforge_soul_kinds";
    /** e14：已减过冷却的标记（compound：键 → 过期 gameTime） */
    private static final String KEY_CD_REDUCED = "lensouls:zeroforge_cd_reduced";
    /** 缓存键：当前三容器里是否有复制之魂 */
    private static final String KEY_HAS_SOUL = "lensouls:zeroforge_has_soul";

    // ==================== 运行期缓存 ====================

    /** e1 / e14：不需要相位缓存，扫描已按秒节流 */
    /** e1：无复制之魂累计 tick */
    private static final Map<UUID, Long> NO_SOUL_TICKS = new ConcurrentHashMap<>();
    /** e2：自伤冷却到期 tick */
    private static final Map<UUID, Long> SELF_HURT_UNTIL = new ConcurrentHashMap<>();
    /** e7：下次强制换手 tick */
    private static final Map<UUID, Long> NEXT_SWAP = new ConcurrentHashMap<>();
    /** e7 反转：力量 X 冷却到期 tick */
    private static final Map<UUID, Long> STRENGTH_UNTIL = new ConcurrentHashMap<>();
    /** e7：上一 tick 所在维度（切维度不触发换手） */
    private static final Map<UUID, ResourceLocation> LAST_DIMENSION = new ConcurrentHashMap<>();
    /** e10：本模组点亮的发光实体（逐玩家） */
    private static final Map<UUID, Set<UUID>> GLOWED = new ConcurrentHashMap<>();

    /** e5 反转的攻速修饰符 ID */
    private static final ResourceLocation ATTACK_SPEED_MOD =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "zeroforge_attack_speed");
    /** e12 天卷的首饰栏位修饰符 ID */
    private static final ResourceLocation HEAD_SLOT_MOD =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "zeroforge_head_slots");

    /** 四元素活性效果（e8 神愤条件用） */
    private static final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>[] INFUSIONS =
            new net.minecraft.core.Holder[]{
                    com.plumejade.lensouls.effect.ModEffects.FIRE_INFUSION,
                    com.plumejade.lensouls.effect.ModEffects.WATER_INFUSION,
                    com.plumejade.lensouls.effect.ModEffects.EARTH_INFUSION,
                    com.plumejade.lensouls.effect.ModEffects.ENDER_INFUSION
            };

    private ZeroforgeCurse() {
    }

    // ==================== 小工具 ====================

    /** 该款是否被选中（未选中 → 不生效、不统计） */
    private static boolean active(Player player) {
        return CurseManager.isActive(player, D);
    }

    /** 跨死亡 / 掉线持久化子键（NeoForge 复活只复制 {@code PlayerPersisted} 子键） */
    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void writeBack(Player player, CompoundTag tag) {
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    /** 实体类型 ID → 字符串（未注册返回 null） */
    private static String typeId(Entity entity) {
        if (entity == null) return null;
        EntityType<?> type = entity.getType();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id == null ? null : id.toString();
    }

    /** 是否为「无人机」（gytrinket）造成的伤害 —— 口径与 {@code DamageHandler.isGytrinketDamage} 一致 */
    private static boolean isDroneDamage(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) return false;
        var typeKey = source.typeHolder().unwrapKey();
        if (typeKey.isPresent() && "gytrinket".equals(typeKey.get().location().getNamespace())) return true;
        for (Entity e : new Entity[]{source.getDirectEntity(), source.getEntity()}) {
            if (e == null) continue;
            if (e.getClass().getName().startsWith("com.gytrinket.")) return true;
            String id = typeId(e);
            if (id != null && id.startsWith("gytrinket:")) return true;
        }
        return false;
    }

    /**
     * 该物品是否「精妙背包 / 维度网络」这类**容器类**物品（封印 e5 的拦截目标）。
     * <p>
     * 判定靠 namespace + {@code Capabilities.ItemHandler.ITEM} 内容物能力：
     * 「拿着就能打开的那个容器」正是注册了 ItemHandler 能力的物品，
     * 与 {@code CopySoulSealHandler} 枚举内容物的口径同源。
     */
    private static boolean isContainerItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        String ns = id.getNamespace();
        if ("sophisticatedbackpacks".equals(ns) || "beyonddimensions".equals(ns)) return true;
        // 兜底：其它实现了内容物能力的「随身容器」也一并拦下（本模组的物品除外）
        return !LenSouls.MODID.equals(ns)
                && stack.getCapability(Capabilities.ItemHandler.ITEM) != null;
    }

    /** 列表写入（带去重与上限；一次写回） */
    private static void addList(Player player, String key, List<String> values, int cap) {
        if (values == null || values.isEmpty()) return;
        CompoundTag tag = persisted(player);
        ListTag current = tag.getList(key, Tag.TAG_STRING);
        Set<String> seen = new HashSet<>(current.size() + values.size());
        for (int i = 0; i < current.size(); i++) {
            seen.add(current.getString(i));
        }
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            if (seen.size() >= cap) break;
            seen.add(value);
        }
        if (seen.size() == current.size()) return;      // 没有新增，不写回（写回有同步成本）

        ListTag next = new ListTag();
        for (String value : seen) {
            next.add(net.minecraft.nbt.StringTag.valueOf(value));
        }
        tag.put(key, next);
        writeBack(player, tag);
    }

    /** 单个值写入（带去重与上限；一次写回） */
    private static void addToList(Player player, String key, String value, int cap) {
        if (value == null || value.isEmpty()) return;
        CompoundTag tag = persisted(player);
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (value.equals(list.getString(i))) return;
        }
        if (list.size() >= cap) return;
        list.add(net.minecraft.nbt.StringTag.valueOf(value));
        tag.put(key, list);
        writeBack(player, tag);
    }

    // ==================== e1 誓约·燃魂 ====================

    /**
     * e1 燃魂：每 30 秒吞噬 1 个复制之魂（物品栏 / 精妙背包 / 维度网络）；无魂时持续饥饿 VI + 虚弱 IV。
     * <p>
     * 条件：在「无复制之魂」状态下存活 30 分钟（36000 tick）→ 反转后不再吞噬、无魂时也不再有 debuff。
     */
    private static void tickDevour(ServerPlayer player) {
        boolean hasSoul = hasCopySoul(player);
        if (hasSoul) {
            // 有魂 ⇒ 吞噬计时与「无魂」计时都从头开始
            NO_SOUL_TICKS.remove(player.getUUID());
            if (CurseManager.on(player, D, 0)
                    && player.level().getGameTime() % DEVOUR_INTERVAL_TICKS == 0L) {
                consumeCopySoul(player);
            }
        } else {
            if (CurseManager.on(player, D, 0)) {
                long n = NO_SOUL_TICKS.merge(player.getUUID(), 1L, Long::sum);
                CurseManager.tick(player, D, 0, 1L, NO_SOUL_GOAL_TICKS);
                if (n >= NO_SOUL_GOAL_TICKS) NO_SOUL_TICKS.remove(player.getUUID());
            }
        }

        // 无魂时的持续性 debuff：诅咒态才给；反转后（rev）完全摘掉
        if (CurseManager.on(player, D, 0) && !hasSoul) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 5, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 3, true, false, false));
        }
    }

    /** 「当前是否有复制之魂」——按 40 tick 缓存的**只读**三容器统计（不消耗） */
    private static boolean hasCopySoul(ServerPlayer player) {
        CompoundTag tag = persisted(player);
        long now = player.level().getGameTime();
        long until = tag.getLong(KEY_HAS_SOUL);
        // 高 32 位是「是否持有」，低 32 位是缓存到期时刻（相对值，够用且不用两个键）
        if (until > 0L && now < (until & 0xFFFFFFFFL)) {
            return (until >>> 32) != 0L;
        }
        boolean found = countCopySoul(player) > 0L;
        long stamp = ((found ? 1L : 0L) << 32) | ((now + SOUL_CACHE_INTERVAL) & 0xFFFFFFFFL);
        tag.putLong(KEY_HAS_SOUL, stamp);
        writeBack(player, tag);
        return found;
    }

    /** 三容器里复制之魂的总数（物品栏 → 容器类物品内容物 → 超越维度） */
    private static long countCopySoul(ServerPlayer player) {
        return ReinforceInventoryScanner.tally(player, Set.of(ModItems.COPY_SOUL.get()))
                .getOrDefault(ModItems.COPY_SOUL.get(), 0L);
    }

    /** 吞噬 1 个复制之魂（三容器顺序取用；没得吃返回 false） */
    private static boolean consumeCopySoul(ServerPlayer player) {
        return ReinforceInventoryScanner.consume(player, ModItems.COPY_SOUL.get());
    }

    // ==================== e2 誓约·反噬 ====================

    /** e2 反噬：击杀生物时自身受 1 点绝对真伤（冷却 5 秒）/ 反转后击杀回复 2 点生命 */
    private static void onKillSelfHurt(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (CurseManager.on(player, D, 1)) {
            Long until = SELF_HURT_UNTIL.get(player.getUUID());
            if (until != null && now < until) return;
            SELF_HURT_UNTIL.put(player.getUUID(), now + SELF_HURT_COOLDOWN_TICKS);
            hurtAbsorbing(player, 1.0f);
        } else if (CurseManager.rev(player, D, 1)) {
            player.heal(2.0f);
        }
    }

    /**
     * 绝对真伤：不吃减伤 / 护盾 / 无敌帧。
     * <p>
     * 先写无敌帧 0（否则 {@code hurt} 会被受击无敌挡掉或减半），再走
     * {@code DamageSources.generic()}（无来源、无护甲归属的通用伤害类型）；
     * 目标自己的减伤逻辑仍可能出手，所以这里对结果做一次「至少补到目标值」的兜底。
     */
    private static void hurtAbsorbing(LivingEntity target, float amount) {
        if (target == null || target.level().isClientSide) return;
        try {
            ((com.plumejade.lensouls.mixin.EntityInvulnerableTimeAccessor) (Object) target)
                    .lensouls$setInvulnerableTime(0);
        } catch (Throwable ignored) {
            // 非 LivingEntity 子类 / 环境异常：继续尝试 hurt
        }
        float before = target.getHealth();
        target.hurt(target.level().damageSources().generic(), amount);
        float dealt = before - target.getHealth();
        if (dealt < amount && target.isAlive()) {
            // 兜底：把被目标自己的减伤吞掉的部分补齐；至少留 1 点生命，绝不直接把人打死
            float target2 = Math.max(1.0f, before - amount);
            if (target2 < target.getHealth()) target.setHealth(target2);
        }
    }

    // ==================== e3 誓约·锈契 ====================

    /**
     * e3 锈契：攻击 15% 概率丢失 + 掉落物数量 -1。
     * <p>
     * 丢失与计数器都放在 {@code LOWEST} 优先级：{@link com.plumejade.lensouls.damage.HitChanceHandler}
     * 是 {@code LOWEST}，同优先级下「后注册者后跑」，这里必然在它之后，
     * 因此「已被命中率取消」的那一刀也会走到这里（才能计入丢失次数）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMissRoll(LivingIncomingDamageEvent event) {
        try {
            if (event.getEntity().level().isClientSide) return;
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
            if (event.getEntity() == player) return;
            if (!active(player)) return;

            if (CurseManager.on(player, D, 2)) {
                if (event.isCanceled() || player.getRandom().nextFloat() < MISS_CHANCE) {
                    if (!event.isCanceled()) event.setCanceled(true);
                    CurseManager.tick(player, D, 2, 1L, MISS_GOAL);
                }
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ e3 锈契掷骰异常", t);
        }
    }

    /** e3 锈契：掉落物数量 -1（生物掉落与方块掉落都减；反转后不再减） */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        try {
            if (event.getEntity().level().isClientSide) return;
            LivingEntity killer = event.getEntity().getKillCredit();
            if (!(killer instanceof ServerPlayer player)) return;
            reduceDrops(event.getDrops(), player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ e3 锈契掉落结算异常", t);
        }
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        try {
            if (!(event.getBreaker() instanceof ServerPlayer player)) return;
            reduceDrops(event.getDrops(), player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ e3 锈契方块掉落结算异常", t);
        }
    }

    private static void reduceDrops(java.util.Collection<ItemEntity> drops, ServerPlayer player) {
        if (drops == null || drops.isEmpty()) return;
        if (!CurseManager.on(player, D, 2)) return;
        List<ItemEntity> snapshot = new ArrayList<>(drops);
        for (ItemEntity entity : snapshot) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;
            if (stack.getCount() > 1) {
                stack.shrink(1);
                entity.setItem(stack);
            } else {
                drops.remove(entity);
            }
            return; // 每次事件只减 1 个
        }
    }

    // ==================== e4 / e6 / e8 / e10 / e11 造成伤害 ====================

    /** 造成伤害侧的全部条目（LOW：在 DamageHandler / 各羽毛处理器之后收口） */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        try {
            if (event.getEntity().level().isClientSide) return;
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
            LivingEntity target = event.getEntity();
            if (target == player) return;
            float d = event.getNewDamage();
            if (d <= 0.0f) return;
            if (!active(player)) return;

            // ── e4 誓约·断供：饱食度未满时伤害 -70%（乘算）；反转后不再罚、饱食度满时 +10% ──
            boolean full = player.getFoodData().getFoodLevel() >= MAX_FOOD;
            if (!full) {
                if (CurseManager.on(player, D, 3)) d *= STARVED_DAMAGE_MULTIPLIER;
            } else if (CurseManager.rev(player, D, 3)) {
                d *= (1.0f + FED_BONUS);
            }

            // ── e6 誓约·叛主 反转：无人机伤害 +100%（乘算） ──
            if (CurseManager.rev(player, D, 5) && isDroneDamage(event.getSource())) {
                d *= DRONE_DAMAGE_MULTIPLIER;
            }

            // ── e8 零号·神愤：元素附加伤害 +17%（未解锁）/ +25%（已解锁） ──
            // 只放大于「本次攻击确实有元素活性」时——这正是「元素附加伤害」的成立条件。
            if (elementalActivitySum(player, event.getSource()) > 0f) {
                float bonus = CurseManager.rev(player, D, 7) ? ELEMENT_BONUS_UNLOCKED : ELEMENT_BONUS_BASE;
                d *= (1.0f + bonus);
            }

            // ── e10 零号·灵通 解锁：发光目标受到伤害 +7%（乘算） ──
            if (CurseManager.rev(player, D, 9) && target.isCurrentlyGlowing()) {
                d *= (1.0f + GLOW_DAMAGE_BONUS);
            }

            // ── e11 零号·博识：对击杀过的生物 +15%（未解锁）/ +30%（已解锁）（加算） ──
            String tid = typeId(target);
            if (tid != null && hasKilled(player, tid)) {
                float bonus = CurseManager.rev(player, D, 10)
                        ? KNOWN_KILL_BONUS_UNLOCKED : KNOWN_KILL_BONUS;
                d *= (1.0f + bonus);
            }

            event.setNewDamage(d);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ 造成伤害结算异常", t);
        }
    }

    /**
     * 复刻 {@code DamageHandler} 的「元素活性合计」计算（只读、不改任何状态）。
     * <p>
     * 为什么复刻而不是共享字段：{@code DamageHandler} 是 {@code EventPriority.NORMAL}，
     * 本处理器是 {@code LOW}，两者都挂在同一个 {@code LivingDamageEvent.Pre} 上，
     * 这里读到的所有来源（武器 / 药水 / 伤害类型 / 子弹）在两次事件之间不会变，
     * 复刻即可得到与「元素附加伤害」同口径的开关条件，不必改其它文件。
     */
    private static float elementalActivitySum(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
        ResourceLocation weaponId = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
        ResourceLocation dtId = player.level().registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getKey(source.type());
        ElementDamage bulletElement = null;
        if (source.getDirectEntity() instanceof GunBulletEntity gunBullet) {
            bulletElement = GunBulletEntity.getBulletElement(gunBullet.getBulletType());
        }

        float sum = 0f;
        for (ElementDamage element : ElementDamage.values()) {
            if (element == ElementDamage.PROJECTILE) continue;
            int level = ItemElementActivityLoader.getLevel(weaponId, element);
            if (level > 0) sum += ElementDamage.getActivityByLevel(level);
            if (bulletElement == element) sum += 2.0f;
            if (dtId != null) sum += DamageTypeElementLoader.getActivity(dtId, element);
            for (MobEffectInstance inst : player.getActiveEffects()) {
                if (inst.getEffect().value() instanceof ElementInfusionEffect effect
                        && effect.getElement() == element) {
                    sum += ElementDamage.getActivityByAmplifier(inst.getAmplifier());
                    break;
                }
            }
            for (MobEffectInstance inst : player.getActiveEffects()) {
                if (inst.getEffect().value() instanceof SoulDotEffect effect
                        && effect.getElement() == element) {
                    sum += ElementDamage.getActivityByAmplifier(inst.getAmplifier());
                    break;
                }
            }
        }
        return sum;
    }

    // ==================== e5 誓约·封库 ====================

    /** e5 封库：无法打开精妙背包与维度网络（客户端/服务端双双拦下右键） */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        try {
            if (event.getLevel() == null || event.getLevel().isClientSide) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!CurseManager.on(player, D, 4)) return;
            if (!isContainerItem(event.getItemStack())) return;
            event.setCanceled(true);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.lensouls.zeroforge.library_sealed"), true);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ e5 封库拦截异常", t);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (event.getLevel().isClientSide) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!CurseManager.on(player, D, 4)) return;
            ItemStack stack = player.getItemInHand(event.getHand());
            if (!isContainerItem(stack)) return;
            event.setCanceled(true);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ e5 封库（方块）拦截异常", t);
        }
    }

    /** e5 封库 反转：攻击速度 +15%（点数类，直接加算在属性上；未反转时摘掉） */
    private static void maintainAttackSpeed(ServerPlayer player) {
        var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        if (attr == null) return;
        boolean want = CurseManager.rev(player, D, 4);
        var existing = attr.getModifier(ATTACK_SPEED_MOD);
        if (want) {
            if (existing == null) {
                attr.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        ATTACK_SPEED_MOD, 0.15D,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else if (existing != null) {
            attr.removeModifier(ATTACK_SPEED_MOD);
        }
    }

    // ==================== e6 誓约·叛主（雪球侧） ====================

    /**
     * e6 叛主：无人机造成伤害后，2% 概率朝佩戴者发射一发 10 点魔法伤害的敌对雪球。
     * <p>
     * 触发面：<b>佩戴者受到</b>伤害，且该伤害的攻击方式是无人机 / 构造体
     * （{@link #isDroneDamage}，与 {@code DamageHandler} 的 gytrinket 判定同口径——
     * 这类伤害的 causing entity 常常就是玩家本人，构造体归玩家所有）。
     * 雪球从无人机位置朝佩戴者发射、以佩戴者为 owner，命中时清无敌帧后结算 10 点魔法伤害。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDroneDamage(LivingDamageEvent.Pre event) {
        try {
            if (event.getEntity().level().isClientSide) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!active(player)) return;
            if (!CurseManager.on(player, D, 5)) return;
            if (!isDroneDamage(event.getSource())) return;
            if (player.getRandom().nextFloat() >= DRONE_SNOWBALL_CHANCE) return;

            Entity drone = event.getSource().getDirectEntity();
            if (drone == null) drone = event.getSource().getEntity();
            double x = drone != null ? drone.getX() : player.getX();
            double y = drone != null ? drone.getEyeY() : player.getEyeY();
            double z = drone != null ? drone.getZ() : player.getZ();

            Level level = player.level();
            Snowball snowball = new Snowball(level, player);
            snowball.setPos(x, y - 0.1D, z);
            Vec3 aim = new Vec3(player.getX() - x, player.getEyeY() - y, player.getZ() - z).normalize();
            snowball.shoot(aim.x, aim.y, aim.z, 1.2f, 1.0f);
            snowball.getPersistentData().putBoolean(SNOWBALL_TAG, true);
            level.addFreshEntity(snowball);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ e6 叛主雪球异常", t);
        }
    }

    /** e6 叛主：敌对雪球命中佩戴者 → 10 点魔法伤害 + 计入「累计承受 50 次」 */
    @SubscribeEvent
    public static void onProjectileImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        try {
            Projectile projectile = event.getProjectile();
            if (projectile.level().isClientSide) return;
            if (!projectile.getPersistentData().getBoolean(SNOWBALL_TAG)) return;
            if (!(event.getRayTraceResult()
                    instanceof net.minecraft.world.phys.EntityHitResult entityHit)) return;
            Entity target = entityHit.getEntity();
            if (!(target instanceof ServerPlayer player)) return;
            if (!active(player) || !CurseManager.on(player, D, 5)) return;

            ((com.plumejade.lensouls.mixin.EntityInvulnerableTimeAccessor) (Object) player)
                    .lensouls$setInvulnerableTime(0);
            player.hurt(player.level().damageSources().magic(), DRONE_SNOWBALL_DAMAGE);
            CurseManager.tick(player, D, 5, 1L, SNOWBALL_HIT_GOAL);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ e6 叛主雪球命中异常", t);
        }
    }

    // ==================== e7 誓约·易手 ====================

    /**
     * e7 易手：非坐骑状态每 8 秒强制交换主副手（Y &lt; 20 时 4 秒）；每次交换 3% 概率把其中一件丢到脚下。
     * <p>
     * 守卫（见设计文档 §7「誓约·易手 实现守卫」，不做会出物品复制 / 丢失事故）：
     * <ul>
     *   <li>只在「没开别的容器」且「光标为空」时交换 —— 否则物品停在 {@code containerMenu} 光标或容器槽里会被吞 / 复制；</li>
     *   <li>旁观者、创造模式、死亡瞬间、切维度不触发 —— 这几种状态下切换手持会丢物品；</li>
     *   <li>丢出用 {@code player.drop(stack, false)} 丢在<b>脚下</b>，不往外抛（3% 丢出若抛远，在岩浆/虚空边就是直接销毁）；</li>
     *   <li>交换后 {@code broadcastChanges()} + 音效 + 动作栏提示 —— 不给反馈会被当成客户端显示错乱上报；</li>
     *   <li>只换 {@code Inventory} 槽位（主手 0 / 副手 40），不碰渲染层。</li>
     * </ul>
     * 反转后解除诅咒；且每次主副手交换时 5% 概率获得力量 X（10 秒），触发后冷却 7 秒。
     */
    private static void tickHandSwap(ServerPlayer player) {
        long now = player.level().getGameTime();
        ResourceLocation dim = player.level().dimension().location();
        ResourceLocation lastDim = LAST_DIMENSION.put(player.getUUID(), dim);

        // 切维度：本次不换手，计时重新起算（跨维度切手持会丢物品）
        if (lastDim != null && !lastDim.equals(dim)) {
            NEXT_SWAP.remove(player.getUUID());
            return;
        }
        if (player.isSpectator() || player.isCreative() || !player.isAlive() || player.isDeadOrDying()) {
            return;
        }
        if (player.isPassenger()) return;   // 非坐骑状态才触发

        boolean cursed = CurseManager.on(player, D, 6);
        // 反转态：不强制换手，但保留「每次主副手交换 5% 力量 X」的奖励
        long interval = cursed
                ? (player.getY() < LOW_Y_THRESHOLD ? SWAP_INTERVAL_LOW_Y_TICKS : SWAP_INTERVAL_TICKS)
                : Long.MAX_VALUE;

        // 反转态的力量 X 由「玩家自己换手」触发，强制换手到期也视为一次换手
        Long next = NEXT_SWAP.get(player.getUUID());
        if (cursed) {
            if (next == null) {
                NEXT_SWAP.put(player.getUUID(), now + interval);
                return;
            }
            if (now >= next) {
                NEXT_SWAP.put(player.getUUID(), now + interval);
                performSwap(player, true);
            }
        } else {
            NEXT_SWAP.remove(player.getUUID());
        }

        // 反转态：玩家自己交换主副手时给力量 X（每 7 秒最多一次）
        if (CurseManager.rev(player, D, 6)) {
            Integer prevMain = LAST_MAIN_HASH.put(player.getUUID(), player.getMainHandItem().hashCode());
            if (prevMain != null && prevMain != player.getMainHandItem().hashCode()) {
                rollStrength(player, now);
            }
        } else {
            LAST_MAIN_HASH.remove(player.getUUID());
        }
    }

    /** e7：上一 tick 主手物品指纹（用来发现「玩家自己换手了」） */
    private static final Map<UUID, Integer> LAST_MAIN_HASH = new ConcurrentHashMap<>();

    /** e7 反转：5% 概率获得力量 X（10 秒），触发后冷却 7 秒 */
    private static void rollStrength(ServerPlayer player, long now) {
        Long until = STRENGTH_UNTIL.get(player.getUUID());
        if (until != null && now < until) return;
        if (player.getRandom().nextFloat() >= STRENGTH_CHANCE) return;
        STRENGTH_UNTIL.put(player.getUUID(), now + STRENGTH_COOLDOWN_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, STRENGTH_DURATION_TICKS, 9,
                true, false, false));
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                        "message.lensouls.zeroforge.strength_x", 10), true);
    }

    /** 真正执行主副手交换（含 3% 丢出与反馈） */
    private static void performSwap(ServerPlayer player, boolean mayDrop) {
        // 守卫一：开着别的容器 / 光标有物品 —— 交换会让物品停在光标或容器槽里（可能被吞 / 复制）
        if (!(player.containerMenu instanceof InventoryMenu)) return;
        if (!player.containerMenu.getCarried().isEmpty()) return;

        Inventory inv = player.getInventory();
        int main = inv.selected;
        int off = Inventory.SLOT_OFFHAND;
        ItemStack mainStack = inv.getItem(main);
        ItemStack offStack = inv.getItem(off);
        if (mainStack.isEmpty() && offStack.isEmpty()) return;

        if (mayDrop && player.getRandom().nextFloat() < HAND_DROP_CHANCE) {
            // 丢在脚下（false = 不往外抛），保证岩浆 / 虚空边不会直接销毁
            if (!mainStack.isEmpty()) {
                inv.setItem(main, ItemStack.EMPTY);
                player.drop(mainStack, false);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.lensouls.zeroforge.hand_drop"), true);
            } else {
                inv.setItem(off, ItemStack.EMPTY);
                player.drop(offStack, false);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.lensouls.zeroforge.hand_drop"), true);
            }
        } else {
            inv.setItem(main, offStack);
            inv.setItem(off, mainStack);
        }
        inv.setChanged();
        player.containerMenu.broadcastChanges();
        // 记下换手后的主手指纹：否则下一 tick 的「自己换手」检测会把这次强制换手再算一次
        LAST_MAIN_HASH.put(player.getUUID(), player.getMainHandItem().hashCode());

        // 反馈：声音 + 动作栏提示（不给反馈会被当成客户端显示错乱上报）
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.7f, 1.4f);
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                        "message.lensouls.zeroforge.hand_swap"), true);

        // 反转态：强制换手同样算一次换手
        if (CurseManager.rev(player, D, 6)) {
            rollStrength(player, player.level().getGameTime());
        }
    }

    // ==================== e8 零号·神愤（条件） ====================

    /** e8 神愤条件：同时拥有四种元素活性药水效果且均 ≥10 级（达成一次即可） */
    private static void tickElementBlessing(ServerPlayer player) {
        if (!CurseManager.on(player, D, 7)) return;
        for (var infusion : INFUSIONS) {
            MobEffectInstance inst = player.getEffect(infusion);
            if (inst == null || inst.getAmplifier() < INFUSION_TARGET_AMPLIFIER) return;
        }
        CurseManager.tick(player, D, 7, 1L, 1L);
    }

    // ==================== e9 零号·阅历（条件 + 经验） ====================

    /** e9 阅历条件：经验等级达到 30 级 —— 等级是状态而非事件，用「到点即达成」判定 */
    private static void tickExperienceBlessing(ServerPlayer player) {
        if (!CurseManager.on(player, D, 8)) return;
        if (player.experienceLevel >= XP_LEVEL_GOAL) {
            CurseManager.tick(player, D, 8, 1L, 1L);
        }
    }

    /** e9 阅历：经验获取 ×3（未解锁）/ ×5（已解锁） */
    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            float mult = CurseManager.rev(player, D, 8) ? XP_MULTIPLIER_UNLOCKED
                    : (CurseManager.on(player, D, 8) ? XP_MULTIPLIER_BASE : 1.0f);
            if (mult == 1.0f) return;
            int amount = event.getAmount();
            if (amount <= 0) return;
            event.setAmount(Math.max(amount, Math.round(amount * mult)));
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ e9 阅历经验结算异常", t);
        }
    }

    // ==================== e10 零号·灵通 ====================

    /**
     * e10 灵通：视野内敌对生物发光（未解锁 / 已解锁都生效；解锁后额外给「发光目标受伤 +7%」）。
     * <p>
     * 条件：用「见微知著」解锁 10 种生物图鉴 —— 从随身的见微知著照片里读取
     * {@code lensouls:glimpsed_mobs} 累积种类数（只读，不改任何其它系统的数据）。
     */
    private static void tickGlow(ServerPlayer player) {
        // 条件：累计记录到的生物种类数 ≥ 10（先累加再判定，达成一次即永久解锁）
        if (CurseManager.on(player, D, 9)) {
            scanGlimpsedPhotos(player);
            if (glimpsedCount(player) >= GLIMPSE_GOAL) {
                CurseManager.reach(player, D, 9);
            }
        }

        // 效果：把视野内的敌对生物点亮，离场 / 死亡 / 未激活时清掉（只清我们自己点亮的）
        boolean want = CurseManager.on(player, D, 9) || CurseManager.rev(player, D, 9);
        Set<UUID> owned = GLOWED.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet());
        if (!want) {
            clearGlowed(player, owned);
            return;
        }

        Set<UUID> keep = new HashSet<>();
        if (player.level() instanceof ServerLevel level) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0f);
            List<LivingEntity> candidates = level.getEntitiesOfClass(
                    LivingEntity.class, player.getBoundingBox().inflate(GLOW_RANGE));
            for (LivingEntity candidate : CameraVisibility.filterVisible(
                    level, eye, look, GLOW_HALF_ANGLE, GLOW_RANGE, candidates)) {
                if (!isHostile(candidate)) continue;
                candidate.setGlowingTag(true);
                keep.add(candidate.getUUID());
                owned.add(candidate.getUUID());
            }
        }
        // 不再在视野内 / 已死的：收回发光（只收自己点亮的）
        java.util.Iterator<UUID> it = owned.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (keep.contains(id)) continue;
            Entity entity = player.level() instanceof ServerLevel level ? level.getEntity(id) : null;
            if (entity instanceof LivingEntity living) living.setGlowingTag(false);
            it.remove();
        }
    }

    /** 敌对生物：原版怪（{@link Enemy}）或怪物分类的 {@link Mob} */
    private static boolean isHostile(LivingEntity entity) {
        if (entity instanceof Enemy) return true;
        if (entity instanceof Mob mob) {
            return mob.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
        }
        return false;
    }

    private static void clearGlowed(ServerPlayer player, Set<UUID> owned) {
        if (owned.isEmpty()) return;
        for (UUID id : owned) {
            Entity entity = player.level() instanceof ServerLevel level ? level.getEntity(id) : null;
            if (entity instanceof LivingEntity living) living.setGlowingTag(false);
        }
        owned.clear();
    }

    /**
     * 从随身物品里的「见微知著」照片累积记录到的生物种类。
     * <p>
     * 照片自带的 {@code lensouls:glimpsed_mobs} 只是**那一帧**的生物列表（每次出片都会覆盖），
     * 所以这里把它并进 {@link #KEY_GLIMPSE} 的持久集合里 —— 只增不减，「达成一次即可」。
     * 纯只读扫描：不改照片、不改图鉴、不依赖其它系统。
     */
    private static void scanGlimpsedPhotos(ServerPlayer player) {
        List<String> found = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            collectGlimpsed(inv.getItem(i), found);
        }
        // 照片也可以挂在饰品栏（照片栏），同样算「随身」
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            var equipped = handler.getEquippedCurios();
            for (int i = 0; i < equipped.getSlots(); i++) {
                collectGlimpsed(equipped.getStackInSlot(i), found);
            }
        });
        addList(player, KEY_GLIMPSE, found, GLIMPSE_CAP);
    }

    private static void collectGlimpsed(ItemStack stack, List<String> out) {
        if (stack == null || stack.isEmpty()) return;
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return;
        ListTag list = data.copyTag().getList("lensouls:glimpsed_mobs", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            String id = list.getString(i);
            if (!id.isEmpty()) out.add(id);
        }
    }

    /** 已累积记录到的生物种类数 */
    private static long glimpsedCount(ServerPlayer player) {
        return persisted(player).getList(KEY_GLIMPSE, Tag.TAG_STRING).size();
    }

    // ==================== e11 零号·博识 ====================

    /** e11 博识：记录击杀过的生物种类（种类数阈值在 {@link #tickSpecies} 每秒复核） */
    private static void recordSpecies(ServerPlayer player, LivingEntity victim) {
        if (!active(player)) return;
        String id = typeId(victim);
        if (id == null) return;
        if (!hasKilled(player, id)) {
            addToList(player, KEY_SPECIES, id, SPECIES_CAP);
        }
    }

    /**
     * e11 条件：累计击杀 50 种不同生物 —— 进度直接写「已记录的种类数」，
     * 这样条件文本里的 {@code [N / 50]} 显示的就是真实种类数（不必再自己 tick 累加）。
     */
    private static void tickSpecies(ServerPlayer player) {
        if (!CurseManager.on(player, D, 10)) return;
        long n = speciesCount(player);
        if (CurseManager.progress(player, D, 10) != n) {
            CurseManager.setProgress(player, D, 10, n);     // 变了才同步
        }
        if (n >= SPECIES_GOAL) {
            CurseManager.reach(player, D, 10);
        }
    }

    private static boolean hasKilled(Player player, String typeId) {
        ListTag list = persisted(player).getList(KEY_SPECIES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (typeId.equals(list.getString(i))) return true;
        }
        return false;
    }

    private static long speciesCount(Player player) {
        return persisted(player).getList(KEY_SPECIES, Tag.TAG_STRING).size();
    }

    // ==================== e12 零号·天卷 ====================

    /** e12 天卷：首饰（head）栏位 +2（未解锁）/ +3（已解锁） */
    private static void maintainHeadSlots(ServerPlayer player) {
        final double want;
        if (CurseManager.on(player, D, 11)) {
            want = HEAD_SLOTS_BASE;
        } else if (CurseManager.rev(player, D, 11)) {
            want = HEAD_SLOTS_UNLOCKED;
        } else {
            want = 0.0D;
        }

        var curios = CuriosApi.getCuriosInventory(player).orElse(null);
        if (curios == null) return;
        AttributeModifier current = null;
        for (AttributeModifier modifier : curios.getModifiers().get("head")) {
            if (HEAD_SLOT_MOD.equals(modifier.id())) {
                current = modifier;
                break;
            }
        }
        if (want <= 0.0D) {
            if (current != null) curios.removeSlotModifier("head", HEAD_SLOT_MOD);
            return;
        }
        // 数值没变就不动（槽位修饰符会走同步）
        if (current != null && Math.abs(current.amount() - want) < 1.0e-4) return;
        if (current != null) curios.removeSlotModifier("head", HEAD_SLOT_MOD);
        curios.addTransientSlotModifier("head", HEAD_SLOT_MOD, want,
                AttributeModifier.Operation.ADD_VALUE);
    }

    /** e12 条件：累计装备过 12 件不同饰品（含 cosmetic 栏；物品 ID 去重） */
    private static void tickAccessories(ServerPlayer player) {
        if (!active(player)) return;
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            List<String> found = new ArrayList<>();
            for (var stacksHandler : handler.getCurios().values()) {
                for (int i = 0; i < stacksHandler.getSlots(); i++) {
                    collectAccessory(stacksHandler.getStacks().getStackInSlot(i), found);
                    if (stacksHandler.hasCosmetic()) {
                        collectAccessory(stacksHandler.getCosmeticStacks().getStackInSlot(i), found);
                    }
                }
            }
            addList(player, KEY_ACCESSORIES, found, ACCESSORY_CAP);
            if (CurseManager.on(player, D, 11)
                    && persisted(player).getList(KEY_ACCESSORIES, Tag.TAG_STRING).size() >= ACCESSORY_GOAL) {
                CurseManager.reach(player, D, 11);
            }
        });
    }

    private static void collectAccessory(ItemStack stack, List<String> out) {
        if (stack == null || stack.isEmpty()) return;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) out.add(id.toString());
    }

    // ==================== e13 零号·坚毅 ====================

    /**
     * e13 坚毅：获得负面效果时 20% 概率回复 10 点生命（未解锁）/ 70%（已解锁）。
     * <p>
     * 条件：累计获得 500 次负面效果。
     */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (event.getEntity().level().isClientSide) return;
            MobEffectInstance inst = event.getEffectInstance();
            if (inst == null) return;
            if (inst.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;

            if (CurseManager.on(player, D, 12)) {
                CurseManager.tick(player, D, 12, 1L, DEBUFF_GOAL);
            }
            float chance = CurseManager.rev(player, D, 12) ? DEBUFF_HEAL_CHANCE_UNLOCKED
                    : (CurseManager.on(player, D, 12) ? DEBUFF_HEAL_CHANCE_BASE : 0.0f);
            if (chance > 0.0f && player.getRandom().nextFloat() < chance) {
                player.heal(DEBUFF_HEAL_AMOUNT);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ e13 坚毅结算异常", t);
        }
    }

    // ==================== e14 零号·银环 ====================

    /** e14 银环：所有镜魂冷却 -30%（未解锁）/ -50%（已解锁） */
    private static void applySoulCooldownReduction(ServerPlayer player) {
        float reduce = 0.0f;
        if (CurseManager.on(player, D, 13)) reduce = SOUL_COOLDOWN_REDUCE_BASE;
        else if (CurseManager.rev(player, D, 13)) reduce = SOUL_COOLDOWN_REDUCE_UNLOCKED;
        if (reduce <= 0.0f) return;

        Inventory inv = player.getInventory();
        TimerService timer = TimerService.getInstance();
        long now = player.level().getGameTime();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof com.plumejade.lensouls.item.LensoulItem)) continue;

            SoulCooldownData cd = stack.get(ModDataComponents.SOUL_COOLDOWN.get());
            if (cd == null) continue;

            // 记录「用过的镜魂种类」（e14 的条件）
            ResourceLocation soulId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (soulId != null) {
                addToList(player, KEY_SOUL_KINDS, soulId.toString(), SOUL_KIND_CAP);
            }
            if (CurseManager.on(player, D, 13)
                    && persisted(player).getList(KEY_SOUL_KINDS, Tag.TAG_STRING).size() >= SOUL_KIND_GOAL) {
                CurseManager.reach(player, D, 13);
            }

            long remaining = cd.remainingTicks(now);
            if (remaining <= 0L) continue;

            String itemUuid = getItemUuid(stack, i);
            if (itemUuid == null) continue;
            String flagKey = itemUuid + ":" + cd.endTime();
            if (isCooldownReduced(player, flagKey, now)) continue;

            long cut = Math.max(1L, (long) (cd.duration() * reduce));
            long newRemaining = Math.max(0L, remaining - cut);

            // 双源一起改：内存计时器（真正的拦截点）+ 物品组件（tooltip 与跨重启持久化）
            timer.start(player.getUUID(), "soul_item_" + itemUuid, newRemaining);
            stack.set(ModDataComponents.SOUL_COOLDOWN.get(),
                    new SoulCooldownData(now + newRemaining, (int) Math.max(1L, cd.duration() - cut)));
            CompoundTag cdTag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            cdTag.putLong("SoulCooldownEnd", now + newRemaining);
            cdTag.putInt("SoulCooldownDur", (int) Math.max(1L, cd.duration() - cut));
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(cdTag));

            markCooldownReduced(player, flagKey, now);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.lensouls.zeroforge.cooldown_cut",
                            Math.round(reduce * 100f)), true);
        }
    }

    /** 镜魂的物品实例 UUID（没有就补一个，避免同物品多堆互相覆盖冷却） */
    private static String getItemUuid(ItemStack stack, int fallbackSlot) {
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        String uuid = tag.getString("SoulItemId");
        if (uuid != null && !uuid.isEmpty()) return uuid;
        uuid = "auto_" + fallbackSlot + "_" + java.util.UUID.randomUUID();
        tag.putString("SoulItemId", uuid);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        return uuid;
    }

    /** 该次冷却是否已经减过（标记里存「过期 gameTime」，过期即清） */
    private static boolean isCooldownReduced(ServerPlayer player, String flagKey, long now) {
        CompoundTag tag = persisted(player);
        CompoundTag marked = tag.getCompound(KEY_CD_REDUCED);
        long expire = marked.getLong(flagKey);
        if (expire > 0L && now < expire) return true;
        marked.remove(flagKey);
        // 顺手清理过期项（key 以 itemUuid:endTime 构成，冷却最长 120s，留 5 分钟余量足够）
        marked.getAllKeys().removeIf(k -> marked.getLong(k) < now);
        tag.put(KEY_CD_REDUCED, marked);
        writeBack(player, tag);
        return false;
    }

    private static void markCooldownReduced(ServerPlayer player, String flagKey, long now) {
        CompoundTag tag = persisted(player);
        CompoundTag marked = tag.getCompound(KEY_CD_REDUCED);
        marked.putLong(flagKey, now + 6_000L);
        tag.put(KEY_CD_REDUCED, marked);
        writeBack(player, tag);
    }

    // ==================== 事件入口 ====================

    /** 每 tick：e1 燃魂的吞噬 / debuff 维持、e7 易手（这两个是「持续性」的，按 tick 判定） */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (player.level().isClientSide) return;
            if (!active(player)) return;
            tickDevour(player);
            tickHandSwap(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ 每 tick 结算异常", t);
        }
    }

    /** 每秒：条件复核、点数类维护、e14 冷却削减 */
    @SubscribeEvent
    public static void onMaintain(PlayerTickEvent.Post event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (player.level().isClientSide) return;

            boolean active = active(player);
            if (!active) {
                // 摘下即摘掉所有「我们加的点数类修饰符」与发光
                maintainAttackSpeed(player);
                maintainHeadSlots(player);
                clearGlowed(player, GLOWED.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet()));
                return;
            }
            if (player.tickCount % MAINTAIN_INTERVAL != 0) return;

            maintainAttackSpeed(player);
            maintainHeadSlots(player);
            tickElementBlessing(player);
            tickExperienceBlessing(player);
            tickSpecies(player);
            tickAccessories(player);
            tickGlow(player);
            applySoulCooldownReduction(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ 周期结算异常", t);
        }
    }

    /** 击杀：e2 反噬 / e4 断供 / e5 封库 / e7 易手 的条件与效果 + e11 记录 */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        try {
            if (event.getEntity().level().isClientSide) return;
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
            if (event.getEntity() == player) return;

            recordSpecies(player, event.getEntity());
            if (!active(player)) return;

            // e2 反噬：自伤 / 击杀回血；低血量击杀计数
            if (event.getEntity().getMaxHealth() > 0f) {
                onKillSelfHurt(player);
                if (CurseManager.on(player, D, 1)
                        && player.getHealth() < player.getMaxHealth() * LOW_HEALTH_RATIO) {
                    CurseManager.tick(player, D, 1, 1L, LOW_KILL_GOAL);
                }
            }

            // e4 断供 条件：击杀远古遗魂
            // e5 封库 条件：击杀炼狱飞龙
            String killed = typeId(event.getEntity());
            if ("cataclysm:ancient_remnant".equals(killed)) {
                CurseManager.tick(player, D, 3, 1L, 1L);
            }
            if ("block_factorys_bosses:infernal_dragon".equals(killed)) {
                CurseManager.tick(player, D, 4, 1L, 1L);
            }

            // e7 易手 条件：在 Y < 0 处击杀 200 只
            if (CurseManager.on(player, D, 6) && player.getY() < 0.0D) {
                CurseManager.tick(player, D, 6, 1L, LOW_Y_KILL_GOAL);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ 击杀结算异常", t);
        }
    }

    /** e4 断供 反转：免疫反胃（持续清除）；诅咒态维持（在 onDealDamage 里按饱食度乘算） */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (event.getEffectInstance() == null) return;
            if (event.getEffectInstance().getEffect().value() != MobEffects.CONFUSION.value()) return;
            if (CurseManager.rev(player, D, 3)) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑦ e4 反胃免疫判定异常", t);
        }
    }

    /** 登出 / 重生 / 切档：清理运行期缓存 + 收回我们点亮的发光 */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayer(event.getEntity());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        clearPlayer(event.getEntity());
    }

    private static void clearPlayer(Player player) {
        if (player == null) return;
        UUID id = player.getUUID();
        Set<UUID> owned = GLOWED.remove(id);
        if (owned != null && player.level() instanceof ServerLevel level) {
            for (UUID entityId : owned) {
                Entity entity = level.getEntity(entityId);
                if (entity instanceof LivingEntity living) living.setGlowingTag(false);
            }
        }
        NO_SOUL_TICKS.remove(id);
        SELF_HURT_UNTIL.remove(id);
        NEXT_SWAP.remove(id);
        STRENGTH_UNTIL.remove(id);
        LAST_DIMENSION.remove(id);
        LAST_MAIN_HASH.remove(id);
    }

    // ==================== 对外查询 ====================

    /** 供调试 / 其它系统查询：该玩家当前是否戴着零号誓炉 */
    public static boolean wears(Player player) {
        return active(player);
    }

    /** 供其它系统查询：e6 是否为「无人机造成的伤害」（口径与 DamageHandler 一致） */
    public static boolean isDroneSource(net.minecraft.world.damagesource.DamageSource source) {
        return isDroneDamage(source);
    }

    /** 供其它系统查询：该实体是否由本类点亮（e10） */
    public static boolean isZeroforgeGlowing(Entity entity) {
        if (entity == null) return false;
        for (Set<UUID> owned : GLOWED.values()) {
            if (owned.contains(entity.getUUID())) return true;
        }
        return false;
    }
}
