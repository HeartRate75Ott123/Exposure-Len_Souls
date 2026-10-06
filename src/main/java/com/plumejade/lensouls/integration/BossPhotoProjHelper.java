package com.plumejade.lensouls.integration;

import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.plumejade.lensouls.entity.SwarmPhantomFade;
import com.plumejade.lensouls.network.SwarmPhantomPacket;
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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

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
        TRIGGER.put("minecraft:ender_dragon", 0.10f);
        TRIGGER.put("minecraft:illusioner", 0.12f);
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

    /**
     * 登录即把服务端的弹幕开关权威值对齐给客户端。
     * <p>
     * 客户端那份只用于画状态提示，不参与判定（真正的裁决在 {@link #trigger} 里读服务端状态），
     * 所以这里漏发最多是提示文字与实际不一致——不能省。
     */
    @SubscribeEvent
    public static void onLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.plumejade.lensouls.network.BarrageStatePacket.send(
                    player, com.plumejade.lensouls.util.BarrageToggle.isEnabled(player));
        }
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
        // 玩家用 B 键关掉弹幕：B 键开关的唯一收口点。
        // 放在最前面（早于去重与限流），关闭时连掷骰额度都不消耗——否则关着也在烧配额，
        // 重新打开时窗口已经被占满。套装额外弹幕与法师胸针必触发都在 triggerInner 里，
        // 因此一并被这一个判断覆盖。
        if (!com.plumejade.lensouls.util.BarrageToggle.isEnabled(player)) return;

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

        // 「玩家刚打中的目标」：在掷骰之前记录，所以即使这一刀没掷中弹幕概率，
        // 已经召唤出来的幻翼也会改扑这个新目标（需求：幻翼优先攻击玩家新打到的）。
        rememberHit(player, target);

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
                case "minecraft:ender_dragon" -> spawnDragonCrystals(player);
                case "minecraft:illusioner" -> spawnPhantomSwarm(player, hit);
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

    /**
     * 玩家周围指定范围内最近的<b>可打目标</b>（选取口径统一在 {@code AllyFilter#isEnemyOf}）。
     * <p>
     * 排除玩家自身、<b>驯服生物</b>（弹幕是「打面前那个敌人」的选敌逻辑，主人身边的狗会挤进这条逻辑里
     * 被当成敌人——即使伤害随后被 {@code PhotoProjSafetyHandler} 免掉，弹幕仍会对着它放技能、
     * 施加减益与击退；既不选它、也不打它才干净）、<b>自家召唤物</b>
     * （幻灵本体与随从、以及 {@link #isSwarmPhantom 幻影幻翼}——空挥路径与目标死亡后的兜底选敌都会经过这里，
     * 不排除就会「新一批幻翼把上一批当敌人打」）、以及<b>第三方辅助作战单位</b>
     * （gytrinket 无人机/蜂群/僚机，否则「最近敌人」会被随行的无人机抢走）。
     */
    private static LivingEntity findNearestNonPlayer(ServerPlayer player, double radius) {
        LivingEntity best = null;
        double bestDist = radius * radius;
        for (net.minecraft.world.entity.Entity e : player.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                en -> en.isAlive() && com.plumejade.lensouls.util.AllyFilter.isEnemyOf(player, en))) {
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
        // 部件感知：多子部件 BOSS（九头蛇头/娜迦体节）的碰撞体是 PartEntity，只查 LivingEntity 会漏
        for (LivingEntity e : com.plumejade.lensouls.util.PartHitUtil.livingTargetsInBox(level, quake)) {
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
        Vec3 origin = beamOrigin(player);
        var beam = new com.github.L_Ender.cataclysm.entity.projectile.Death_Laser_Beam_Entity(
                com.github.L_Ender.cataclysm.init.ModEntities.DEATH_LASER_BEAM.get(),
                level, player,
                origin.x, origin.y, origin.z,
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
     * <p>
     * <b>崩溃教训（1.5.3 修复）</b>：{@code PLAYER_DEATH_LASERS} 是 {@link java.util.concurrent.CopyOnWriteArrayList}，
     * 其迭代器 {@code COWIterator.remove()} **恒抛 UnsupportedOperationException**——
     * 1.4.96~1.5.2 里这里写的是 {@code it.remove()}，先驱者死亡激光**结算完的那一刻**
     * （{@code beam.isRemoved()}）就触发实机服务端 tick 崩溃（crash-2026-09-29）。
     * 修法：for-each 走 COW 快照遍历，移除用 {@code list.remove(link)}（COW 支持）。
     */
    private static void syncDeathLaserAim(net.minecraft.server.MinecraftServer server) {
        if (PLAYER_DEATH_LASERS.isEmpty()) return;
        var playerList = server.getPlayerList();
        for (DeathLaserLink link : PLAYER_DEATH_LASERS) {
            var beam = link.beam().get();
            if (beam == null || beam.isRemoved()) {
                PLAYER_DEATH_LASERS.remove(link);
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

    /** 光束起点相对眼睛的横向偏移（格）：往玩家右手挪，让相机不再正好压在光束轴上 */
    private static final double BEAM_ORIGIN_RIGHT = 0.35;
    /** 光束起点相对眼睛的下移（格） */
    private static final double BEAM_ORIGIN_DOWN = 0.15;

    /**
     * 光束类弹幕（湮灭激光 / 死亡激光）的生成点：从眼睛往「右手 0.35 / 下 0.15」偏一点。
     * <p>
     * <b>为什么必须偏</b>：这两种光束的几何就是「若干张<b>包含光束轴</b>的面片」（见
     * {@code AnnihilationBeamCrossRenderMixin} 里的复算），而相机在光束轴上时<b>同时落在那几张面片里</b>，
     * 投影只剩一条线——玩家第一人称几乎看不到光束本体。原版的光束看着立体，是因为看的人（玩家看 BOSS 的光束）
     * 本来就站在轴外；我们这条光束的发射者就是观察者本人，只能把起点从眼睛里挪出来。
     * <p>
     * 伤害代价可忽略：射线只是横移 0.35 格，30 格射程上约 0.7°，且两个光束的命中盒都带 {@code inflate(1,1,1)}。
     */
    private static Vec3 beamOrigin(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-6) {
            right = new Vec3(1.0, 0.0, 0.0);   // 正上/正下看时叉积退化，随便给个方向即可
        }
        return eye.add(right.normalize().scale(BEAM_ORIGIN_RIGHT)).add(0.0, -BEAM_ORIGIN_DOWN, 0.0);
    }

    /** 湮灭激光的射线长度：本体对「玩家 caster」会再砍一半，所以传 60 → 实际 30 格 */
    private static final float ANNIHILATION_BEAM_REACH = 60.0F;
    /**
     * 激光存活 tick（本体 DURATION：每 tick 沿射线做一次实体判定，到点 discard）。
     * <p>
     * 取 <b>36 = 3 个「爆点动画整轮」</b>：命中点的爆点一轮 = 5 帧 × 120 ms = 12 tick
     * （{@code client/render/AnnihilationBurstClock}）。原值 30 = 2.5 轮，光束会在半轮上结束；
     * 用户要求「至少等这轮动画放完再结束生命」，所以本体寿命对齐到整轮边界
     * （配合 {@code mixin/compat/AnnihilationExplosionRoundMixin} 让收尾那一整轮也放完）。
     */
    private static final int ANNIHILATION_BEAM_TICKS = 36;
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
        Vec3 origin = beamOrigin(player);
        var beam = new net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.AnnihilationBeamEntity(
                (EntityType<? extends net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.AnnihilationBeamEntity>) beamType,
                level, player, origin.x, origin.y, origin.z,
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

    // ========== 末影龙：末影水晶赐福 ==========

    /** 水晶存续 tick（40 = 2 秒） */
    private static final int CRYSTAL_TICKS = 40;
    /** 每次召唤的水晶数 */
    private static final int CRYSTAL_COUNT = 3;
    /** 水晶水平散布半径（格） */
    private static final double CRYSTAL_SPREAD = 2.6D;
    /**
     * 每 tick 回复占最大生命的比例：40 × 0.005 = <b>单次共 20%</b>。
     * <p>
     * 原版 {@code EndCrystal} <b>本身不治疗</b>（末地龙战是末影龙扫描水晶回血），
     * 所以这里的回血完全由本队列驱动 —— 不是借用原版机制。
     */
    private static final float CRYSTAL_HEAL_RATIO = 0.005F;

    /**
     * 一次赐福水晶。<b>按玩家去重</b>：回血是玩家级（每 tick 一次），不是每枚水晶一次，
     * 否则 3 枚水晶会变成 3 倍回复。
     */
    private record CrystalAura(java.util.UUID player, List<java.util.UUID> crystals,
                               long expireTick, float healPerTick) {}
    private static final Map<java.util.UUID, CrystalAura> CRYSTAL_AURAS = new ConcurrentHashMap<>();

    /**
     * 末影龙：<b>末影水晶赐福</b> —— 玩家周围升起 3 枚末影水晶，原版紫色光柱射向自己，
     * 2 秒内每 tick 回复 0.5% 最大生命（合计 20%）。
     * <p>
     * 三条对着实机 jar（{@code compiledWithNeoForge_*}）核实过的事实，类路径是
     * {@code net.minecraft.world.entity.boss.enderdragon.EndCrystal}：
     * <ol>
     *   <li><b>不在 {@code world.entity.item} 包下</b>，而在 {@code world.entity.boss.enderdragon}；</li>
     *   <li>1.21.1 <b>没有</b> {@code setBeamRenderTarget(Entity)} / {@code setHeal(boolean)}
     *       （那是旧版 API），光柱是靠 {@code setBeamTarget(BlockPos)}
     *       （同步字段 {@code DATA_BEAM_TARGET}）画的，{@code EndCrystalRenderer} 读 {@code getBeamTarget()}；</li>
     *   <li>{@code tick()} 在「{@code ServerLevel} + {@code getDragonFight() != null} + 所在方块是空气」
     *       三个条件同时成立时会 {@code setBlockAndUpdate(pos, BaseFireBlock.getState(...))}
     *       ——<b>在脚下点火</b>。末地龙战期间前两个条件必然成立，所以把水晶<b>埋进地面方块一格</b>
     *       （{@code groundY - 1}），实体所在方块非空气 ⇒ 完全不走 fire 分支；
     *       观感上正好是「从地面射向你的紫色光柱」。这与本仓库先驱者激光刻意不
     *       {@code setFire(true)} 是同一类地形副作用，必须避开。</li>
     *   <li>{@code hurt()} 通过 {@code isInvulnerableTo} 之后会
     *       {@code explode(..., 6.0F, false, ExplosionInteraction.BLOCK)}
     *       ——<b>炸玩家 + 炸地形</b>。所以水晶必须完全免疫：{@code setInvulnerable(true)}
     *       挡不住创造玩家与 {@code BYPASSES_INVULNERABILITY}，最终防线是
     *       {@code BlessingCrystalMixin} 对本标记的 {@code hurt} 直接置 false。
     *       两处都要打标记，mixin 只认标记。</li>
     * </ol>
     */
    private static void spawnDragonCrystals(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) return;

        // 同一玩家的光环不叠加（避免连着触发把回血翻倍）：先收掉旧的一组
        CrystalAura existing = CRYSTAL_AURAS.remove(player.getUUID());
        if (existing != null) {
            for (java.util.UUID id : existing.crystals()) {
                Entity old = level.getEntity(id);
                if (old != null) old.discard();
                DISCARD_AT.remove(id);
            }
        }

        long expireTick = level.getServer().getTickCount() + CRYSTAL_TICKS;
        List<java.util.UUID> ids = new ArrayList<>(CRYSTAL_COUNT);
        BlockPos beamTarget = player.blockPosition().above();

        for (int i = 0; i < CRYSTAL_COUNT; i++) {
            double a = (Math.PI * 2.0 * i) / CRYSTAL_COUNT + player.getYRot() * Math.PI / 180.0;
            double cx = player.getX() + Math.cos(a) * CRYSTAL_SPREAD;
            double cz = player.getZ() + Math.sin(a) * CRYSTAL_SPREAD;
            double groundY = findGroundY(level, cx, player.getY(), cz);
            if (groundY < level.getMinBuildHeight() + 1) continue;

            EndCrystal crystal = new EndCrystal(level, cx, groundY - 1.0, cz);
            crystal.setBeamTarget(beamTarget);
            crystal.setShowBottom(false);
            // 免疫：setInvulnerable 挡住常规伤害；标记交给 BlessingCrystalMixin 兜住
            // 创造玩家与 BYPASSES_INVULNERABILITY（否则一刀就是 6.0 破坏方块的爆炸）
            crystal.setInvulnerable(true);
            com.plumejade.lensouls.util.BlessingCrystalMarker.mark(crystal);
            level.addFreshEntity(crystal);
            ids.add(crystal.getUUID());
            // 兜底：本类正常到期会收走，这里防的是玩家下线/世界卸载等中断路径
            DISCARD_AT.put(crystal.getUUID(), expireTick);
        }

        if (ids.isEmpty()) return;
        CRYSTAL_AURAS.put(player.getUUID(),
                new CrystalAura(player.getUUID(), ids, expireTick, CRYSTAL_HEAL_RATIO));
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_AMBIENT,
                SoundSource.PLAYERS, 1.0F, 1.4F);
    }

    private static void runCrystalAuras(MinecraftServer server, long now) {
        if (CRYSTAL_AURAS.isEmpty()) return;
        for (var entry : new ArrayList<>(CRYSTAL_AURAS.entrySet())) {
            CrystalAura aura = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(aura.player());
            if (player == null || !player.isAlive() || now >= aura.expireTick()) {
                for (java.util.UUID id : aura.crystals()) {
                    for (ServerLevel sl : server.getAllLevels()) {
                        Entity ent = sl.getEntity(id);
                        if (ent != null) ent.discard();
                    }
                    DISCARD_AT.remove(id);
                }
                CRYSTAL_AURAS.remove(entry.getKey());
                continue;
            }
            player.heal(player.getMaxHealth() * aura.healPerTick());
        }
    }

    // ========== 幻术师：幻影围攻 ==========

    /** 幻翼存续 tick —— 口径在 {@link SwarmPhantomFade}（5 秒 + 末尾 1.5 秒渐隐） */
    private static final int SWARM_TICKS = SwarmPhantomFade.DURATION_TICKS;
    /** 每次召唤的幻翼数 */
    private static final int SWARM_COUNT = 3;
    /** 幻翼生成时相对锚点的散布半径（格） */
    private static final double SWARM_SPREAD = 2.5D;
    /** 接触伤害判定距离（平方；2.5 格） */
    private static final double SWARM_HIT_DIST_SQR = 6.25D;
    /** 换目标的最小间隔（tick）：避免每刻重算"最近敌人" */
    private static final int SWARM_RETARGET_INTERVAL = 10;
    /** 「玩家刚打中的目标」保留时长（tick） */
    private static final int LAST_HIT_TTL = 100;

    /**
     * 本模组召唤的幻影幻翼标记（实体 persistentData 键）。
     * <p>
     * <b>为什么必须有这个标记</b>：幻翼本身是 {@code LivingEntity}，飞在玩家和目标旁边，
     * 是「最近的生物」。而空挥路径（{@code hit == null}）与目标死亡后的兜底选敌都会退化成
     * {@link #findNearestNonPlayer}，不排除自家召唤物就会选到上一批幻翼 ⇒
     * <b>新一批幻翼把上一批当敌人打</b>（实测症状）。玩家顺手打到自家幻翼时，
     * 经由 {@code LAST_HIT} 也会产生同一条误伤链。
     * <p>
     * 只用一个 persistentData 键、且所有读取点都在本类内，刻意<b>不复用</b>幻灵系统的
     * {@code lensouls:phantom_minion}：那套标记会连带触发幻灵专属的穿透伤害、
     * 目标硬拦截与击杀归属改写，语义不同，不该混用。
     */
    private static final String SWARM_PHANTOM_TAG = com.plumejade.lensouls.util.AllyFilter.SWARM_PHANTOM_TAG;

    /**
     * 是不是本模组召唤的幻翼（自家作战单位，永远不作为弹幕目标；定格选取也用它）。
     * <p>口径与标记键的定义都在 {@code AllyFilter#isSwarmPhantom}（"谁是自己人"的唯一事实来源）。
     */
    public static boolean isSwarmPhantom(net.minecraft.world.entity.Entity e) {
        return com.plumejade.lensouls.util.AllyFilter.isSwarmPhantom(e);
    }

    /** 玩家最近一次命中的敌人（触发点写入，弹幕选敌用） */
    private record LastHit(java.util.UUID entity, long tick) {}
    private static final Map<java.util.UUID, LastHit> LAST_HIT = new ConcurrentHashMap<>();

    /**
     * 记录玩家刚打中的敌人 —— 幻翼会优先扑这个目标。
     * <p>
     * 刻意用 {@code level.getGameTime()} 而不是 {@code server.getTickCount()}：后者是另一个计数器
     * （两者只差一个常量偏移，但混用就变成拿两个时钟做差）。这里必须与
     * {@link #resolveLastHit} 同源。
     */
    private static void rememberHit(ServerPlayer player, LivingEntity hit) {
        if (hit == null || hit instanceof net.minecraft.world.entity.player.Player) return;
        // 自家召唤的幻翼不算「玩家新打到的敌人」，否则打到自家幻翼会让整队掉头互殴
        if (isSwarmPhantom(hit)) return;
        if (com.plumejade.lensouls.util.AllyFilter.isAssistConstruct(hit)) return;
        LAST_HIT.put(player.getUUID(), new LastHit(hit.getUUID(), player.level().getGameTime()));
    }

    /** 解出玩家刚打中且仍然有效的目标（超时/已死/换维度/变玩家/是自家召唤物都视为无效） */
    private static LivingEntity resolveLastHit(ServerPlayer player, ServerLevel level) {
        LastHit lh = LAST_HIT.get(player.getUUID());
        if (lh == null) return null;
        if (level.getGameTime() - lh.tick() > LAST_HIT_TTL) {
            LAST_HIT.remove(player.getUUID());
            return null;
        }
        if (level.getEntity(lh.entity()) instanceof LivingEntity le && le.isAlive()
                && !(le instanceof net.minecraft.world.entity.player.Player)
                && !isSwarmPhantom(le)) {
            return le;
        }
        return null;
    }

    private record SwarmPhantom(ServerLevel level, java.util.UUID phantom, java.util.UUID caster,
                                java.util.UUID anchor, long expireTick, float dmg, long nextRetarget) {}
    private static final List<SwarmPhantom> SWARM_PHANTOMS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 幻术师：<b>幻影围攻</b> —— 在被攻击的敌人身边召出 3 只幻翼，幻翼锁定该敌人<b>主动追击</b>，
     * 接触即造成等同攻击面板的伤害。玩家中途打到新目标时，幻翼会改扑新目标。
     * <p>
     * 「只打敌人、不打玩家」是对着 {@code Phantom.registerGoals()} 的实机字节码
     * （{@code javap -c} 核实）做的 —— 原版注册的是：
     * <ul>
     *   <li>{@code targetSelector}：{@code PhantomAttackPlayerTargetGoal}（只找玩家）；</li>
     *   <li>{@code goalSelector}：{@code PhantomAttackStrategyGoal} /
     *       {@code PhantomSweepAttackGoal} / {@code PhantomCircleAroundAnchorGoal}。</li>
     * </ul>
     * <b>两个都要摘</b>：{@code PhantomSweepAttackGoal} 是 <b>AOE</b>
     * （{@code getEntitiesOfClass(LivingEntity, getBoundingBox().inflate(d), …)} 后对每个目标
     * {@code doHurtTarget}），留着就会扫到玩家。只摘 targetSelector 是不够的。
     * <p>
     * {@code Mob.targetSelector} / {@code goalSelector} 都是 {@code public final}（不是 protected），
     * {@code GoalSelector.removeAllGoals(Predicate)} 也只有这一个重载 ——
     * 两者都不需要 mixin，也不需要反射。
     * <p>
     * <b>飞行与姿态：必须喂 {@code moveTargetPoint}，交给原版控制器</b>
     * （对着实机字节码逐条核实，前两版都在这里翻过车）：
     * <ol>
     *   <li>寻路（{@code getNavigation().moveTo(...)}）<b>完全无效</b>：幻翼的 {@code moveControl}
     *       是它自己的 {@code Phantom$PhantomMoveControl}，不是 {@code FlyingMob} 默认的
     *       {@code FlyingMoveControl}；而 {@code FlyingMoveControl} 的 MOVE_TO 分支只给
     *       {@code setYya} 赋值、从不设 {@code setZza}，加上 {@code FlyingMob.travel} 的
     *       {@code moveRelative} 倍率写死 {@code 0.02f}（不读 {@code getSpeed()}），
     *       水平位移恒为 0 —— 所以第一版「摘 goal + moveTo」的幻翼只是原地上下晃。</li>
     *   <li>自己写 {@code setDeltaMovement} <b>同样不行</b>：{@code PhantomMoveControl.tick()}
     *       是<b>唯一</b>设置 {@code setYRot}/{@code setXRot} 的地方，绕过它就等于「delta 指向目标、
     *       而偏航俯仰永远停在生成时的 0」——实机表现正是「固定朝向、身子朝上」。</li>
     *   <li>正解：只写私有的 {@code moveTargetPoint}（见 {@code PhantomMoveTargetAccessor}），
     *       由原版控制器自己算偏航（{@code atan2(dz,dx)}，每 tick 转 4°）与俯仰、
     *       并以 {@code speed}（航向对齐后向 1.8 加速 = 俯冲）合成速度。姿态因此天然正确。</li>
     *   <li>{@code PhantomLookControl.tick()} 是<b>空实现</b>，{@code setLookAt(...)} 对幻翼毫无作用，
     *       不要再用它控制朝向。</li>
     * </ol>
     * <p>
     * 也<b>不用 setNoAi(true)</b>：那会连 {@code serverAiStep()} 一起停掉，而
     * {@code moveControl.tick()} / {@code lookControl.tick()} 正在其中——幻翼就彻底不动了。
     * 保留 AI、只摘 goal，正好让原版控制器接管飞行。
     * <p>
     * <b>范围限定</b>：只对这里 {@code addFreshEntity} 的那 3 只动手 —— 摘的是这两个实体实例的
     * goal，accessor 只写我们自己的目标点；全世界的幻术师、幻翼、自然生成物一律不受影响。
     * <p>
     * 伤害走 {@code playerAttack}（<b>近战</b>伤害类型），因此不会被
     * {@code onRangedHit} 的 {@code RangedAttackHelper.isRanged} 认成远程 ⇒
     * 不会触发「弹幕命中 → 再掷弹幕」的自增殖，不需要额外打 {@code photo_proj} 标记。
     */
    private static void spawnPhantomSwarm(ServerPlayer player, LivingEntity hit) {
        LivingEntity anchor = hit != null ? hit : findNearestNonPlayer(player, 16.0);
        if (anchor == null || anchor instanceof net.minecraft.world.entity.player.Player) return;
        // 自家召唤物不是敌人：打到自家幻翼（或它恰好是"最近生物"）时不要再召一批去打它
        if (isSwarmPhantom(anchor)) return;
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null) return;

        float dmg = playerFinalDamage(player);
        long now = level.getServer().getTickCount();
        long expireTick = now + SWARM_TICKS;

        for (int i = 0; i < SWARM_COUNT; i++) {
            Phantom ph = EntityType.PHANTOM.create(level);
            if (ph == null) return;
            double a = (Math.PI * 2.0 * i) / SWARM_COUNT;
            ph.setPos(anchor.getX() + Math.cos(a) * SWARM_SPREAD,
                    anchor.getY() + 2.2 + i * 0.3,
                    anchor.getZ() + Math.sin(a) * SWARM_SPREAD);
            ph.setPhantomSize(2 + i);
            // 不调 finalizeSpawn：1.21.1 它的实现末尾会 setPhantomSize(0)（javap 核实），
            // 会把上面显式设的体型抹掉；幻翼又无装备，本来也不需要它。
            ph.targetSelector.removeAllGoals(g -> true);
            ph.goalSelector.removeAllGoals(g -> true);
            // 打上「自家召唤物」标记：必须在 addFreshEntity 之前，否则入世那一刻可能已被别处选敌扫到
            ph.getPersistentData().putBoolean(SWARM_PHANTOM_TAG, true);
            // 无敌：与虚影幻灵同口径（**原版无敌标签**，不是抗性、也不是事件层过滤）。
            //   ① 免索敌：LivingEntity.canBeSeenAsEnemy() = !isInvulnerable() && … ⇒ 怪物不再盯幻翼；
            //   ② 砍不到：Entity.isInvulnerableTo 挡掉非 BYPASSES_INVULNERABILITY、非创造玩家的伤害——
            //      玩家近战/横扫/弓箭/照片弹幕/召唤物代打，以及「幻翼互殴」（伤害源虽由 caster 发起，
            //      但幻翼自身已无敌）全在这一层挡掉，不再需要事件层过滤；
            //   ③ 创造模式玩家仍被 isInvulnerableTo 放行 ⇒ 由 PhantomDamageHandler#onIncomingDamage 兜底。
            ph.setInvulnerable(true);
            // 时效（服务端 persistentData）同样在入世前写好：玩家开始追踪时据此下发余命
            SwarmPhantomFade.mark(ph, expireTick);
            // 首帧就要有目标点，否则 moveTargetPoint 还是默认的 Vec3.ZERO，
            // 第一个 tick 会朝世界原点扑一下
            setSwarmTargetPoint(ph, anchor);
            level.addFreshEntity(ph);
            SWARM_PHANTOMS.add(new SwarmPhantom(level, ph.getUUID(), player.getUUID(),
                    anchor.getUUID(), expireTick, dmg, now));
            DISCARD_AT.put(ph.getUUID(), expireTick);
        }
    }

    /**
     * 玩家「开始追踪」某实体时，给自家幻翼补发一份余命。
     * <p>
     * <b>为什么改成发余命而不是写同步数据槽</b>：旧做法用
     * {@code SynchedEntityData.defineId(Phantom.class, …)} 往原版幻翼的数据表里加槽，
     * 而 {@code defineId} 的 id 是按「谁先初始化」现场分配的 —— 本类（服务端）
     * 与渲染层（客户端）首次触碰 {@code SwarmPhantomFade} 的时机不同，同一个槽两端 id 不一致，
     * 客户端收到「id=17 的 Integer」却发现本地是 Boolean，直接报
     * {@code Invalid entity data item type for field … on entity Phantom} 掉线（实测崩溃）。
     * <p>
     * 现在只在追踪建立时发一次余命，渐隐由客户端本地按时间推算：
     * 区块重载重建实体、后加入、传送过去，都会重新触发开始追踪 ⇒ 拿到的永远是当前余命。
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof Phantom phantom)) return;
        if (!(event.getEntity() instanceof ServerPlayer viewer)) return;
        if (!SwarmPhantomFade.isSwarm(phantom)) return;

        MinecraftServer server = phantom.level().getServer();
        if (server == null) return;
        long remaining = SwarmPhantomFade.expireTick(phantom) - server.getTickCount();
        if (remaining <= 0) return;   // 已到期：交给 runPhantomSwarms 的收回流程
        PacketDistributor.sendToPlayer(viewer, new SwarmPhantomPacket(phantom.getId(), (int) remaining));
    }

    private static void runPhantomSwarms(MinecraftServer server, long now) {
        if (SWARM_PHANTOMS.isEmpty()) return;
        for (int i = SWARM_PHANTOMS.size() - 1; i >= 0; i--) {
            SwarmPhantom s = SWARM_PHANTOMS.get(i);
            ServerPlayer caster = server.getPlayerList().getPlayer(s.caster());
            Phantom ph = s.level().getEntity(s.phantom()) instanceof Phantom p ? p : null;

            // 摘了 goal 的幻翼不会自然消失，成活与到期一律由本队列收
            if (ph == null || !ph.isAlive() || caster == null || !caster.isAlive() || now >= s.expireTick()) {
                if (ph != null) ph.discard();
                DISCARD_AT.remove(s.phantom());
                SWARM_PHANTOMS.remove(i);
                continue;
            }

            boolean stale = now >= s.nextRetarget();
            LivingEntity anchor;
            if (stale) {
                // 优先级：玩家刚打中的目标 > 原锚点（还活着）> 附近最近的敌人 > 收回
                anchor = resolveLastHit(caster, s.level());
                if (anchor == null) {
                    LivingEntity recorded = isSelectableAnchor(s.level().getEntity(s.anchor()))
                            ? (LivingEntity) s.level().getEntity(s.anchor()) : null;
                    anchor = recorded != null ? recorded : findNearestNonPlayer(caster, 16.0);
                }
                if (anchor == null) {
                    ph.discard();
                    DISCARD_AT.remove(s.phantom());
                    SWARM_PHANTOMS.remove(i);
                    continue;
                }
                SWARM_PHANTOMS.set(i, new SwarmPhantom(s.level(), s.phantom(), s.caster(),
                        anchor.getUUID(), s.expireTick(), s.dmg(), now + SWARM_RETARGET_INTERVAL));
            } else {
                // 两次重算之间沿用缓存锚点；它一旦失效就本轮立刻重算
                net.minecraft.world.entity.Entity cached = s.level().getEntity(s.anchor());
                if (!isSelectableAnchor(cached)) {
                    SWARM_PHANTOMS.set(i, new SwarmPhantom(s.level(), s.phantom(), s.caster(),
                            s.anchor(), s.expireTick(), s.dmg(), now));
                    continue;
                }
                anchor = (LivingEntity) cached;
            }

            if (ph.getTarget() != anchor) ph.setTarget(anchor);

            // ── 飞行：只喂目标点，位移/偏航/俯仰全部交给原版 Phantom$PhantomMoveControl ──
            // 不要在这里写 setDeltaMovement，也不要 setLookAt（PhantomLookControl.tick 是空实现）。
            setSwarmTargetPoint(ph, anchor);

            // ── 渐隐：只发一次时效 —— 余命在「玩家开始追踪」时下发（见 onStartTracking），
            //    末尾 1.5 秒的 alpha 由客户端本地按时间推算，不逐 tick 同步 ──

            if (ph.distanceToSqr(anchor) < SWARM_HIT_DIST_SQR) {
                // 最后一道闸：真到了结算这一步再确认一次锚点合法。
                // 幻翼互殴的伤害源是 playerAttack(caster)（直接实体是玩家，不是幻翼），所以伤害侧认不出
                // 「自己人打自己人」；现在幻翼带原版无敌标签（生成处 setInvulnerable(true)），
                // 互殴即使漏到这里也打不动。这里仍是主防线：少放技能、少刷音效。
                if (!isSelectableAnchor(anchor)) continue;
                anchor.invulnerableTime = 0;
                anchor.hurt(caster.damageSources().playerAttack(caster), s.dmg());
                ph.swing(InteractionHand.MAIN_HAND);
                s.level().playSound(null, ph.blockPosition(), SoundEvents.PHANTOM_AMBIENT,
                        SoundSource.NEUTRAL, 1.0F, 1.2F);
            }
        }
    }

    /**
     * 把飞行目标点写进原版幻翼的私有字段 {@code moveTargetPoint}，让
     * {@code Phantom$PhantomMoveControl} 自己换算成偏航 / 俯仰 / 速度。
     * <p>
     * 取眼睛高度 + 0.4 而不是脚底：与另外三只的生成高度、以及「扑脸」的观感一致。
     */
    private static void setSwarmTargetPoint(Phantom ph, LivingEntity anchor) {
        ((com.plumejade.lensouls.mixin.PhantomMoveTargetAccessor) (Object) ph)
                .lensouls$setMoveTargetPoint(
                        new Vec3(anchor.getX(), anchor.getEyeY() + 0.4D, anchor.getZ()));
    }

    // 幻翼的误伤保护已并入「原版无敌标签」：生成处 {@code ph.setInvulnerable(true)}；
    // 创造模式那一档由 {@code PhantomDamageHandler#onIncomingDamage} 统一兜底。
    // 原先的 onSwarmPhantomIncomingDamage / isPlayerCaused / isPlayerChain 已删除——
    // 它只挡「玩家来源」的伤害，**怪物照样能打幻翼、也照样把幻翼当索敌目标**；
    // 换成无敌标签后这两件事一起解决（且连受击闪红/击退/音效都没有）。

    /**
     * 该实体能不能当幻翼的锚点：活着、非玩家、<b>且不是本模组自己召唤的幻翼</b>。
     * <p>
     * 三处选敌（生成、重算、缓存校验）共用同一口径——分别手写判断正是第一版出现
     * 「幻翼互殴」的原因：只要漏掉任意一处，自家幻翼就会从那一处流进来。
     */
    private static boolean isSelectableAnchor(net.minecraft.world.entity.Entity e) {
        return e instanceof LivingEntity le && le.isAlive()
                && !(le instanceof net.minecraft.world.entity.player.Player)
                && !isSwarmPhantom(le);
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
            for (LivingEntity e : com.plumejade.lensouls.util.PartHitUtil.livingTargetsInBox(sc.level(), box)) {
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
        // 末影龙赐福水晶：每 tick 回血 + 到期清理（必须排在下面那道 return 之前）
        runCrystalAuras(event.getServer(), now);
        // 幻术师幻影围攻：主动追击 + 接触伤害（同样必须排在 return 之前）
        runPhantomSwarms(event.getServer(), now);
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
