package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.item.ModItems;
import com.plumejade.lensouls.mixin.EntityInvulnerableTimeAccessor;
import com.plumejade.lensouls.network.TwistSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
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
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.HashMap;
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
 *   <li>祸之可能性：每 30s 生成 5 只随机敌对生物</li>
 *   <li>挫败：受击攻击面板 ×0.94/层（5s）</li>
 *   <li>食之无味：由 FoodDataMixin 阻断食物回血</li>
 *   <li>失忆：由 CraftingMenuMixin 禁复制之魂合成（掉落保留）</li>
 *   <li>疯狂：合成 +1 扭曲值（上限 200），受击 +0.2%/点、造成 +1%/点，死亡归零</li>
 *   <li>恶意：元素附加伤害 +12%（DamageHandler 内应用）</li>
 * </ul>
 * 生命周期防护：登录同步/清残留、登出恢复创造并清内存层、死亡清层+扭曲归零，
 * 崩溃重进由每 tick 自愈 + 持久化原模式键兜底。
 */
public class FeatherAbyssHandler {

    public static final String KEY_TWIST = "lensouls:abyss_twist";
    public static final String KEY_GM_BEFORE = "lensouls:abyss_gm_before";
    private static final String KEY_DOOM_TIMER = "lensouls:abyss_doom_timer";
    private static final String KEY_SPAWN_TIMER = "lensouls:abyss_spawn_timer";
    private static final String SNOWBALL_TAG = "lensouls:feather_snowball";

    /** 疯狂：扭曲值上限 200 */
    public static final int MAX_TWIST = 200;
    /** 安魂曲：全局受伤倍率 */
    public static final float REQUIEM_TAKEN = 1.33f;
    /** 厄运：挖方块触发概率（0.8%） */
    public static final float DOOM_CHANCE = 0.008f;
    /** 厄运：每分钟 debuff 间隔（1200 tick） */
    public static final int DOOM_INTERVAL = 1200;
    /** 厄运：debuff 时长 20s */
    public static final int DOOM_DURATION = 400;
    /** 祸之可能性：30s 生成间隔 */
    public static final int SPAWN_INTERVAL = 600;
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
        if (player.tickCount % 20 != 0) return;
        if (has) {
            applyRequiem(player);
            refreshStackModifiers(player);
            advanceTimers(player);
        } else {
            removeRequiem(player);
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

    // ========== 安魂曲：maxHealth -1/存档游戏天 + 全局受伤 +33% ==========

    private static void applyRequiem(ServerPlayer player) {
        // 存档的游戏天数（世界累计 dayTime/24000，睡一觉 +1 天）
        int days = (int) (player.level().getDayTime() / 24000L);
        AttributeInstance max = player.getAttribute(Attributes.MAX_HEALTH);
        if (max == null) return;
        if (days <= 0) {
            max.removeModifier(REQUIEM_MODIFIER);
            return;
        }
        double want = -days;
        AttributeModifier mod = max.getModifier(REQUIEM_MODIFIER);
        if (mod == null || Math.abs(mod.amount() - want) > 0.001) {
            max.removeModifier(REQUIEM_MODIFIER);
            max.addTransientModifier(
                    new AttributeModifier(REQUIEM_MODIFIER, want, AttributeModifier.Operation.ADD_VALUE));
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
            event.setNewDamage(event.getNewDamage() * REQUIEM_TAKEN);
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

    // ========== 祸之可能性：30s 生成 5 只 ==========

    private static void spawnDisasterMobs(ServerPlayer player) {
        EntityType<?> type = DISASTER_TYPES[player.getRandom().nextInt(DISASTER_TYPES.length)];
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < 5; i++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            double dist = 3 + player.getRandom().nextDouble() * 5;
            double x = player.getX() + Math.cos(angle) * dist;
            double z = player.getZ() + Math.sin(angle) * dist;
            Entity e = type.create(level);
            if (e == null) continue;
            e.setPos(x, player.getY(), z);
            level.addFreshEntity(e);
        }
    }

    // ========== 计时器（持久化，掉线不丢） ==========

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
        long spawnNext = tag.getLong(KEY_SPAWN_TIMER);
        if (spawnNext <= 0L) {
            tag.putLong(KEY_SPAWN_TIMER, now + SPAWN_INTERVAL);
            dirty = true;
        } else if (now >= spawnNext) {
            spawnDisasterMobs(player);
            tag.putLong(KEY_SPAWN_TIMER, now + SPAWN_INTERVAL);
            dirty = true;
        }
        if (dirty) writeBack(player, tag);
    }

    /** 摘下羽毛：重置计时器（重新戴上从新周期开始） */
    private static void resetTimers(ServerPlayer player) {
        CompoundTag tag = persisted(player);
        boolean dirty = false;
        if (tag.contains(KEY_DOOM_TIMER)) { tag.remove(KEY_DOOM_TIMER); dirty = true; }
        if (tag.contains(KEY_SPAWN_TIMER)) { tag.remove(KEY_SPAWN_TIMER); dirty = true; }
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

    /** 登录：疯狂值同步 + 清残留内存层（崩溃/断线兜底） */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        clearStackState(player.getUUID());
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
