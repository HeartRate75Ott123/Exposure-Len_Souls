package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.item.ModItems;
import com.plumejade.lensouls.mixin.EntityInvulnerableTimeAccessor;
import com.plumejade.lensouls.network.AbyssCountdownPacket;
import com.plumejade.lensouls.network.TwistSyncPacket;
import com.plumejade.lensouls.particle.ModParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
 *   <li>安魂曲：实时按游玩天数 maxHealth -1，全局受伤 +33%</li>
 *   <li>愚钝：经验减半</li>
 *   <li>自闭：16 格内生物加入世界 → 0.5 魔法伤害 + 护甲层(×0.75/层, 3s)</li>
 *   <li>厄运：挖方块 0.8% 生成 15 蠹虫；每分钟 2 个不重复 debuff（20s, 等级3-20）</li>
 *   <li>祸之可能性：充能 15min（扭曲值越高越短，下限 2min），充能完毕且身边 7 格内 5+ 怪物时引动末影龙之吼 + 3s 倒计时，随后召唤 5 只（僵尸/骷髅/洞穴蜘蛛其一）</li>
 *   <li>挫败：受击攻击面板 ×0.94/层（5s）</li>
 *   <li>食之无味：由 FoodDataMixin 阻断食物回血</li>
 *   <li>失忆：由 CraftingMenuMixin 禁复制之魂合成（掉落保留）</li>
 *   <li>疯狂：合成 +1 扭曲值（上限 200），受击 +0.2%/点、造成 +1%/点，死亡归零，击杀怪物 3% 降 1</li>
 *   <li>恶意：元素附加伤害 +12%（DamageHandler 内应用）</li>
 * </ul>
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

    /** 佩戴检测：Curios 任意槽位持有折翼沉渊 */
    public static boolean hasAbyss(Player player) {
        if (player == null) return false;
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.FEATHER_ABYSS.get())).isPresent())
                .orElse(false);
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
        boolean has = hasAbyss(player);
        // 创造模式切换：每 tick 响应（手持判定开销小）
        updateAutoCreative(player, has);
        // 安魂曲：每 tick 实时读取天数并应用（内部仅变化时更新，开销小）
        if (has) {
            applyRequiem(player);
        } else {
            removeRequiem(player);
        }
        if (player.tickCount % 20 != 0) return;
        if (has) {
            refreshStackModifiers(player);
            advanceTimers(player);
        } else {
            removeStackModifiers(player);
            resetTimers(player);
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

    // ========== 安魂曲：maxHealth -1/天（下限1） + 受到的伤害每天 +33% ==========

    /** 安魂曲天数：镜像 InControl——主世界昼夜转换计数（SavedData 持久化，睡一觉 +1） */
    private static int getSaveDays(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return 0;
        return FeatherAbyssDayData.getData(server).getDaycounter();
    }

    /** 每 tick 推进安魂曲天数计数（主世界昼夜转换，睡一觉/过一夜 +1） */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        FeatherAbyssDayData.getData(server).tick(overworld);
        processSummonScheduler();
    }

    private static void applyRequiem(ServerPlayer player) {
        long rawDayTime = player.level().getDayTime();
        int days = getSaveDays(player);
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
        if (max == null) {
            LOGGER.info("[AbyssRequiem] maxHealth attribute null");
            return;
        }
        double before = max.getValue();
        // 自然上限（去掉本修饰符）→ 保证最终上限 ≥ 1：penalty = min(days, natural - 1)
        double natural = before + days;
        int penalty = (int) Math.max(0, Math.min(days, Math.floor(natural - 1.0)));
        if (penalty <= 0) {
            if (max.getModifier(REQUIEM_MODIFIER) != null) {
                max.removeModifier(REQUIEM_MODIFIER);
                LOGGER.info("[AbyssRequiem] removed: getDayTime={} days={}", rawDayTime, days);
            }
            return;
        }
        double want = -penalty;
        AttributeModifier mod = max.getModifier(REQUIEM_MODIFIER);
        boolean changed = mod == null || Math.abs(mod.amount() - want) > 0.001;
        if (changed) {
            max.removeModifier(REQUIEM_MODIFIER);
            max.addTransientModifier(
                    new AttributeModifier(REQUIEM_MODIFIER, want, AttributeModifier.Operation.ADD_VALUE));
            double after = max.getValue();
            // 让扣上限立刻反映到血条
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
            LOGGER.info("[AbyssRequiem] apply getDayTime={} days={} natural={} penalty={} maxBefore={} maxAfter={}",
                    rawDayTime, days, natural, penalty, before, after);
        }
    }

    private static void removeRequiem(ServerPlayer player) {
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) max.removeModifier(REQUIEM_MODIFIER);
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
        applyMultStack(player, Attributes.ARMOR, ARMOR_MODIFIER, 0.75, armor.size());
        List<Long> attack = prune(ATTACK_STACKS, player.getUUID(), now);
        applyMultStack(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER, 0.94, attack.size());
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

        // 玩家受击
        if (event.getEntity() instanceof ServerPlayer player && hasAbyss(player)) {
            // 安魂曲：受到的伤害按天累加 ×(1 + 0.33×天)
            int saveDays = getSaveDays(player);
            event.setNewDamage(event.getNewDamage() * (1.0f + REQUIEM_TAKEN_PER_DAY * saveDays));
            int twist = getTwist(player);
            if (twist > 0) {
                event.setNewDamage(event.getNewDamage() * (1.0f + twist * 0.002f));
            }
            // 神秘雪球：以攻击佩戴者的怪物为中心，环形发射 8 个雪球
            Entity attacker = event.getSource().getEntity();
            if (attacker instanceof LivingEntity livingAttacker && !(attacker instanceof Player)) {
                spawnSnowballRing(livingAttacker);
            }
            // 挫败：受击加攻击层
            addAttackStack(player);
        }

        // 玩家造成伤害（疯狂：+1%/点）
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && hasAbyss(attacker)) {
            int twist = getTwist(attacker);
            if (twist > 0) {
                event.setNewDamage(event.getNewDamage() * (1.0f + twist * 0.01f));
            }
        }
    }

    /** 以攻击者（怪物）为中心发射一圈 8 个雪球（标记 + owner），命中伤害由 onProjectileImpact 结算 */
    private static void spawnSnowballRing(LivingEntity center) {
        Level level = center.level();
        for (int i = 0; i < 8; i++) {
            Snowball snowball = new Snowball(level, center);
            snowball.setPos(center.getX(), center.getEyeY() - 0.2, center.getZ());
            double angle = (i / 8.0) * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            snowball.shoot(dir.x, dir.y + 0.1, dir.z, 0.6f, 1.0f);
            snowball.getPersistentData().putBoolean(SNOWBALL_TAG, true);
            level.addFreshEntity(snowball);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide) return;
        if (!projectile.getPersistentData().getBoolean(SNOWBALL_TAG)) return;
        HitResult hit = event.getRayTraceResult();
        if (hit instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (target instanceof LivingEntity && target != projectile.getOwner()) {
                // 清除无敌帧（命中玩家时同样生效，保证 3 点魔法伤害必中）
                ((EntityInvulnerableTimeAccessor) (Object) target).lensouls$setInvulnerableTime(0);
                target.hurt(projectile.level().damageSources().magic(), 3.0f);
            }
        }
    }

    // ========== 愚钝：经验减半 ==========

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (event.getEntity() instanceof ServerPlayer player && hasAbyss(player)) {
            // 经验减半但至少保留 1（避免小数值直接归零完全阻止获取）
            event.setAmount((int) Math.ceil(event.getAmount() / 2.0));
        }
    }

    // ========== 自闭：生物加入世界 ==========

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity) || entity instanceof Player) return;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                entity.getBoundingBox().inflate(16))) {
            if (!hasAbyss(player)) continue;
            player.hurt(player.level().damageSources().magic(), 0.5f);
            addArmorStack(player);
        }
    }

    // ========== 厄运：挖掘 + 每分钟 debuff ==========

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        if (!hasAbyss(player)) return;
        if (player.getRandom().nextFloat() >= DOOM_CHANCE) return;
        BlockPos pos = event.getPos();
        for (int i = 0; i < 15; i++) {
            Entity silverfish = EntityType.SILVERFISH.create(level);
            if (silverfish == null) continue;
            silverfish.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            level.addFreshEntity(silverfish);
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

    // ========== 祸之可能性：充能 → 触发 → 召唤 ==========

    /** 单次召唤计划：粒子已显，延迟后于落点生成怪物 */
    private static final class SummonPlan {
        final long time;
        final ServerLevel level;
        final List<Vec3> positions;
        final EntityType<?> type;

        SummonPlan(long time, ServerLevel level, List<Vec3> positions, EntityType<?> type) {
            this.time = time;
            this.level = level;
            this.positions = positions;
            this.type = type;
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

    /** 执行一次召唤：生成 5 个精灵粒子落点，1s 后于落点生成 5 只怪物（其一类型） */
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
            LOGGER.info("[AbyssSummon] performSummon particle#{} at ({},{},{})", i, x, py, z);
        }
        PENDING_SUMMONS.add(new SummonPlan(level.getGameTime() + SUMMON_DELAY_TICKS, level, positions, type));
        LOGGER.info("[AbyssSummon] queued {} positions, type={}, spawn at gameTime={}", positions.size(), type, level.getGameTime() + SUMMON_DELAY_TICKS);
    }

    private static void spawnAtPositions(SummonPlan plan) {
        int spawned = 0;
        for (Vec3 p : plan.positions) {
            Entity e = plan.type.create(plan.level);
            if (e == null) continue;
            e.setPos(p.x, p.y, p.z);
            plan.level.addFreshEntity(e);
            spawned++;
            LOGGER.info("[AbyssSummon] spawned {} at ({},{},{})", e.getType(), p.x, p.y, p.z);
        }
        LOGGER.info("[AbyssSummon] spawnAtPositions done, total={}", spawned);
    }

    /** 测试指令：直接从「触发倒计时」阶段开始（龙吼 + 3s 倒计时 + 召唤），不污染充能计时器 */
    public static void triggerTestCalamity(ServerPlayer player) {
        if (!hasAbyss(player)) return;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.0f);
        AbyssCountdownPacket.send(player);
        DELAYED_SUMMONS.add(new DelayedSummon(player.level().getGameTime() + COUNTDOWN_TICKS, player));
        LOGGER.info("[AbyssSummon] triggerTestCalamity by {}, countdown until gameTime={}", player.getName().getString(), player.level().getGameTime() + COUNTDOWN_TICKS);
    }

    /** 每服务器刻推进延迟召唤与测试召唤队列 */
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
                    performSummon(p);
                    it.remove();
                }
            }
        }
    }

    // ========== 计时器（持久化，掉线不丢） ==========

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
            LOGGER.info("[AbyssCharge] twist={} interval {}->{} progress={} next {}->{}",
                    getTwist(player), oldInterval, newInterval, progress, chargeNext, newNext);
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
            applyDoomDebuffs(player);
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
                performSummon(player);
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
        if (event.getEntity() instanceof ServerPlayer player && hasAbyss(player)) {
            addTwist(player, 1);
        }
    }

    // ========== 生命周期防护 ==========

    /** 死亡：清内存层 + 扭曲归零（PlayerPersisted 写入，复活复制后生效） */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        clearStackState(player.getUUID());
        if (hasAbyss(player)) {
            setTwist(player, 0);
        }
    }

    /** 疯狂：击杀怪物有 3% 概率降低 1 点扭曲值 */
    @SubscribeEvent
    public static void onMobKilled(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof Player) return;
        if (!(event.getEntity() instanceof Monster)) return;
        Entity killer = event.getSource().getEntity();
        ServerPlayer player = null;
        if (killer instanceof ServerPlayer sp) {
            player = sp;
        } else if (killer instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer sp) {
            player = sp;
        }
        if (player != null && hasAbyss(player) && player.getRandom().nextFloat() < 0.03f) {
            addTwist(player, -1);
        }
    }

    /**
     * 死亡重生：立即重挂安魂曲修饰符。
     * <p>
     * {@code restoreFrom} 只复制属性基础值（assignBaseValues），修饰符一律丢失——
     * 无论 transient 还是 permanent。这里在重生实体创建瞬间重挂，消除重生间隙。
     * Clone 时新实体 curio 可能未就绪，用旧实体佩戴状态作主判据。
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        if (!event.isWasDeath()) return;
        Player oldPlayer = event.getOriginal();
        if (hasAbyss(oldPlayer) || hasAbyss(newPlayer)) {
            applyRequiem(newPlayer);
        }
    }

    /** 登录：疯狂值同步 + 清残留内存层 + 安魂曲即时重挂（崩溃/断线兜底） */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        clearStackState(player.getUUID());
        if (hasAbyss(player)) {
            TwistSyncPacket.send(player, Math.round(getTwist(player) / 2.0f));
            applyRequiem(player);
        }
        // 卡创造兜底：下一 tick 的 updateAutoCreative 会按当前手持状态自愈
    }

    /** 登出：恢复被强制切换的创造模式 + 清内存层 */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        clearStackState(player.getUUID());
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
}
