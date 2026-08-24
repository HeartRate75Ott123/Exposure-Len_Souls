package com.plumejade.lensouls.entity;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.effect.ElementInfusionEffect;
import com.plumejade.lensouls.network.PhantomStartPacket;
import com.plumejade.lensouls.network.PhantomStopPacket;
import com.plumejade.lensouls.network.PhantomSkillPacket;
import com.plumejade.lensouls.network.PhantomTickPacket;
import net.neoforged.fml.ModList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BOSS 虚影幻灵序列管理器（服务端单例）。
 * <p>
 * 虚拟期间玩家被锁定在观察位（每 tick 强制传送），幻灵固定在召唤位置。
 */
public class BossPhantomManager {

    private static final BossPhantomManager INSTANCE = new BossPhantomManager();
    public static final int PHANTOM_TOTAL_TICKS = 200;

    private final Map<UUID, BossPhantomData> activePhantoms = new ConcurrentHashMap<>();
    /** 幻灵期间玩家原始游戏模式（用于旁观者模式恢复） */
    private final Map<UUID, net.minecraft.world.level.GameType> originalGameTypes = new ConcurrentHashMap<>();

    public static BossPhantomManager getInstance() { return INSTANCE; }

    /** 通过幻灵实体 ID 反查玩家 */
    public ServerPlayer findPlayerByPhantomEntityId(int phantomEntityId) {
        for (var entry : activePhantoms.entrySet()) {
            if (entry.getValue().phantomEntityId() == phantomEntityId) {
                return findPlayer(entry.getKey());
            }
        }
        return null;
    }

    // ========== 启动 ==========

    public void startPhantom(ServerPlayer player, BossPhantomType type, String descId, int amplifier) {
        UUID pid = player.getUUID();

        if (activePhantoms.containsKey(pid)) {
            LenSouls.LOGGER.warn("[幻灵] 玩家 {} 已有幻灵，忽略", player.getName().getString());
            return;
        }

        double ox = player.getX(), oy = player.getY(), oz = player.getZ();
        float oyaw = player.getYRot(), opitch = player.getXRot();

        // 立即刷新元素效果（使用物品实际等级，而非 type 硬编码）
        player.removeEffect(type.getEffectHolder());
        player.addEffect(new MobEffectInstance(type.getEffectHolder(),
                Config.DEFAULT_DURATION.get() * 20, amplifier, false, false, false));
        ElementInfusionEffect.setPlayerData(player, type.getElement(), type.shouldApplySlowness(), descId);
        player.sendSystemMessage(Component.translatable("message.lensouls.soul_activated",
                Component.translatable(descId)));

        // 旁观者模式（不移动玩家位置，不施加效果）
        originalGameTypes.put(player.getUUID(), player.gameMode.getGameModeForPlayer());
        player.getPersistentData().putInt("lensouls:originalGameType",
                player.gameMode.getGameModeForPlayer().getId());
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);

        // 借真身驱动（模组加载 + className 非空时借用真实 BOSS 实体）
        if (!type.getClassName().isEmpty() && type.isModLoaded()) {
            startBorrowedEntity(player, type, descId, amplifier, ox, oy, oz, oyaw, opitch);
            return;
        }

        // 降级：BossPhantomEntity 旧路径（模组未加载时备用）
        // 无需施加迟缓/抗性，旁观者模式已防止被攻击

        // 生成幻灵
        BossPhantomEntity phantom = new BossPhantomEntity(
                player.level(), type, pid, ox, oy + 1.5, oz, oyaw);
        phantom.setCustomName(Component.translatable("entity.lensouls.boss_phantom." + type.name().toLowerCase()));
        phantom.setCustomNameVisible(false);
        Object lionAnim = getLionfishAnimation(type);
        if (lionAnim != null) phantom.startAnimation(lionAnim);
        player.level().addFreshEntity(phantom);

        double py = oy + 1.5; // 虚影生成Y
        PacketDistributor.sendToPlayer(player,
                new PhantomStartPacket(pid, type, PHANTOM_TOTAL_TICKS, phantom.getId(), ox, py, oz, oyaw));

        // 入场：传送门浮现粒子（灾变系：END_PORTAL 椭圆传送门）
        spawnEntryPortal(player.serverLevel(), type, ox, py, oz);

        activePhantoms.put(pid, new BossPhantomData(
                type, pid, PHANTOM_TOTAL_TICKS, PHANTOM_TOTAL_TICKS, descId,
                phantom.getId(), amplifier, ox, oy, oz, oyaw, opitch, ox, oy, oz));

    }

    /** 玩家当前是否有活跃的幻灵表演 */
    public boolean hasActivePhantom(UUID playerId) {
        return activePhantoms.containsKey(playerId);
    }

    public void cancelPhantom(ServerPlayer player) {
        BossPhantomData d = activePhantoms.get(player.getUUID());
        if (d != null) endPhantom(player, d, false);
    }

    public void clearPlayer(UUID pid) {
        activePhantoms.remove(pid);
    }

    // ========== 借真身驱动（泛化版本） ==========

    /**
     * 泛化借体方法：通过 BossPhantomType 元数据反射构造任意 BOSS 实体，
     * 设 target = 附近敌对生物，让 AI 自动出招。
     * 渲染层由 LivingEntityPhantomMixin 替换为半透明。
     */
    private void startBorrowedEntity(ServerPlayer player, BossPhantomType type, String descId, int amplifier,
                                      double ox, double oy, double oz, float oyaw, float opitch) {
        ServerLevel level = player.serverLevel();
        double py = oy + 1.5;
        Entity entity = null;
        boolean addedToWorld = false;

        try {
            // 1. 反射获取 EntityType（通过 ModEntities 字段）
            Class<?> modEntitiesClass = Class.forName(type.getModEntitiesClass());
            var field = modEntitiesClass.getDeclaredField(type.getEntityTypeFieldName());
            var holder = (java.util.function.Supplier<?>) field.get(null);
            Object entityType = holder.get();

            // 2. 反射构造实体 (EntityType, Level)
            Class<?> entityClass = Class.forName(type.getClassName());
            var ctor = entityClass.getDeclaredConstructor(net.minecraft.world.entity.EntityType.class, Level.class);
            entity = (Entity) ctor.newInstance(entityType, level);
            entity.setPos(ox, py, oz);
            entity.setYRot(oyaw);
            entity.setOldPosAndRot();
            if (entity instanceof LivingEntity le) {
                le.yBodyRot = oyaw;
                le.yBodyRotO = oyaw;
                le.yHeadRot = oyaw;
                le.yHeadRotO = oyaw;
            }

            // 3. 幻灵标记（双重标记：persistentData + customName，对抗实体类 save/load 丢失）
            entity.getPersistentData().putBoolean("lensouls:phantom", true);
            // 记录镜魂等级（1-5），供穿透伤害按等级取值
            entity.getPersistentData().putInt("lensouls:phantom_level", amplifier + 1);
            entity.setCustomName(Component.translatable("entity.lensouls.boss_phantom." + type.name().toLowerCase()));
            entity.setCustomNameVisible(false);
            // 持久化保存玩家原始游戏模式（对抗断线丢失）
            player.getPersistentData().putInt("lensouls:originalGameType",
                    player.gameMode.getGameModeForPlayer().getId());

            // 4. 类型特定初始化（Ignis: blockingProgress, KnightPhantom: 攻击态, Naga: 家园限制）
            type.initEntity(entity, level);

            // Naga 有严格家园限制，需将 restrictionPoint 设到召唤位，否则不会攻击玩家
            if (type == BossPhantomType.NAGA) {
                try {
                    Class<?> nagaClass = Class.forName("twilightforest.entity.boss.Naga");
                    var rpMethod = nagaClass.getMethod("setRestrictionPoint", net.minecraft.core.GlobalPos.class);
                    var globalPos = net.minecraft.core.GlobalPos.of(level.dimension(),
                            net.minecraft.core.BlockPos.containing(ox, oy, oz));
                    rpMethod.invoke(entity, globalPos);
                } catch (Exception e) {
                    LenSouls.LOGGER.warn("[幻灵] Naga setRestrictionPoint 失败", e);
                }
            }

            // 5. 找最近敌对生物作为 target
            LivingEntity target = findNearestEnemy(level, ox, py, oz);
            if (target != null && entity instanceof Mob mob) {
                mob.setTarget(target);
            } else {
                LenSouls.LOGGER.warn("[幻灵] {} 未找到敌对 target", type.name());
            }

            // 6. 加入世界（addFreshEntity 会触发 startSeenByPlayer → boss bar 出现）
            level.addFreshEntity(entity);
            addedToWorld = true;
            // 加入后再清除 boss bar（startSeenByPlayer 新增的玩家被 removeAllPlayers 移除）
            com.plumejade.lensouls.boss.BossBarCache.clearBossBar(entity);

            // 7. 召唤瞬间 AOE 伤害（排除幻灵自身）
            dealSpawnAOE(level, type, ox, py, oz, entity);

            // 8. 发送开始包 + 入场粒子
            PacketDistributor.sendToPlayer(player, new PhantomStartPacket(player.getUUID(), type,
                    PHANTOM_TOTAL_TICKS, entity.getId(), ox, py, oz, oyaw));
            spawnEntryPortal(level, type, ox, py, oz);

            activePhantoms.put(player.getUUID(), new BossPhantomData(
                    type, player.getUUID(), PHANTOM_TOTAL_TICKS, PHANTOM_TOTAL_TICKS, descId,
                    entity.getId(), amplifier, ox, oy, oz, oyaw, opitch, ox, oy, oz));


        } catch (Throwable t) {
            LenSouls.LOGGER.error("[幻灵] {} 启动失败", type.name(), t);
            // 实体已加入世界但未进入 activePhantoms → 立即清理
            if (addedToWorld && entity != null && entity.isAlive()) {
                entity.discard();
            }
            // 恢复玩家游戏模式
            net.minecraft.world.level.GameType original = originalGameTypes.remove(player.getUUID());
            if (original != null) player.setGameMode(original);
            player.getPersistentData().remove("lensouls:originalGameType");
        }
    }

    /** 召唤瞬间径向 AOE（排除幻灵自身；对所有玩家免疫以防误伤队友） */
    private static void dealSpawnAOE(ServerLevel level, BossPhantomType type,
                                      double cx, double cy, double cz, Entity self) {
        double range = type == BossPhantomType.OBLITERATOR ? 30.0 : 10.0;
        DamageSource piercing = level.damageSources().magic();
        AABB aabb = new AABB(cx - range, cy - range, cz - range,
                cx + range, cy + range, cz + range);
        int count = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, aabb)) {
            if (e instanceof Player || !e.isAlive()) continue;
            if (e == self) continue; // 不伤害幻灵自身
            if (e.distanceToSqr(cx, cy, cz) > range * range) continue;
            e.hurt(piercing, 50.0f + type.getSkillDamage());
            count++;
        }
    }

    /** 20 格内优先找 BOSS 级目标（血量 > 200 或有 BossBar），没有才找最近普通敌对 */
    private static LivingEntity findNearestEnemy(ServerLevel level, double x, double y, double z) {
        AABB box = new AABB(x - 20, y - 10, z - 20, x + 20, y + 10, z + 20);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box);

        // 第一轮：找 BOSS 级目标（血量 > 200）
        LivingEntity boss = null;
        double bossDist = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            if (e instanceof Player || !e.isAlive()) continue;
            if (e.getMaxHealth() > 200) {
                double d = e.distanceToSqr(x, y, z);
                if (d < bossDist) { bossDist = d; boss = e; }
            }
        }
        if (boss != null) return boss;

        // 第二轮：普通敌对（无玩家）
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            if (e instanceof Player || !e.isAlive()) continue;
            double d = e.distanceToSqr(x, y, z);
            if (d < nearestDist) { nearestDist = d; nearest = e; }
        }
        return nearest;
    }

    /** 跨所有已加载维度按 network ID 查找实体（主线程安全） */
    @javax.annotation.Nullable
    private static Entity findEntityAcrossDimensions(int entityId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        for (ServerLevel sl : server.getAllLevels()) {
            Entity e = sl.getEntity(entityId);
            if (e != null) return e;
        }
        return null;
    }

    /** 利维坦：强制水域导航 + 悬浮 */
    private static void forceLeviathanWaterMode(Entity entity) {
        try {
            Class<?> clazz = Class.forName("com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Leviathan.The_Leviathan_Entity");
            java.lang.reflect.Field landNav = clazz.getDeclaredField("isLandNavigator");
            landNav.setAccessible(true);
            landNav.set(entity, false);
            entity.setNoGravity(true);
        } catch (Exception e) {
            LenSouls.LOGGER.error("[幻灵] 利维坦水域模式切换失败", e);
        }
    }

    /** 利维坦：清零内部攻击冷却 */
    private static void resetLeviathanCooldowns(Entity entity) {
        try {
            Class<?> clazz = entity.getClass();
            for (String fn : new String[]{"hunting_cooldown", "bite_cooldown", "melee_cooldown", "makePortalCooldown"}) {
                try {
                    java.lang.reflect.Field f = clazz.getDeclaredField(fn);
                    f.setAccessible(true); f.setInt(entity, 0);
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception e) {
            LenSouls.LOGGER.error("[幻灵] 利维坦冷却清零失败", e);
        }
    }

    /** 幻影骑士：维持可见+攻击力加成，阻止 HOVER 回退，自动选敌 */
    private static void forceKnightPhantomAttackMode(Entity entity) {
        try {
            Class<?> clazz = Class.forName("twilightforest.entity.boss.KnightPhantom");
            // FLAG_CHARGING = true → visibleSize + 攻击力+7（安全兜底）
            var dataAccessorField = clazz.getDeclaredField("FLAG_CHARGING");
            dataAccessorField.setAccessible(true);
            var dataAccessor = (net.minecraft.network.syncher.EntityDataAccessor<Boolean>) dataAccessorField.get(null);
            if (!entity.getEntityData().get(dataAccessor)) {
                entity.getEntityData().set(dataAccessor, true);
            }
            // formation 被 AI 切回 HOVER 时调用 switchToFormation 重新进入攻击态（正确处理 updateMyNumber + setChargingAtPlayer）
            java.lang.reflect.Field formationField = clazz.getDeclaredField("currentFormation");
            formationField.setAccessible(true);
            Class<?> formationEnum = Class.forName("twilightforest.entity.boss.KnightPhantom$Formation");
            Object current = formationField.get(entity);
            Object hover = Enum.valueOf((Class<Enum>) formationEnum, "HOVER");
            if (current == hover) {
                Object attackStart = Enum.valueOf((Class<Enum>) formationEnum, "ATTACK_PLAYER_START");
                clazz.getMethod("switchToFormation", formationEnum).invoke(entity, attackStart);
            }
            // 无目标时自动选敌（仅非玩家实体）
            if (entity instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == null) {
                net.minecraft.world.entity.LivingEntity t = findNearestEnemy(
                        (net.minecraft.server.level.ServerLevel) entity.level(),
                        entity.getX(), entity.getY(), entity.getZ());
                if (t != null) mob.setTarget(t);
            }
        } catch (Exception e) {
            // 静默：TF 未加载时不会到这里
        }
    }

    /** 幻影骑士：清零内部冷却 + 解除盾牌 */
    private static void resetKnightPhantomCooldowns(Entity entity) {
        try {
            Class<?> clazz = entity.getClass();
            for (String fn : new String[]{"attackCooldown", "attackTimer", "shieldCooldown", "nextAbility"}) {
                try {
                    java.lang.reflect.Field f = clazz.getDeclaredField(fn);
                    f.setAccessible(true); f.setInt(entity, 0);
                } catch (NoSuchFieldException ignored) {}
            }
            try {
                java.lang.reflect.Field f = clazz.getDeclaredField("shieldBlocks");
                f.setAccessible(true); f.setBoolean(entity, false);
            } catch (NoSuchFieldException ignored) {}
        } catch (Exception e) {
            LenSouls.LOGGER.error("[幻灵] 幻影骑士冷却清零失败", e);
        }
    }

    // ========== 下界合金巨兽冷却清零 ==========

    /** 反射缓存：Netherite_Monstrosity_Entity 的 4 个冷却字段 */
    private static java.lang.reflect.Field nmShootCooldown;
    private static java.lang.reflect.Field nmFlareCooldown;
    private static java.lang.reflect.Field nmOverpowerCooldown;
    private static java.lang.reflect.Field nmCheckCooldown;
    private static boolean nmFieldsResolved = false;

    private static void resolveNMFields() {
        if (nmFieldsResolved) return;
        try {
            Class<?> clazz = Class.forName("com.github.L_Ender.cataclysm.entity.InternalAnimationMonster" +
                    ".IABossMonsters.NewNetherite_Monstrosity.Netherite_Monstrosity_Entity");
            nmShootCooldown = findField(clazz, "shoot_cooldown");
            nmFlareCooldown = findField(clazz, "flare_shoot_cooldown");
            nmOverpowerCooldown = findField(clazz, "overpower_cooldown");
            nmCheckCooldown = findField(clazz, "check_cooldown");
            nmFieldsResolved = true;
        } catch (Exception e) {
            LenSouls.LOGGER.error("[幻灵] 无法加载 Netherite_Monstrosity 冷却字段", e);
        }
    }

    /** 查找类及其父类的字段 */
    private static java.lang.reflect.Field findField(Class<?> clazz, String name) {
        for (var c = clazz; c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    /** 每 tick 将下界合金巨兽的 4 个冷却字段清零 */
    private static void resetNetheriteCooldowns(Entity entity) {
        resolveNMFields();
        if (!nmFieldsResolved) return;
        try {
            if (nmShootCooldown != null) nmShootCooldown.setInt(entity, 0);
            if (nmFlareCooldown != null) nmFlareCooldown.setInt(entity, 0);
            if (nmOverpowerCooldown != null) nmOverpowerCooldown.setInt(entity, 0);
            if (nmCheckCooldown != null) nmCheckCooldown.setInt(entity, 0);
        } catch (Exception e) {
            LenSouls.LOGGER.error("[幻灵] 重置 Netherite 冷却失败", e);
        }
    }

    // ========== Tick ==========

    public void tick() {
        if (activePhantoms.isEmpty()) return;

        Iterator<Map.Entry<UUID, BossPhantomData>> it = activePhantoms.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BossPhantomData> entry = it.next();
            BossPhantomData d = entry.getValue();
            ServerPlayer p = findPlayer(d.playerId());
            if (p == null) {
                it.remove();
                continue;
            }

            boolean isBorrowed = d.type().isModLoaded();
            int remaining = d.remainingTicks();

            if (isBorrowed) {
                // ===== 借体模式：幻灵自由移动，玩家在旁观者模式自由视角 =====
                Entity ie = p.level().getEntity(d.phantomEntityId());
                if (ie instanceof Mob mob) {
                    if (mob.getTarget() == null || !mob.getTarget().isAlive()) {
                        // 丢失 target 时重新指派（解决下界合金巨兽等实体 AI 清除目标的问题）
                        LivingEntity t = findNearestEnemy(p.serverLevel(), mob.getX(), mob.getY(), mob.getZ());
                        if (t != null) mob.setTarget(t);
                    }
                    if (mob.getTarget() != null) {
                        // 强制面向目标
                        double dx = mob.getTarget().getX() - mob.getX();
                        double dz = mob.getTarget().getZ() - mob.getZ();
                        float ty = (float)(Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
                        mob.yBodyRot = ty;
                        mob.yHeadRot = ty;
                        mob.setYRot(ty);
                    }
                    // 下界合金巨兽：每 tick 清零内部冷却，防止冷却期占用演出时间
                    if (d.type() == BossPhantomType.NETHERITE_MONSTROSITY) {
                        resetNetheriteCooldowns(ie);
                    }
                    // 利维坦：强制水域导航 + 冷却清零
                    if (d.type() == BossPhantomType.THE_LEVIATHAN) {
                        forceLeviathanWaterMode(ie);
                        resetLeviathanCooldowns(ie);
                    }
                    // 幻影骑士：维持攻击态 + 冷却清零
                    if (d.type() == BossPhantomType.KNIGHT_PHANTOM) {
                        forceKnightPhantomAttackMode(ie);
                        resetKnightPhantomCooldowns(ie);
                    }
                } else if (ie == null) {
                    LenSouls.LOGGER.warn("[幻灵] 实体丢失 id={}，主动结束幻灵", d.phantomEntityId());
                    // 跨所有维度查找（可能在另一个维度或区块已卸载时找不到）
                    Entity phantom = findEntityAcrossDimensions(d.phantomEntityId());
                    if (phantom != null) {
                        phantom.discard();
                    }
                    it.remove();
                    endPhantom(p, d, true);
                    continue;
                }
            } else {
                // ===== 旧版 BossPhantomEntity 模式：传送观察位 + 阶段包 =====
                p.teleportTo(d.watchX(), d.watchY(), d.watchZ());
                int skillTick = d.type().getSkillTick();

                if (remaining == skillTick + 10) {
                    setPhantomPhase(p.level(), d.phantomEntityId(), BossPhantomEntity.PHASE_CHARGE);
                    PacketDistributor.sendToPlayer(p, new PhantomTickPacket(d.type(), 0));
                }
                if (d.isSkillTick()) {
                    setPhantomPhase(p.level(), d.phantomEntityId(), BossPhantomEntity.PHASE_EXECUTE);
                    PacketDistributor.sendToPlayer(p, new PhantomSkillPacket(d.type()));
                    dealSkillDamage(d, p.level());
                }
                if (remaining == skillTick - 8) {
                    setPhantomPhase(p.level(), d.phantomEntityId(), BossPhantomEntity.PHASE_DECAY);
                    PacketDistributor.sendToPlayer(p, new PhantomTickPacket(d.type(), 2));
                }
                if (d.type() == BossPhantomType.ENDER_GUARDIAN) {
                    applyEnderGuardianTickPull(d, p.level());
                }
            }

            if (d.isExpired()) {
                it.remove();
                endPhantom(p, d, true);
            } else {
                entry.setValue(d.tick());
            }
        }

    }

    // ========== 幻灵技能伤害 ==========

    /**
     * 技能触发时对幻灵周围非玩家实体造成伤害。
     * 所有 BOSS：径向 AOE（固定 50 + 技能伤害）。
     * 云筑魔像额外：定向光束远程伤害（10° 窄锥，20 格射程）。
     */
    private static void dealSkillDamage(BossPhantomData d, Level level) {
        BossPhantomType type = d.type();
        Entity phantom = level.getEntity(d.phantomEntityId());
        if (phantom == null) {
            LenSouls.LOGGER.warn("[幻灵] 技能伤害：幻灵实体不存在，使用召唤位");
        }
        double cx = phantom != null ? phantom.getX() : d.originX();
        double cy = phantom != null ? phantom.getY() : d.originY() + 1.5;
        double cz = phantom != null ? phantom.getZ() : d.originZ();
        double range = type == BossPhantomType.OBLITERATOR ? 30.0 : 10.0;

        // 穿甲伤害源（magic 类型无视护甲）
        DamageSource piercing = level.damageSources().magic();

        AABB aabb = new AABB(cx - range, cy - range, cz - range,
                cx + range, cy + range, cz + range);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, aabb)) {
            if (target instanceof Player) continue;
            if (!target.isAlive()) continue;
            double dist = target.distanceToSqr(cx, cy, cz);
            if (dist > range * range) continue;

            float total = 50.0f + type.getSkillDamage();
            target.hurt(piercing, total);
        }

        // 云筑魔像额外：定向光束远程伤害（启动朝向，10° 窄锥，20 格射程）
        if (type == BossPhantomType.CLOUD_GOLEM) {
            applyCloudGolemBeam(d, cx, cy, cz, level, piercing);
        }
        // 末影守卫的单次范围伤害不再附带拉取——每 tick 持续吸引由 applyEnderGuardianTickPull 处理
    }

    /** 云筑魔像：定向光束（启动朝向，10° 窄锥，20 格射程） */
    private static void applyCloudGolemBeam(BossPhantomData d, double cx, double cy, double cz,
                                             Level level, DamageSource piercing) {
        double yawRad = Math.toRadians(d.originYRot());
        double beamRange = 20.0;
        double beamHalfAngle = Math.toRadians(10.0);

        Vec3 dir = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        AABB beamBox = new AABB(cx - beamRange, cy - 3, cz - beamRange,
                cx + beamRange, cy + 3, cz + beamRange);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, beamBox)) {
            if (target instanceof Player || !target.isAlive()) continue;
            double distSq = target.distanceToSqr(cx, cy, cz);
            if (distSq > beamRange * beamRange) continue;

            Vec3 toTarget = new Vec3(target.getX() - cx, 0, target.getZ() - cz).normalize();
            if (dir.dot(toTarget) < Math.cos(beamHalfAngle)) continue;

            target.hurt(piercing, 30.0f);
        }
    }

    /**
     * 末影守卫虚影：每 tick 将 32 格内非玩家实体持续拉向虚影中心。
     * 使用引力枪同款的 tick 式 setDeltaMovement 替换机制而非一次性脉冲。
     */
    private static void applyEnderGuardianTickPull(BossPhantomData d, Level level) {
        Entity phantom = level.getEntity(d.phantomEntityId());
        double cx = phantom != null ? phantom.getX() : d.originX();
        double cy = phantom != null ? phantom.getY() : d.originY() + 1.5;
        double cz = phantom != null ? phantom.getZ() : d.originZ();
        double pullRange = 32.0;
        double pullForce = 0.8;
        double stopRadius = 2.0; // 到达中心 2 格内自动停止吸引

        AABB pullBox = new AABB(cx - pullRange, cy - pullRange, cz - pullRange,
                cx + pullRange, cy + pullRange, cz + pullRange);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, pullBox)) {
            if (target instanceof Player || !target.isAlive()) continue;
            double distSq = target.distanceToSqr(cx, cy, cz);
            if (distSq > pullRange * pullRange) continue;
            // 已到达中心附近 → 停止吸引
            if (distSq <= stopRadius * stopRadius) continue;
            // 替换式 velocity（vs 累加式），引力枪同款，产生持续稳定的吸引
            Vec3 pull = new Vec3(cx - target.getX(), cy - target.getY(), cz - target.getZ()).normalize().scale(pullForce);
            target.setDeltaMovement(pull);
            target.hurtMarked = true;
        }
    }

    // ========== 结束 ==========

    void endPhantom(ServerPlayer p, BossPhantomData d, boolean apply) {
        // 恢复游戏模式（旁观者 → 原模式）
        net.minecraft.world.level.GameType original = originalGameTypes.remove(p.getUUID());
        if (original != null) p.setGameMode(original);
        // 清理持久化 NBT 标记
        p.getPersistentData().remove("lensouls:originalGameType");

        p.setNoGravity(false);
        p.teleportTo(d.originX(), d.originY(), d.originZ());
        p.setYRot(d.originYRot());
        p.setXRot(d.originXRot());
        p.setCamera(p);
        removeStunEffects(p);

        Entity phantom = p.level().getEntity(d.phantomEntityId());
        if (phantom == null) {
            phantom = findEntityAcrossDimensions(d.phantomEntityId());
        }
        if (phantom != null) {
            phantom.discard();
        } else {
            // 不在任何已加载维度 → 在未加载区块中 → EntityJoinLevelEvent 兜底
            LenSouls.LOGGER.warn("[幻灵] endPhantom 找不到实体 id={}，留待区块加载时拦截",
                    d.phantomEntityId());
        }

        PacketDistributor.sendToPlayer(p, new PhantomStopPacket(d.playerId()));

        if (apply) {
            BossPhantomType t = d.type();
            // startPhantom 已立即应用效果，此处仅确保生效（使用物品实际等级）
            if (!p.hasEffect(t.getEffectHolder())) {
                p.addEffect(new MobEffectInstance(t.getEffectHolder(),
                        Config.DEFAULT_DURATION.get() * 20, d.amplifier(), false, false, false));
                ElementInfusionEffect.setPlayerData(p, t.getElement(), t.shouldApplySlowness(), d.descId());
                p.sendSystemMessage(Component.translatable("message.lensouls.soul_activated",
                        Component.translatable(d.descId())));
            }
        }

        // 退场：崩解消散粒子
        BossPhantomType t = d.type();
        spawnExitParticles(p.serverLevel(), t, d.originX(), d.originY() + 1.5, d.originZ());

    }

    // ========== 效果工具 ==========

    private static void applyStunEffects(ServerPlayer p, int ticks) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 255, false, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 255, false, false, false));
    }

    private static void removeStunEffects(ServerPlayer p) {
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
    }

    private static ServerPlayer findPlayer(UUID pid) {
        var s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) return null;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(pid)) return p;
        }
        return null;
    }

    // ========== 入场/退场粒子 ==========

    /** 幻灵生成时：传送门浮现粒子 */
    private static void spawnEntryPortal(ServerLevel level, BossPhantomType type,
                                          double x, double y, double z) {
        boolean cataclysm = ModList.get().isLoaded("cataclysm");
        boolean isCataclysmBoss = type == BossPhantomType.IGNIS
                || type == BossPhantomType.ENDER_GUARDIAN
                || type == BossPhantomType.NETHERITE_MONSTROSITY;

        if (isCataclysmBoss && cataclysm) {
            // 灾变系：SOUL_LAVA 粒子构成椭圆传送门 + SPARK 环
            try {
                var soulLava = com.github.L_Ender.cataclysm.init.ModParticle.SOUL_LAVA.get();
                var spark = com.github.L_Ender.cataclysm.init.ModParticle.SPARK.get();
                for (int ring = 0; ring < 3; ring++) {
                    float radius = 1.2f + ring * 0.3f;
                    for (int i = 0; i < 16; i++) {
                        float a = 2f * (float) Math.PI * i / 16;
                        double px = x + Math.cos(a) * radius;
                        double pz = z + Math.sin(a) * radius;
                        double py = y + Math.sin(a * 2) * radius * 1.5f;
                        level.sendParticles(soulLava, px, py, pz, 1, 0, 0.05, 0, 0);
                        if (i % 4 == 0) {
                            level.sendParticles(spark, px, py, pz, 1, 0, 0.02, 0, 0);
                        }
                    }
                }
            } catch (Throwable ignored) {
                fallbackEntryPortal(level, x, y, z);
            }
        } else {
            fallbackEntryPortal(level, x, y, z);
        }
    }

    /** 入场降级：原版 PORTAL 粒子 */
    private static void fallbackEntryPortal(ServerLevel level, double x, double y, double z) {
        for (int i = 0; i < 30; i++) {
            double dx = (level.random.nextDouble() - 0.5) * 3;
            double dz = (level.random.nextDouble() - 0.5) * 3;
            level.sendParticles(ParticleTypes.PORTAL,
                    x + dx, y + level.random.nextDouble() * 2, z + dz,
                    1, -dx * 0.1, 0, -dz * 0.1, 0.1);
        }
    }

    /** 幻灵结束时：崩解消散粒子 */
    private static void spawnExitParticles(ServerLevel level, BossPhantomType type,
                                            double x, double y, double z) {
        boolean cataclysm = ModList.get().isLoaded("cataclysm");
        boolean isCataclysmBoss = type == BossPhantomType.IGNIS
                || type == BossPhantomType.ENDER_GUARDIAN
                || type == BossPhantomType.NETHERITE_MONSTROSITY;

        if (isCataclysmBoss && cataclysm) {
            // 灾变系：SOUL_LAVA 爆发 + SPARK 环 + 烟
            try {
                var soulLava = com.github.L_Ender.cataclysm.init.ModParticle.SOUL_LAVA.get();
                var spark = com.github.L_Ender.cataclysm.init.ModParticle.SPARK.get();
                for (int i = 0; i < 15; i++) {
                    double dx = (level.random.nextDouble() - 0.5) * 3;
                    double dz = (level.random.nextDouble() - 0.5) * 3;
                    level.sendParticles(soulLava,
                            x + dx, y + level.random.nextDouble() * 2, z + dz,
                            2, dx * 0.15, 0.1, dz * 0.15, 0.03);
                }
                for (int ring = 0; ring < 2; ring++) {
                    float radius = 1.0f + ring * 0.5f;
                    for (int i = 0; i < 12; i++) {
                        float a = 2f * (float) Math.PI * i / 12;
                        level.sendParticles(spark,
                                x + Math.cos(a) * radius, y + 0.3, z + Math.sin(a) * radius,
                                1, Math.cos(a) * 0.2, 0.05, Math.sin(a) * 0.2, 0.02);
                    }
                }
            } catch (Exception ignored) {
                fallbackExitParticles(level, x, y, z);
            }
        } else {
            fallbackExitParticles(level, x, y, z);
        }
    }

    /** 退场降级：原版 POOF + FLAME */
    private static void fallbackExitParticles(ServerLevel level, double x, double y, double z) {
        for (int i = 0; i < 12; i++) {
            double dx = (level.random.nextDouble() - 0.5) * 2;
            double dz = (level.random.nextDouble() - 0.5) * 2;
            level.sendParticles(ParticleTypes.POOF,
                    x + dx, y + level.random.nextDouble() * 1.5, z + dz,
                    2, dx * 0.2, 0.1, dz * 0.2, 0.05);
        }
        for (int i = 0; i < 10; i++) {
            double dx = (level.random.nextDouble() - 0.5) * 2.5;
            double dz = (level.random.nextDouble() - 0.5) * 2.5;
            level.sendParticles(ParticleTypes.FLAME,
                    x + dx, y + level.random.nextDouble(), z + dz,
                    1, dx * 0.1, 0.05, dz * 0.1, 0.02);
        }
    }

    /** 设置幻灵实体阶段（通过实体 ID 查找） */
    private static void setPhantomPhase(Level level, int phantomEntityId, int phase) {
        Entity e = level.getEntity(phantomEntityId);
        if (e instanceof BossPhantomEntity phantom) {
            phantom.setPhantomPhase(phase);
        }
    }

    /** 通过反射获取 Ignis / Ender Guardian 的技能 Animation 对象（Object 承载，避免编译期依赖 lionfishapi） */
    @javax.annotation.Nullable
    private static Object getLionfishAnimation(BossPhantomType type) {
        if (type == BossPhantomType.IGNIS) {
            return getAnimField("com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity", "SPIN_ATTACK");
        }
        if (type == BossPhantomType.ENDER_GUARDIAN) {
            return getAnimField("com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ender_Guardian_Entity", "GUARDIAN_BLACKHOLE");
        }
        return null;
    }

    @javax.annotation.Nullable
    private static Object getAnimField(String className, String fieldName) {
        try {
            Class<?> clazz = Class.forName(className);
            java.lang.reflect.Field f = clazz.getDeclaredField(fieldName);
            return f.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 断线重连修复 ==========

    /**
     * 玩家在幻灵表演期间死亡时，立即结束幻灵并恢复游戏模式，
     * 防止玩家卡在旁观者模式。
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BossPhantomManager mgr = getInstance();
            BossPhantomData d = mgr.activePhantoms.get(player.getUUID());
            if (d != null) {
                mgr.endPhantom(player, d, false);
                mgr.activePhantoms.remove(player.getUUID());
                mgr.originalGameTypes.remove(player.getUUID());
            }
        }
    }

    /**
     * 玩家断线时清理活跃幻灵，防止残留实体。
     * <p>
     * 在玩家退出前将原始游戏模式持久化到 NBT，
     * 供 {@link #onPlayerLogin(PlayerEvent.PlayerLoggedInEvent)} 重连后恢复。
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BossPhantomManager mgr = getInstance();
            BossPhantomData d = mgr.activePhantoms.get(player.getUUID());
            if (d != null) {
                // 将原始游戏模式持久化到 NBT（对抗断线丢失）
                net.minecraft.world.level.GameType original = mgr.originalGameTypes.get(player.getUUID());
                if (original != null) {
                    player.getPersistentData().putInt("lensouls:originalGameType", original.getId());
                }
                Entity e = player.level().getEntity(d.phantomEntityId());
                if (e == null) {
                    e = findEntityAcrossDimensions(d.phantomEntityId());
                }
                if (e != null) {
                    e.discard();
                } else {
                    LenSouls.LOGGER.warn("[幻灵] 断线清理找不到实体 id={}，留待区块加载时拦截",
                            d.phantomEntityId());
                }
                mgr.activePhantoms.remove(player.getUUID());
                mgr.originalGameTypes.remove(player.getUUID());
            }
        }
    }

    /**
     * 玩家登录时清理残留的无重力状态。
     * 防止玩家在幻灵序列中断线后重进时仍然处于无重力状态。
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 清除残留的幻灵数据
            getInstance().clearPlayer(player.getUUID());
            // 清除上一会话残留的元素附魔数据（自定义名称、减速标记）
            ElementInfusionEffect.cleanupPlayer(player);
            // 清除残留的定身效果（幻灵期间 Slowness 255 + Resistance 255）
            removeStunEffects(player);
            // 如果玩家还残留无重力（断线时未触发 endPhantom），强制关闭
            if (player.isNoGravity()) {
                player.setNoGravity(false);
            }
            // 从 NBT 恢复原始游戏模式（断线时未触发 endPhantom）
            if (player.getPersistentData().contains("lensouls:originalGameType")) {
                int gmId = player.getPersistentData().getInt("lensouls:originalGameType");
                net.minecraft.world.level.GameType original = net.minecraft.world.level.GameType.byId(gmId);
                if (original != null && player.gameMode.getGameModeForPlayer() != original) {
                    player.setGameMode(original);
                }
                player.getPersistentData().remove("lensouls:originalGameType");
            }
        }
    }

    // ========== 区块加载时拦截残留虚影 ==========

    /**
     * 从区块文件加载实体时，检查是否带有 lensouls:phantom 标记。
     * 不属于活跃幻灵的残留实体直接阻止加入世界（setCanceled）。
     * 覆盖场景：崩溃重进、跑远后区块重载、大退残留等。
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!event.loadedFromDisk()) return;  // 只拦截区块文件加载（忽略 addFreshEntity）

        Entity entity = event.getEntity();
        // 双重检测：persistentData 标记 + customName translation key（对抗实体类不保存 persistentData）
        boolean isPhantom = entity.getPersistentData().getBoolean("lensouls:phantom");
        if (!isPhantom) {
            Component name = entity.getCustomName();
            if (name != null && name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                isPhantom = tc.getKey().startsWith("entity.lensouls.boss_phantom");
            }
        }
        if (!isPhantom) return;

        boolean belongsToActive = getInstance().activePhantoms.values().stream()
                .anyMatch(d -> d.phantomEntityId() == entity.getId());

        if (!belongsToActive) {
            event.setCanceled(true);
        }
    }
}
