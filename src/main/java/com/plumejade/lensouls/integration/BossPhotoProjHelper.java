package com.plumejade.lensouls.integration;

import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boss 照片弹幕触发框架。
 * <p>
 * 触发信号由 mixin 提供（{@code Player#attack} / BetterCombat {@code ServerNetwork#handleAttackRequest}），
 * 每次完整挥砍开始调用 {@link #onSwing}。空手狂按 / 空挥均触发（BetterCombat 环境）。
 * <p>
 * 远程伤害命中同样触发（{@link #onRangedHit}）：玩家用弓箭 / 弩 / 三叉戟 / 次元枪子弹等
 * 远程手段打中生物时，与挥击共用同一套判定与去重，不再局限于"必须先挥一下"。
 * 发射：玩家位置 + 玩家视线方向。所有弹射物以玩家为 shooter/caster（伤害归属玩家，不伤自身/队友）。
 */
public class BossPhotoProjHelper {

    /**
     * 挥击去重（内存态，不持久化——persistentData 跨会话污染会导致永不触发）。
     * 时钟用世界游戏时间而非实体 tickCount：玩家死亡重生后是新实体，tickCount 会归零，
     * 若存旧实体的 tickCount 会算出负数差值导致弹幕永久卡死。
     */
    private static final java.util.Map<java.util.UUID, Long> LAST_SWING =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** boss 照片 id → 触发概率 */
    private static final Map<String, Float> TRIGGER = new HashMap<>();
    static {
        TRIGGER.put("cataclysm:ender_guardian", 0.15f);
        TRIGGER.put("cataclysm:ignis", 0.15f);
        TRIGGER.put("cataclysm:netherite_monstrosity", 0.12f);
        TRIGGER.put("cataclysm:the_harbinger", 0.12f);
        TRIGGER.put("cataclysm:the_leviathan", 0.10f);
        TRIGGER.put("cataclysm:ancient_remnant", 0.12f);
        TRIGGER.put("cataclysm:maledictus", 0.15f);
        TRIGGER.put("cataclysm:scylla", 0.15f);
        TRIGGER.put("legendary_monsters:posessed_paladin", 0.15f);
        TRIGGER.put("legendary_monsters:cloud_golem", 0.10f);
        TRIGGER.put("legendary_monsters:the_obliterator", 0.15f);
        TRIGGER.put("minecraft:evoker", 0.15f);
        TRIGGER.put("minecraft:skeleton", 0.15f);
        TRIGGER.put("archaion:last_of_deepslate", 0.12f);
        TRIGGER.put("fdbosses:chesed", 0.12f);
        TRIGGER.put("fdbosses:malkuth", 0.12f);
        TRIGGER.put("fdbosses:geburah", 0.12f);
    }

    /**
     * 弹幕判定重入保护：触发过程中（弹幕同刻命中、连锁引爆等）再次请求触发一律忽略。
     * 这是阻断「触发 → 弹幕 → 命中 → 再触发」自增殖的最后一道保险，与
     * {@link com.plumejade.lensouls.util.PhotoProjMarker} 的标记判定互补。
     */
    private static final ThreadLocal<Boolean> IN_TRIGGER = ThreadLocal.withInitial(() -> false);

    /**
     * 高频触发保护：每个玩家在 {@link #ROLL_WINDOW_TICKS} 内最多掷
     * {@link #ROLL_BUDGET} 次弹幕触发。
     * <p>
     * 正常情况下限流打不到：挥击本身有 3tick 去重，10 张 boss 照片狂点也就 ~66 次/秒。
     * 这个额度（24 次/10tick = 48 次/秒…按窗口节流）是给异常增殖留的熔断：
     * 一旦某个非标记弹幕（第三方模组的激光/法阵）混进来形成回环，这里会先熔断，
     * 而不是让实体数量指数爆炸后再由韧性检查/网络同步去撞空指针。
     */
    private static final int ROLL_WINDOW_TICKS = 10;
    private static final int ROLL_BUDGET = 24;
    /** UUID → [窗口起点 gameTime, 本窗口已用额度]（仅服务端主线程访问） */
    private static final Map<java.util.UUID, long[]> ROLL_STATE = new java.util.concurrent.ConcurrentHashMap<>();
    /** UUID → 上次「触发超限」告警的 gameTime（30 秒一次，避免刷屏） */
    private static final Map<java.util.UUID, Long> LAST_CAP_WARN = new java.util.concurrent.ConcurrentHashMap<>();

    /** 清理指定玩家的挥击去重记录（切档/登出时调用，避免跨会话 tick 残留导致 boss 弹幕被卡死） */
    public static void clearSwing(java.util.UUID uuid) {
        LAST_SWING.remove(uuid);
        ROLL_STATE.remove(uuid);
        LAST_CAP_WARN.remove(uuid);
    }

    /** 登出即清干净：三张表都按玩家 UUID 记账，不清就是慢性内存增长 */
    @SubscribeEvent
    public static void onLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        clearSwing(event.getEntity().getUUID());
    }

    /** 每次完整挥砍开始调用（由 Player#attack / BetterCombat handleAttackRequest mixin 触发）。
     *  {@code hitTarget} 为本次被攻击的实体（可为 null：空挥/BetterCombat 路径无目标时退化为最近敌人）。 */
    public static void onSwing(ServerPlayer player, Entity hitTarget) {
        trigger(player, hitTarget);
    }

    /**
     * 远程伤害命中触发（{@link LivingDamageEvent.Pre}，仅服务端）。
     * <p>
     * 玩家以远程手段命中生物时与挥击同等对待，逐次命中各掷一次骰：
     * <ul>
     *   <li>「远程」口径复用 {@link com.plumejade.lensouls.damage.RangedAttackHelper#isRanged}，
     *       与照片的远程伤害加成 / 元素弹射物弱点判定保持一致（弓箭 / 弩 / 三叉戟 /
     *       次元枪子弹 / 投掷物等，不含近战与直接命中）。</li>
     *   <li>本模组自身弹幕（{@code lensouls:photo_proj}）命中的连锁不再触发，避免弹幕自我增殖。</li>
     *   <li>自伤（自己打到自己）不算命中；命中判定已被 {@code HitChanceHandler} 取消的伤害
     *       不会走到本事件，因此"打空"不会触发。</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onRangedHit(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() == player) return; // 自伤不算命中
        // 照片弹幕本身即为远程伤害：其命中不再回头触发弹幕（全链路标记判定，见 PhotoProjMarker）
        if (com.plumejade.lensouls.util.PhotoProjMarker.isBarrageDamage(event.getSource())) return;
        if (!com.plumejade.lensouls.damage.RangedAttackHelper.isRanged(event.getSource())) return;
        trigger(player, event.getEntity());
    }

    /** 弹幕判定核心：挥击与远程命中共用同一套 3 tick 去重 + 各 Boss 触发概率。 */
    private static void trigger(ServerPlayer player, Entity hitTarget) {
        // 重入保护：本次触发过程中（同刻命中/引爆）产生的伤害不再触发新一轮弹幕
        if (Boolean.TRUE.equals(IN_TRIGGER.get())) return;

        long now = player.level().getGameTime();
        // 去重：BetterCombat 命中时会同时走原版 attack 与 handleAttackRequest，3 tick 内只触发一次
        Long last = LAST_SWING.get(player.getUUID());
        if (last != null && now - last < 3) return;
        // 高频触发保护（熔断）：异常增殖时先掐掉触发，避免实体/网络/韧性检查被灌爆
        if (!consumeRollBudget(player, now)) return;
        LAST_SWING.put(player.getUUID(), now);

        IN_TRIGGER.set(true);
        try {
            triggerInner(player, hitTarget);
        } finally {
            IN_TRIGGER.set(false);
        }
    }

    /** 高频触发额度：窗口内超限即拒绝，并最多每 30 秒告警一次。 */
    private static boolean consumeRollBudget(ServerPlayer player, long now) {
        long[] state = ROLL_STATE.computeIfAbsent(player.getUUID(), k -> new long[] {now, 0L});
        if (now - state[0] >= ROLL_WINDOW_TICKS) {
            state[0] = now;
            state[1] = 0L;
        }
        if (state[1] >= ROLL_BUDGET) {
            Long warned = LAST_CAP_WARN.get(player.getUUID());
            if (warned == null || now - warned > 600L) {
                LAST_CAP_WARN.put(player.getUUID(), now);
                com.plumejade.lensouls.LenSouls.LOGGER.warn(
                        "[PhotoBoss] 弹幕触发频率超限，本窗口内已忽略后续触发（玩家 {}，{} 次/{} tick）",
                        player.getName().getString(), ROLL_BUDGET, ROLL_WINDOW_TICKS);
            }
            return false;
        }
        state[1]++;
        return true;
    }

    /** 真正的触发逻辑（已在外层完成重入/去重/限流）。 */
    private static void triggerInner(ServerPlayer player, Entity hitTarget) {
        LivingEntity target = hitTarget instanceof LivingEntity le ? le : null;

        // 套装弹幕钩子：从玩家 persistent 读取 barrage_trigger / barrage_dmg
        CompoundTag setFlags = player.getPersistentData().getCompound("lensouls:set_flags");
        int barrageExtra = setFlags.getInt("barrage_trigger");
        float barrageDmg = setFlags.getFloat("barrage_dmg");

        List<String> gear = PhotoSpecialEffects.collectGearEntities(player);
        // 法师胸针 + 手持法杖（数据驱动 staff_item）→ 弹幕必定触发
        boolean broochForce = com.plumejade.lensouls.handler.BroochEffectHandler.hasBrooch(player)
                && (com.plumejade.lensouls.config.StaffItemLoader.isStaff(player.getMainHandItem())
                || com.plumejade.lensouls.config.StaffItemLoader.isStaff(player.getOffhandItem()));
        for (String id : gear) {
            Float chance = TRIGGER.get(id);
            if (chance != null && (broochForce || player.getRandom().nextFloat() < chance)) {
                fireBossSkill(player, id, target);
                for (int k = 0; k < barrageExtra; k++) {
                    player.getPersistentData().putFloat("lensouls:barrage_dmg_mult", barrageDmg);
                    fireBossSkill(player, id, target);
                }
                if (barrageExtra > 0) player.getPersistentData().remove("lensouls:barrage_dmg_mult");
            }
        }
    }

    // ========== 各 Boss 弹幕 ==========

    private static void fireBossSkill(ServerPlayer player, String bossId, LivingEntity hit) {
        try {
            switch (bossId) {
                case "cataclysm:ender_guardian" -> spawnVoidRune(player);
                case "cataclysm:ignis" -> spawnIgnisFireballs(player);
                case "cataclysm:netherite_monstrosity" -> spawnFallingBlocks(player);
                case "cataclysm:the_harbinger" -> spawnDeathLaser(player);
                case "cataclysm:the_leviathan" -> spawnAbyssBlast(player);
                case "cataclysm:ancient_remnant" -> spawnDesertStele(player);
                case "cataclysm:maledictus" -> spawnPhantomArrows(player);
                case "cataclysm:scylla" -> spawnWaves(player);
                case "legendary_monsters:posessed_paladin" -> spawnSoulPillars(player);
                case "legendary_monsters:cloud_golem" -> spawnEnergyBeam(player);
                case "legendary_monsters:the_obliterator" -> spawnAnnihilationLaser(player);
                case "minecraft:evoker" -> spawnEvokerFangs(player);
                case "minecraft:skeleton" -> spawnSkeletonArrows(player);
                case "archaion:last_of_deepslate" -> spawnEchoStar(player, hit);
                case "fdbosses:chesed" -> spawnChesedField(player, hit);
                case "fdbosses:malkuth" -> spawnMalkuthSword(player, hit);
                case "fdbosses:geburah" -> spawnGeburahRay(player, hit);
            }
        } catch (Exception e) {
            com.plumejade.lensouls.LenSouls.LOGGER.warn("[PhotoBoss] 弹幕触发失败: " + bossId, e);
        }
    }

    /** 末影守卫：虚空符文（1.5s 后引爆，6 点魔法伤害）——召唤在最近的非玩家生物脚下 */
    private static void spawnVoidRune(ServerPlayer player) {
        Level level = player.level();
        float yawRad = (float) (player.getYRot() * Math.PI / 180.0);
        double px, pz, py;
        LivingEntity target = findNearestNonPlayer(player, 16.0);
        if (target != null) {
            px = target.getX();
            pz = target.getZ();
            py = findGroundY(level, px, target.getY(), pz);
        } else {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0F);
            px = eye.x + look.x * 4.0;
            pz = eye.z + look.z * 4.0;
            py = findGroundY(level, px, eye.y, pz);
        }
        if (py < level.getMinBuildHeight() + 1) return;
        float dmg = playerFinalDamage(player);
        var rune = new com.github.L_Ender.cataclysm.entity.projectile.Void_Rune_Entity(
                level, px, py, pz, yawRad, 30, dmg, player);
        markAndSpawn(rune);
    }

    /** 玩家周围指定范围内最近的非玩家 LivingEntity（排除玩家自身） */
    private static LivingEntity findNearestNonPlayer(ServerPlayer player, double radius) {
        LivingEntity best = null;
        double bestDist = radius * radius;
        for (net.minecraft.world.entity.Entity e : player.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                en -> !(en instanceof net.minecraft.world.entity.player.Player) && en.isAlive())) {
            double d = player.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    /** 焰魔：烈焰轰击 ×3（照片版 6 点 + 目标最大生命 2%，命中施加炽焰烙印；由 IgnisFireballPercentMixin 修正） */
    private static void spawnIgnisFireballs(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getViewVector(1.0F);
        for (int i = 0; i < 3; i++) {
            var fb = new com.github.L_Ender.cataclysm.entity.projectile.Ignis_Fireball_Entity(level, player);
            fb.setPos(player.getX() + look.x * 0.5, player.getEyeY(), player.getZ() + look.z * 0.5);
            // 扇形微偏
            double spread = (i - 1) * 0.12;
            Vec3 dir = look.yRot((float) spread);
            fb.shoot(dir.x, dir.y, dir.z, 0.25f, 3.0f);
            try { fb.setUp(0); } catch (Exception ignored) {}
            markPercentAndSpawn(fb);
        }
    }

    /** 地震践踏半径（本体 {@code EarthQuake(6.25)}，照片版收窄一层） */
    private static final double QUAKE_RADIUS = 4.0D;
    /** 本体 {@code CMCommonConfig.NetheriteMonstrosity.SmashHpdamage = 0.08}：额外附带目标最大生命 8% */
    private static final float QUAKE_HP_PART = 0.08F;
    /** 践踏掀起的装饰落石数量 */
    private static final int QUAKE_DEBRIS = 5;

    /**
     * 下界合金巨兽：<b>地震践踏</b>（照抄本体 {@code Netherite_Monstrosity_Entity.EarthQuake}）
     * + 掀地落石（纯视觉）。
     * <p>
     * 为什么是这个形态（对着 3.32 jar 反编译核实过）：
     * <ul>
     *   <li>{@code Cm_Falling_Block_Entity} 是<b>零伤害的装饰实体</b>（全类只有 {@code tick/move}，
     *       没有 hurt/explode/setOwner/setDamage）；本体从来是「装饰物 + 另一段独立 AABB 伤害」两件事。
     *       旧照片版只搬了装饰物 ⇒ 落石砸下来<b>一点血都不掉</b>，这就是「鸡肋」的字面根因。</li>
     *   <li>本体真正的招牌是 {@code EarthQuake(6.25)}：{@code getBoundingBox().inflate(6.25)} 内全部结算
     *       {@code ATTACK_DAMAGE + min(ATTACK_DAMAGE, 目标最大生命 × 0.08)}，命中后上抛
     *       （{@code launch(entity, 2.0, 0.6)}）、破盾 120t、狂暴时点燃 6s。照片版照这个口径做成
     *       <b>以敌人为中心的地震</b>，只是把半径收到 4 格、去掉点燃。</li>
     *   <li>落点从「玩家视线前 3 格」改成<b>最近敌人脚下</b>（找不到才退回视线前方）——
     *       原来的固定落点几乎总是砸在空地上。</li>
     * </ul>
     * 伤害走 {@code playerAttack} 归属玩家（吃摄魂/元素/套装加成，不算远程、不会回头触发弹幕）；
     * 落石只做视觉，<b>不再单独结算</b>，避免同一个目标被「践踏 + 石柱」结算两次。
     */
    private static void spawnFallingBlocks(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getViewVector(1.0F);
        LivingEntity target = findNearestNonPlayer(player, 16.0);

        // 践踏中心：最近敌人脚下；没有敌人则落在视线前 4 格地面
        double cx, cz, fromY;
        if (target != null) {
            cx = target.getX();
            cz = target.getZ();
            fromY = target.getY();
        } else {
            cx = player.getX() + look.x * 4.0;
            cz = player.getZ() + look.z * 4.0;
            fromY = player.getY();
        }
        double gy = findGroundY(level, cx, fromY, cz);
        if (gy < level.getMinBuildHeight() + 1) return;

        // ── ① 地震践踏：范围内全部结算「面板 + min(面板, 目标最大生命 8%)」并上抛 ──
        float panel = playerFinalDamage(player);
        net.minecraft.world.phys.AABB quake = new net.minecraft.world.phys.AABB(
                cx - QUAKE_RADIUS, gy - 1.5D, cz - QUAKE_RADIUS,
                cx + QUAKE_RADIUS, gy + 3.0D, cz + QUAKE_RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, quake)) {
            if (e == player || e instanceof net.minecraft.world.entity.player.Player) continue;
            if (!e.isAlive()) continue;
            float dmg = panel + Math.min(panel, e.getMaxHealth() * QUAKE_HP_PART);
            e.invulnerableTime = 0;
            if (e.hurt(player.damageSources().playerAttack(player), dmg)) {
                // 本体 launch(entity, 2.0D, 0.6D)：水平朝外推 + 上抛
                double dx = e.getX() - cx;
                double dz = e.getZ() - cz;
                double d2 = Math.max(dx * dx + dz * dz, 0.001D);
                e.push(dx / d2 * 2.0D, 0.6D, dz / d2 * 2.0D);
                e.hurtMarked = true;
            }
        }

        // ── ② 掀地落石：纯视觉（本体同款装饰实体），撒在践踏圈内 ──
        BlockState block = Blocks.NETHERRACK.defaultBlockState();
        for (int i = 0; i < QUAKE_DEBRIS; i++) {
            double ang = level.random.nextDouble() * Math.PI * 2.0;
            double r = level.random.nextDouble() * QUAKE_RADIUS * 0.8;
            double x = cx + Math.cos(ang) * r;
            double z = cz + Math.sin(ang) * r;
            double y = findGroundY(level, x, gy, z);
            if (y < level.getMinBuildHeight() + 1) continue;
            var fb = new com.github.L_Ender.cataclysm.entity.effect.Cm_Falling_Block_Entity(level, x, y, z, block, 10);
            fb.push(0, 0.2 + level.random.nextGaussian() * 0.04, 0);
            markAndSpawn(fb);
        }

        // 本体 EarthQuake 也播爆炸音；旧照片版整段静音
        level.playSound(null, cx, gy, cz, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.15f);
    }

    /** 死亡激光存活 tick（本体 {@code setDuration(60)}，照片版收一半，20tick 蓄力另计） */
    private static final int DEATH_LASER_TICKS = 30;
    /** 额外附加的目标最大生命百分比（本体 {@code DeathLaserHpdamage}，口径 damage + min(damage, maxHealth × p × 0.01)） */
    private static final float DEATH_LASER_HP_PERCENT = 2.0F;

    /**
     * 先驱者：<b>死亡激光</b>（本体招牌 {@code Death_Laser_Beam_Entity}，30 格贯穿射线）。
     * <p>
     * <b>旧版为什么「不明显」</b>：旧版发射的是 {@code Laser_Beam_Entity}——那是先驱者
     * <b>普通远程攻击</b>用的小型激光弹（`The_Harbinger_Entity.performRangedAttack` 里的 laser 分支，
     * 伤害 {@code Harbinger.Laserdamage}），它 {@code extends Projectile}、带
     * {@code accelerationPower} 与惯性，本质是「一颗飞得快的小弹丸」，怎么摆都不显眼。
     * 本体的招牌是 <b>death laser</b>：{@code The_Harbinger_Entity} 死亡激光状态里
     * {@code new Death_Laser_Beam_Entity(..., 60, DeathLaserdamage, DeathLaserHpdamage)}。
     * <p>
     * 现用法照抄本体（3.32 jar javap 核实签名
     * {@code (EntityType, Level, LivingEntity, double, double, double, float, float, int, float, float)}）：
     * <ul>
     *   <li>{@code RADIUS = 30} 是<b>常量</b>——射线固定 30 格，贯穿；</li>
     *   <li>{@code tickCount > 20} 才开始结算（本体那 1 秒蓄力，正好配死亡激光的预警音）；</li>
     *   <li>伤害 {@code getDamage() + min(getDamage(), target.getMaxHealth() * Hpdamage * 0.01)}，
     *       每 tick 沿射线判定一次，由 {@code PhotoPercentDamageThrottleHandler} 的 10tick 节流兜住
     *       （本弹幕带 {@code photo_percent} 标记，且该实体的伤害源把<b>光束本身</b>作为直接实体，
     *       所以节流与 {@code BossProjHurtMixin} 清无敌帧都正常生效）；</li>
     *   <li>判定盒 {@code inflate(1,1,1)} —— 射线很「粗」，不容易从怪身边擦过去；</li>
     *   <li>安全：<b>不</b>调 {@code setFire(true)}（本体只在 powered 时开，开了会在命中地面处
     *       <b>生火</b>，属于地形副作用，照片版一律不发火）；</li>
     *   <li><b>朝向同步</b>：本体 {@code tick()} 每 tick 用 {@code caster.yHeadRot/getXRot()} 刷
     *       {@code renderYaw/renderPitch} 给<b>客户端渲染</b>，而<b>服务端判定</b>用的是构造时定死的
     *       {@code getYaw()/getPitch()}（玩家 caster 不会走 {@code updateWithHarbinger()}）。
     *       于是「挥砍后转头」会出现光柱视觉跟着摆、伤害留在原线的错位。这里在
     *       {@link #syncDeathLaserAim} 里每 tick 用同一个公式把服务端 yaw/pitch 也同步过去
     *       —— 效果就是一条<b>跟着你视线扫射</b>的死亡激光，与客户端所见一致。</li>
     * </ul>
     * 射线方向由我们传入（服务端用 {@code getYaw()/getPitch()}）；客户端按施法者头部朝向渲染，
     * 玩家挥砍瞬间两者一致，随后由 {@link #syncDeathLaserAim} 持续对齐。
     */
    private static void spawnDeathLaser(ServerPlayer player) {
        Level level = player.level();
        float yaw = (float) ((player.getYRot() + 90.0) * Math.PI / 180.0);
        float pitch = (float) (-player.getXRot() * Math.PI / 180.0);
        float dmg = playerFinalDamage(player);
        var beam = new com.github.L_Ender.cataclysm.entity.projectile.Death_Laser_Beam_Entity(
                com.github.L_Ender.cataclysm.init.ModEntities.DEATH_LASER_BEAM.get(),
                level, player,
                player.getX(), player.getEyeY(), player.getZ(),
                yaw, pitch,
                DEATH_LASER_TICKS,          // duration：结算 tick 数（另有 20 tick 蓄力）
                dmg,                        // damage：玩家面板
                DEATH_LASER_HP_PERCENT);    // Hpdamage：最大生命百分比
        markPercentAndSpawn(beam);
        PLAYER_DEATH_LASERS.add(new DeathLaserLink(player.getUUID(), new java.lang.ref.WeakReference<>(beam)));
    }

    /** 玩家发射的死亡激光：记录 (施法者, 光束) 以便每 tick 同步判定朝向 */
    private record DeathLaserLink(java.util.UUID caster,
                                  java.lang.ref.WeakReference<com.github.L_Ender.cataclysm.entity.projectile.Death_Laser_Beam_Entity> beam) {}

    private static final java.util.List<DeathLaserLink> PLAYER_DEATH_LASERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 把服务端判定朝向同步到施法者头部（公式与本体 {@code tick()} 里给渲染用的那一份完全一致：
     * {@code yaw = (yHeadRot + 90) * π/180}、{@code pitch = -xRot * π/180}）。
     * 光束消失/施法者下线即从表里摘掉。
     */
    private static void syncDeathLaserAim(net.minecraft.server.MinecraftServer server) {
        if (PLAYER_DEATH_LASERS.isEmpty()) return;
        var playerList = server.getPlayerList();
        for (var it = PLAYER_DEATH_LASERS.iterator(); it.hasNext(); ) {
            DeathLaserLink link = it.next();
            var beam = link.beam().get();
            if (beam == null || beam.isRemoved()) {
                it.remove();
                continue;
            }
            ServerPlayer caster = playerList == null ? null : playerList.getPlayer(link.caster());
            if (caster == null || !caster.isAlive()) continue;
            beam.setYaw((float) ((caster.yHeadRot + 90.0) * Math.PI / 180.0));
            beam.setPitch((float) (-caster.getXRot() * Math.PI / 180.0));
        }
    }

    /** 利维坦：深渊裂缝激光（8+5%生命，2s 后从裂缝射出） */
    private static void spawnAbyssBlast(ServerPlayer player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double px = eye.x + look.x * 5.0;
        double pz = eye.z + look.z * 5.0;
        double py = findGroundY(level, px, eye.y, pz);
        if (py < level.getMinBuildHeight() + 1) return;
        float yaw = (float) (player.getYRot() * Math.PI / 180.0);
        var blast = new com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Leviathan.Abyss_Blast_Portal_Entity(
                level, px, py, pz, yaw, 40, playerFinalDamage(player), 0.05f, player);
        markPercentAndSpawn(blast);
    }

    /** 岩碑风暴的碑数（本体一次技能撒 8×16+16=144 个，照片版收成一轮 5 面） */
    private static final int STELE_COUNT = 5;
    /** 岩碑坠落高度（本体在「天花板之下、约 bossY+17」生成，这里按地形自适应） */
    private static final int STELE_FALL_HEIGHT = 14;

    /**
     * 远古遗魂：<b>岩碑风暴</b>——5 面沙岩碑从目标头顶高空成环坠落，贯穿命中。
     * <p>
     * <b>为什么必须重做</b>（对已安装的<b>灾变 3.32 jar</b> 反编译核实；注意
     * {@code 参考项目的源码\灾变} 是 3.33 源码树，两版实现不同）：
     * <ul>
     *   <li>3.32 的 {@code Ancient_Desert_Stele_Entity extends Projectile}，<b>没有</b>落地 AoE
     *       （{@code setImpactDamage}/{@code setImpactRadius} 是 3.33 才加的，写上去编不过），
     *       {@code onHitBlock} 是空实现，伤害<b>只</b>来自每 tick 的移动射线命中实体；</li>
     *   <li>旧照片版用 {@code findGroundY} 把石碑<b>贴地</b>生成 ⇒ warmup 结束后第一个移动 tick
     *       的射线（起点是脚底）立刻打中脚下的方块 → {@code onHitBlock}（空）→ {@code onHit()} →
     *       放个音效粒子就 {@code discard()} ⇒ <b>伤害恒为 0</b>，与韧性减伤、命中率都无关；</li>
     *   <li>本体用法是从目标 Y 向上扫到 {@code min(maxBuildHeight, bossY+20)}，在<b>约 17 格高空</b>
     *       生成、{@code delay = 1..16} 分批砸落（一次 144 个）。</li>
     * </ul>
     * 现在：以敌人为中心画一圈半径 1.2~2.0 的落点，出生高度取
     * {@link #findAirSpawnY}（天花板之下的最高空气位，洞穴战不会卡进方块），warmup 1/3/5/7/9
     * 依次砸下（岩碑风暴的「分批」感），伤害直接用玩家攻击面板（3.32 不会像 3.33 那样额外 ×0.6）。
     */
    private static void spawnDesertStele(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getViewVector(1.0F);
        float yawRad = (float) (player.getYRot() * Math.PI / 180.0);
        float dmg = playerFinalDamage(player);
        LivingEntity target = findNearestNonPlayer(player, 16.0);
        double bx, bz, by;
        if (target != null) {
            bx = target.getX();
            bz = target.getZ();
            by = target.getY();
        } else {
            Vec3 eye = player.getEyePosition();
            bx = eye.x + look.x * 4.0;
            bz = eye.z + look.z * 4.0;
            by = player.getY();
        }
        for (int i = 0; i < STELE_COUNT; i++) {
            // 环形落点（风车阵的原版味道），带一点随机偏移免得每次都一模一样
            double ang = (Math.PI * 2.0 / STELE_COUNT) * i + level.random.nextDouble() * 0.3;
            double r = 1.2 + level.random.nextDouble() * 0.8;
            double px = bx + Math.cos(ang) * r;
            double pz = bz + Math.sin(ang) * r;
            double py = findAirSpawnY(level, px, pz, by, STELE_FALL_HEIGHT);
            if (py < level.getMinBuildHeight() + 1) continue;
            var stele = new com.github.L_Ender.cataclysm.entity.projectile.Ancient_Desert_Stele_Entity(
                    level, px, py, pz, yawRad, 1 + i * 2, dmg, player);
            // 3.32 的 DAMAGE 是同步数据：构造后再显式设一次，避免版本差异导致 0 伤害
            try { stele.setDamage(dmg); } catch (Throwable ignored) {}
            markAndSpawn(stele);
        }
    }

    /**
     * 找一个「天花板之下的最高空气位」作为坠落弹幕的出生点：从 {@code fromY + maxRise} 向下
     * 找连续两格空气的位置；洞穴/室内会自然落到天花板正下方，保证石碑真的能往下飞，
     * 而不是卡在石头里第一个 tick 就判定撞方块消失。
     *
     * @return 出生 Y；找不到返回极小值
     */
    private static double findAirSpawnY(Level level, double x, double z, double fromY, int maxRise) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = Math.min((int) Math.floor(fromY) + maxRise, level.getMaxBuildHeight() - 3);
        int bottom = (int) Math.floor(fromY) + 3;
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            if (level.isEmptyBlock(pos) && level.isEmptyBlock(pos.above())) {
                return y;
            }
        }
        return level.getMinBuildHeight() - 1;
    }

    /** 咒翼灵骸：追踪灵魂箭 ×3（3.5 点幽灵伤害） */
    private static void spawnPhantomArrows(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getViewVector(1.0F);
        LivingEntity target = player.getLastHurtMob();
        for (int i = 0; i < 3; i++) {
            var arrow = new com.github.L_Ender.cataclysm.entity.projectile.Phantom_Arrow_Entity(level, player, target);
            arrow.setBaseDamage(playerFinalDamage(player));
            double spread = (i - 1) * (6.0 * Math.PI / 180.0);
            Vec3 dir = look.yRot((float) spread);
            arrow.shoot(dir.x, dir.y, dir.z, 1.8f, 1.0f);
            arrow.setPos(player.getX(), player.getEyeY(), player.getZ());
            markAndSpawn(arrow);
        }
    }

    /** 斯库拉：水波 ×3（6 点伤害 + 湿润，扇形 25°） */
    private static void spawnWaves(ServerPlayer player) {
        Level level = player.level();
        for (int i = 0; i < 3; i++) {
            var wave = new com.github.L_Ender.cataclysm.entity.effect.Wave_Entity(level, player, 60, playerFinalDamage(player));
            wave.setPos(player.getX(), player.getY() + 0.5, player.getZ());
            wave.setState(1);
            float yaw = player.getYRot() + (i - 1) * 25.0f;
            wave.setYRot(yaw);
            markAndSpawn(wave);
        }
    }

    /** 堕落圣骑：灵魂尖刺 ×3（等同攻击面板 + 目标最大生命 3% 的幽灵伤害）
     *  追踪最近生物，在其脚下升起；找不到则退回玩家视线前方 */
    private static void spawnSoulPillars(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getViewVector(1.0F);
        float yawRad = (float) (player.getYRot() * Math.PI / 180.0);
        double bx, bz, by;
        LivingEntity target = findNearestNonPlayer(player, 16.0);
        if (target != null) {
            bx = target.getX();
            bz = target.getZ();
            by = findGroundY(level, bx, target.getY(), bz);
        } else {
            Vec3 eye = player.getEyePosition();
            bx = eye.x + look.x * 4.0;
            bz = eye.z + look.z * 4.0;
            by = findGroundY(level, bx, eye.y, bz);
        }
        if (by < level.getMinBuildHeight() + 1) return;
        float dmg = playerFinalDamage(player);
        for (int i = 0; i < 3; i++) {
            double side = (i - 1) * 1.25;
            double px = bx - look.z * side;
            double pz = bz + look.x * side;
            double py = by + 0.1;
            var pillar = new net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.SoulPillarEntity(
                    level, px, py, pz, yawRad, 20, player, 20, dmg, false);
            markAndSpawn(pillar);
        }
    }

    /** 云筑魔像：天穹激光（贯穿 30 格，每 0.25s 3+1%生命，40tick） */
    private static void spawnEnergyBeam(ServerPlayer player) {
        Level level = player.level();
        EntityType<?> beamType = null;
        try {
            beamType = net.miauczel.legendary_monsters.entity.ModEntities.ENERGY_BEAM.get();
        } catch (Exception ignored) {}
        if (beamType == null) return;
        float yaw = (float) ((player.getYRot() + 90.0) * Math.PI / 180.0);
        float pitch = (float) (-player.getXRot() * Math.PI / 180.0);
        var beam = new net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.EnergyBeamEntity(
                (EntityType<? extends net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.EnergyBeamEntity>) beamType,
                level, player, player.getX(), player.getY() + 1.0, player.getZ(),
                yaw, pitch, 40, playerFinalDamage(player), 0.0f);
        markPercentAndSpawn(beam);
    }

    /** 弹幕基础伤害 = 玩家攻击面板（ATTACK_DAMAGE 属性值，已含力量/武器等加成）；受 barrage_dmg 倍率影响 */
    private static float playerFinalDamage(ServerPlayer player) {
        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float m = player.getPersistentData().getFloat("lensouls:barrage_dmg_mult");
        return m > 0 ? base * m : base;
    }

    /** 湮灭激光的射线长度：本体对「玩家 caster」会再砍一半，所以传 60 → 实际 30 格 */
    private static final float ANNIHILATION_BEAM_REACH = 60.0F;
    /** 激光存活 tick（本体 DURATION：每 tick 沿射线做一次实体判定，到点 discard） */
    private static final int ANNIHILATION_BEAM_TICKS = 30;
    /** 额外附加的目标最大生命百分比（本体 {@code Hpdamage} 是百分数：damage + maxHealth × Hpdamage × 0.01） */
    private static final float ANNIHILATION_BEAM_HP_PERCENT = 2.0F;

    /**
     * 湮灭构造体：湮灭激光（贯穿长射线，面板 + 目标最大生命 2%，贯穿视线方向）。
     * <p>
     * <b>旧版「射线很短」的根因</b>（对实机 jar `legendary_monsters-2.1.20` 的
     * {@code AnnihilationBeamEntity} 逐字节核实）：射线长度<b>就是</b>构造参数里的最后一个
     * {@code r}（同步字段 {@code B_RADIUS}），而 {@code calculateEndPos()} 里写死了
     * <pre>float r = caster instanceof Player ? B_RADIUS / 2 : B_RADIUS;   // 字节码：instanceof Player → fdiv</pre>
     * ——玩家当 caster 时还会<b>再砍一半</b>。旧版传的是 {@code r = 3.0f} ⇒ 实际射线只有
     * <b>1.5 格</b>，于是「看着像没打出去」。官方玩家武器 {@code AtomSplitterItem} 是构造后
     * {@code setRadius(30)}（玩家 → 15 格），boss 本体传 5~30（非玩家不砍半）。
     * 照片版取 60 → <b>30 格</b>，拿到正版观感。
     * <p>
     * 伤害口径照抄本体 {@code tick()}：{@code getDamage() + target.getMaxHealth() * (getHpDamage() * 0.01)}。
     * <p>
     * <b>两条必须知道的实机事实</b>（均对实机 jar 2.1.20 反编译核实，别照直觉写）：
     * <ul>
     *   <li>这条射线的伤害源把<b>玩家</b>当作直接实体（{@code ModDamageTypes.causeAnnihilationDamage}
     *       的实现是 {@code new DamageSource(holder, caster, caster)}，**完全忽略传入的射线实体**）。
     *       因此 {@code PhotoPercentDamageThrottleHandler} 与 {@code BossProjHurtMixin}（都按
     *       {@code getDirectEntity()} 找标记）对<b>它都不生效</b>，实际节流来自原版无敌帧
     *       （{@code invulnerableTime = 20}）→ 30 tick 的射线大约只结算 2 次，数值不失控；
     *       同时也说明它<b>不会</b>回头触发弹幕（{@code RangedAttackHelper.isRanged} 要求
     *       {@code !isDirect()}，而这里就是直接命中）。</li>
     *   <li>{@code onAddedToLevel()} 会给非 quad 射线附赠一串爆点
     *       （{@code spawnExplosions(8, 2, 射线长/π)}，且 Player 平视时几乎必触发）——射线越长爆点越多，
     *       每个 8.0 伤害 + 2 发 6.0 小弹且<b>伤害写死无法缩放</b>。已用
     *       {@code ObliteratorBeamNoAutoExplosionMixin} 对带 {@code photo_proj} 的射线掐掉这次调用。</li>
     * </ul>
     */
    private static void spawnAnnihilationLaser(ServerPlayer player) {
        if (!com.plumejade.lensouls.entity.BossPhantomType.OBLITERATOR.isModLoaded()) return;
        Level level = player.level();
        EntityType<?> beamType = net.miauczel.legendary_monsters.entity.ModEntities.ANNIHILATION_BEAM.get();
        float yaw = (float) ((player.getYRot() + 90.0) * Math.PI / 180.0);
        float pitch = (float) (-player.getXRot() * Math.PI / 180.0);
        float dmg = playerFinalDamage(player);
        var beam = new net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.AnnihilationBeamEntity(
                (EntityType<? extends net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.AnnihilationBeamEntity>) beamType,
                level, player, player.getX(), player.getEyeY(), player.getZ(),
                yaw, pitch,
                ANNIHILATION_BEAM_TICKS,        // duration：存活 tick
                dmg,                            // damage：玩家面板
                ANNIHILATION_BEAM_HP_PERCENT,   // Hpdamage：最大生命百分比
                1,                              // delay：起手延迟
                false,                          // isQuad：玩家版走单道长射线（true 是本体四向风车）
                0.0f,                           // followSpeed：0 = 不扫射
                0.0f,                           // additionalRotation
                0.0f,                           // turnBackAtDurationPrecentage
                false,                          // rightTurnFirst
                ANNIHILATION_BEAM_REACH);       // r：射线长度（玩家会 /2 → 30 格）
        // 构造器里 calculateEndPos() 在 setRadius(r) 之前跑过一次，这里补一次让第 0 tick 就是全长
        // （官方玩家武器 AtomSplitterItem 也是构造后 setRadius）
        try { beam.setRadius(ANNIHILATION_BEAM_REACH); } catch (Throwable ignored) {}
        markPercentAndSpawn(beam);
    }

    /** 唤魔者：尖牙一排（从玩家视线前方地面钻出；群伤，伤害为原版固定值——Minecraft 未暴露 setter，无法挂面板） */
    private static void spawnEvokerFangs(ServerPlayer player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        float yawRad = (float) (player.getYRot() * Math.PI / 180.0);
        for (int i = 1; i <= 5; i++) {
            double dx = eye.x + look.x * i;
            double dz = eye.z + look.z * i;
            double dy = findGroundY(level, dx, eye.y, dz);
            if (dy < level.getMinBuildHeight() + 1) continue;
            EvokerFangs fang = new EvokerFangs(level, dx, dy, dz, yawRad, 6, player);
            markAndSpawn(fang);
        }
    }

    /** 骷髅：弓箭 ×3（朝最近生物；伤害等同攻击面板） */
    private static void spawnSkeletonArrows(ServerPlayer player) {
        Level level = player.level();
        float dmg = playerFinalDamage(player);
        LivingEntity target = findNearestNonPlayer(player, 16.0);
        Vec3 from = new Vec3(player.getX(), player.getEyeY(), player.getZ());
        Vec3 dir = target != null
                ? new Vec3(target.getX(), target.getEyeY(), target.getZ()).subtract(from).normalize()
                : player.getViewVector(1.0F);
        for (int i = 0; i < 3; i++) {
            Arrow arrow = new Arrow(EntityType.ARROW, level);
            arrow.setOwner(player);
            arrow.setPos(from.x, from.y, from.z);
            double spread = (i - 1) * (6.0 * Math.PI / 180.0);
            Vec3 d = dir.yRot((float) spread);
            arrow.shoot(d.x, d.y, d.z, 1.6f, 1.0f);
            arrow.setBaseDamage(dmg);
            arrow.setCritArrow(false);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            markAndSpawn(arrow);
        }
    }

    // ========== archaion / fdbosses 兼容弹幕（运行时反射，可选 mod 缺席时优雅跳过）==========

    private static boolean modLoaded(String id) {
        return ModList.get().isLoaded(id);
    }

    /** 反射读取 fdbosses BossEntities 的静态 Supplier<EntityType> 字段并求值 */
    private static EntityType<?> fdEntityType(String field) throws Exception {
        Class<?> init = Class.forName("com.finderfeed.fdbosses.init.BossEntities");
        java.util.function.Supplier<?> sup = (java.util.function.Supplier<?>) init.getField(field).get(null);
        return (EntityType<?>) sup.get();
    }

    /** 深渊终末：回声之星（模仿 echos_grace 满蓄射击，命中点 3 格 AoE 爆炸伤害） */
    private static void spawnEchoStar(ServerPlayer player, LivingEntity hit) {
        if (!modLoaded("archaion")) return;
        Level level = player.level();
        LivingEntity target = hit != null ? hit : findNearestNonPlayer(player, 24.0);
        Vec3 eye = player.getEyePosition();
        Vec3 dir = target != null
                ? new Vec3(target.getX(), target.getY() + target.getEyeHeight(), target.getZ()).subtract(eye).normalize()
                : player.getViewVector(1.0F);
        try {
            Class<?> cls = Class.forName("com.ratrod.archaion.entities.projectile.EchoStarProjectile");
            Projectile proj = (Projectile) cls.getConstructor(Level.class, LivingEntity.class, ItemStack.class)
                    .newInstance(level, player, ItemStack.EMPTY);
            cls.getMethod("setBaseDamage", float.class).invoke(proj, playerFinalDamage(player));
            proj.shoot(dir.x, dir.y, dir.z, 1.8f, 0.0f);
            markAndSpawn((Entity) proj);
        } catch (Exception ex) {
            com.plumejade.lensouls.LenSouls.LOGGER.warn("[PhotoBoss] 回声之星弹幕失败", ex);
        }
    }

    /** 王国：巨剑斩击（巨剑在玩家位置拔起，面朝被打中的敌人；命中时刻由镜头对前方敌人劈出免自伤的范围竖劈） */
    private static void spawnMalkuthSword(ServerPlayer player, LivingEntity hit) {
        if (!modLoaded("fdbosses")) return;
        Level level = player.level();
        LivingEntity target = hit != null ? hit : findNearestNonPlayer(player, 24.0);
        if (target == null) return;
        try {
            Class<?> atkCls = Class.forName("com.finderfeed.fdbosses.content.entities.malkuth_boss.MalkuthAttackType");
            Object fire = Enum.valueOf((Class<? extends Enum>) atkCls, "FIRE");
            Vec3 pos = player.position();
            Vec3 dir = new Vec3(target.getX() - pos.x, 0, target.getZ() - pos.z);
            if (dir.lengthSqr() < 1e-6) dir = new Vec3(0, 0, 1);
            dir = dir.normalize();
            Class<?> swordCls = Class.forName("com.finderfeed.fdbosses.content.entities.malkuth_boss.malkuth_giant_sword.MalkuthGiantSwordSlash");
            Method summon = swordCls.getMethod("summon", Level.class, Vec3.class, Vec3.class, atkCls, float.class);
            Object sword = summon.invoke(null, level, pos, dir, fire, playerFinalDamage(player));
            if (sword != null && level instanceof ServerLevel sl && sl.getServer() != null) {
                // 关掉实体自带伤害（会连玩家一起劈），由镜头在命中时刻自行劈出（免自伤、归属玩家）
                swordCls.getMethod("setDoDamage", boolean.class).invoke(sword, false);
                SWORD_CLEAVES.add(new SwordCleave(
                        (long) sl.getServer().getTickCount() + 90, sl, pos, dir, player, playerFinalDamage(player)));
            }
        } catch (Exception ex) {
            com.plumejade.lensouls.LenSouls.LOGGER.warn("[PhotoBoss] 巨剑斩击弹幕失败", ex);
        }
    }

    /** 待竖劈的巨剑（fdbosses MalkuthGiantSwordSlash 起手 60t + 下劈 30t = 90t 命中） */
    private record SwordCleave(long atTick, ServerLevel level, Vec3 pos, Vec3 dir, ServerPlayer caster, float dmg) {}
    private static final java.util.concurrent.ConcurrentLinkedQueue<SwordCleave> SWORD_CLEAVES =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** 在巨剑命中时刻对其视线前方 32 格范围的敌人劈出 面板 伤害（排除玩家/队友，归属玩家攻击源） */
    private static void runSwordCleaves(ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();
        SwordCleave sc;
        while ((sc = SWORD_CLEAVES.peek()) != null) {
            if (sc.atTick() > now) break;
            SWORD_CLEAVES.poll();
            ServerPlayer caster = sc.caster();
            if (caster == null || !caster.isAlive()) continue;
            Vec3 c = sc.pos().add(sc.dir().scale(7.5)).add(0, -1, 0);
            Vec3 a = sc.dir().scale(32.0);
            Vec3 p = sc.dir().yRot((float) (Math.PI / 2.0)).scale(10.0);
            Vec3 up = new Vec3(0, 10, 0);
            double mnx = Double.MAX_VALUE, mny = Double.MAX_VALUE, mnz = Double.MAX_VALUE;
            double mxx = -Double.MAX_VALUE, myy = -Double.MAX_VALUE, mzz = -Double.MAX_VALUE;
            for (Vec3 v : new Vec3[] {
                    c.add(a).add(p), c.add(a).subtract(p), c.subtract(a).add(p), c.subtract(a).subtract(p),
                    c.add(a).add(p).add(up), c.add(a).subtract(p).add(up),
                    c.subtract(a).add(p).add(up), c.subtract(a).subtract(p).add(up)}) {
                mnx = Math.min(mnx, v.x); mny = Math.min(mny, v.y); mnz = Math.min(mnz, v.z);
                mxx = Math.max(mxx, v.x); myy = Math.max(myy, v.y); mzz = Math.max(mzz, v.z);
            }
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(mnx, mny, mnz, mxx, myy, mzz);
            Class<?> buddy = null;
            try {
                buddy = Class.forName("com.finderfeed.fdbosses.content.entities.malkuth_boss.MalkuthBossBuddy");
            } catch (Exception ignored) {}
            for (LivingEntity e : sc.level().getEntitiesOfClass(LivingEntity.class, box)) {
                if (e == caster || e instanceof net.minecraft.world.entity.player.Player) continue;
                if (!e.isAlive()) continue;
                if (buddy != null && buddy.isInstance(e)) continue;
                e.invulnerableTime = 0;
                e.hurt(caster.damageSources().playerAttack(caster), sc.dmg());
            }
        }
    }

    /** 审判：法阵射线（在被打中的敌人身上浮现青色法阵，蓄力后朝敌我连线方向射出 20 格贯穿射线） */
    private static void spawnGeburahRay(ServerPlayer player, LivingEntity hit) {
        if (!modLoaded("fdbosses")) return;
        Level level = player.level();
        LivingEntity target = hit != null ? hit : findNearestNonPlayer(player, 24.0);
        if (target == null) return;
        try {
            Vec3 origin = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
            Vec3 dir = origin.subtract(player.getEyePosition()).normalize();
            Class<?> cls = Class.forName("com.finderfeed.fdbosses.content.entities.geburah.casts.GeburahRayCastingCircle");
            Method summon = cls.getMethod("summon", Level.class, Vec3.class, Vec3.class);
            Object circle = summon.invoke(null, level, origin, dir);
            // 该法阵由 fdbosses 自行加入世界，我们只补标记：命中时不得再回头掷弹幕触发
            markOnly(circle);
        } catch (Exception ex) {
            com.plumejade.lensouls.LenSouls.LOGGER.warn("[PhotoBoss] 法阵射线弹幕失败", ex);
        }
    }

    /** 仁慈：动能力场（在被打中的敌人位置竖起能量场，1.5s 内对其持续造成每 5tick 一次的电击伤害后消散） */
    private static void spawnChesedField(ServerPlayer player, LivingEntity hit) {
        if (!modLoaded("fdbosses")) return;
        Level level = player.level();
        LivingEntity target = hit != null ? hit : findNearestNonPlayer(player, 16.0);
        if (target == null) return;
        try {
            EntityType<?> type = fdEntityType("CHESED_KINETIC_FIELD");
            Entity field = type.create(level);
            if (field == null) return;
            field.setPos(target.getX(), target.getY(), target.getZ());
            markAndSpawn(field);
            discardLater(field, 45);
            KINETIC_FIELDS.put(field.getUUID(),
                    new KineticField(target.getUUID(), player, playerFinalDamage(player) * 0.5f,
                            level.getGameTime(), 6));
        } catch (Exception ex) {
            com.plumejade.lensouls.LenSouls.LOGGER.warn("[PhotoBoss] 动能力场弹幕失败", ex);
        }
    }

    // ---- 临时实体超时清理（fdbosses 动能力场等不自删实体兜底）----

    private static final Map<java.util.UUID, Long> DISCARD_AT = new ConcurrentHashMap<>();

    /** 动能力场 tick 电击记录：锁定被召唤时困住的敌人，每 5tick 电击一次 */
    private record KineticField(java.util.UUID enemy, ServerPlayer caster, float dmg, long nextHit, int hitsLeft) {}
    private static final Map<java.util.UUID, KineticField> KINETIC_FIELDS = new ConcurrentHashMap<>();

    private static void discardLater(Entity entity, int ticks) {
        if (entity.level() instanceof ServerLevel sl && sl.getServer() != null) {
            DISCARD_AT.put(entity.getUUID(), (long) sl.getServer().getTickCount() + ticks);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();
        // 玩家死亡激光：判定朝向持续跟随施法者视线（否则光柱视觉在摆、伤害留在原线）
        syncDeathLaserAim(event.getServer());
        // 超时清理不自删的临时实体（动能力场墙）
        if (!DISCARD_AT.isEmpty()) {
            DISCARD_AT.entrySet().removeIf(entry -> {
                if (entry.getValue() > now) return false;
                for (ServerLevel sl : event.getServer().getAllLevels()) {
                    Entity ent = sl.getEntity(entry.getKey());
                    if (ent != null) ent.discard();
                }
                return true;
            });
        }
        // 巨剑竖劈：到达 90t 命中时刻的自管伤害（实体自带伤害已关）
        runSwordCleaves(event);
        // 动能力场 tick 电击：对被困住的敌人持续伤害（清无敌帧保证每 5tick 全额命中）
        if (KINETIC_FIELDS.isEmpty()) return;
        for (var e : new ArrayList<>(KINETIC_FIELDS.entrySet())) {
            KineticField kf = e.getValue();
            ServerPlayer caster = kf.caster();
            boolean dead = caster == null || !caster.isAlive();
            if (dead || caster.level().getGameTime() < kf.nextHit()) {
                if (dead) KINETIC_FIELDS.remove(e.getKey());
                continue;
            }
            LivingEntity enemy = null;
            for (ServerLevel sl : event.getServer().getAllLevels()) {
                Entity ent = sl.getEntity(kf.enemy());
                if (ent instanceof LivingEntity le) { enemy = le; break; }
            }
            if (enemy == null || !enemy.isAlive()) {
                KINETIC_FIELDS.remove(e.getKey());
                continue;
            }
            enemy.invulnerableTime = 0;
            enemy.hurt(caster.damageSources().playerAttack(caster), kf.dmg());
            int left = kf.hitsLeft() - 1;
            if (left <= 0) {
                KINETIC_FIELDS.remove(e.getKey());
            } else {
                KINETIC_FIELDS.put(e.getKey(),
                        new KineticField(kf.enemy(), caster, kf.dmg(), caster.level().getGameTime() + 5, left));
            }
        }
    }

    /** 从 y 向下扫描，返回地面稳固方块上方 1 格（找不到返回极小值） */
    private static double findGroundY(Level level, double x, double startY, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        for (int y = (int) Math.floor(startY) + 2; y > level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            if (level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
                return y + 1.0;
            }
        }
        return level.getMinBuildHeight() - 1;
    }

    /** 打上照片弹幕标记后加入世界（供 BossProjHurtMixin 清目标无敌帧） */
    private static void markAndSpawn(net.minecraft.world.entity.Entity entity) {
        com.plumejade.lensouls.util.PhotoProjMarker.mark(entity, false);
        entity.level().addFreshEntity(entity);
    }

    /**
     * 打上照片弹幕 + "会造成目标最大生命百分比伤害"标记后加入世界。
     * BossProjHurtMixin 仅对带该标记的弹幕做 10tick 内置间隔（同目标命中节流）。
     */
    private static void markPercentAndSpawn(net.minecraft.world.entity.Entity entity) {
        com.plumejade.lensouls.util.PhotoProjMarker.mark(entity, true);
        entity.level().addFreshEntity(entity);
    }

    /**
     * 只打标记、不加入世界——给「由第三方模组的 summon 方法内部自行加入世界」的弹幕用。
     * 漏掉这一步的弹幕（例如 fdbosses 的法阵射线）伤害归属玩家且不带标记，
     * 命中时会被当成普通远程伤害再掷一次弹幕触发 → 触发/弹幕回环。
     */
    private static void markOnly(Object maybeEntity) {
        if (maybeEntity instanceof net.minecraft.world.entity.Entity entity) {
            com.plumejade.lensouls.util.PhotoProjMarker.mark(entity, false);
        }
    }
}
