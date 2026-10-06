package com.plumejade.lensouls.entity;

import com.plumejade.lensouls.util.AllyFilter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

/**
 * 幻灵防误伤 + 穿透伤害 + 召唤物效果赦免。
 * <p>
 * 防误伤：幻灵来源（借体 BOSS 本体、其召唤物、或其弹幕 owner）对玩家不造成任何伤害——
 * 在 LivingIncomingDamageEvent 阶段直接取消（含近战/接触/弹幕/召唤物），真正免伤且含击退。
 * 召唤者处于旁观者模式已天然免疫，此处额外拦截以防队友/敌队被借体 BOSS 及其召唤物波及。
 * <p>
 * 召唤物特殊效果赦免（玩家）：借体 BOSS 的 AI 会召出它自己的随从（例：利维坦的牵引、暮色雪怪首领的减速），
 * 这些随从即使不能锁定玩家，其 <b>范围/光环类特殊效果</b>仍可能落到玩家身上。因此在
 * {@link MobEffectEvent.Applicable}（NeoForge 提供 {@code getEffectSource()}，可追溯到施法实体）
 * 里，凡来源属于幻灵、目标为玩家、且效果非增益者一律 {@code DO_NOT_APPLY}；
 * 另有 {@link com.plumejade.lensouls.mixin.PhantomPushGuardMixin} 拦截物理推动/牵引。
 * <p>
 * 穿透伤害：仅借体 BOSS 本体每次命中非玩家时覆盖为固定穿透伤害
 * （按镜魂等级 1-5：20 / 36 / 42 / 70 / 74，无视护甲/伤害桶/单次上限）。
 * <p>
 * 击杀归属：幻灵本体、它召出的随从、以及它们射出的弹幕，杀死的怪都算<b>召唤者玩家</b>的击杀
 * （{@link #resolveOwnerUUID} 是归属的唯一事实来源，写入点见 {@code BossPhantomManager}）。
 * 伤害数值、穿透伤害、韧性减伤一概不变，<b>只改归属</b>。
 */
public class PhantomDamageHandler {

    private static final int[] PEN_DAMAGE = {20, 36, 42, 70, 74};

    // ---- persistentData 键 ----
    // 写入点：BossPhantomManager。**键名的唯一事实来源是 AllyFilter**，这里只做别名转发——
    // 「自己人」的标记键不许有第二份定义（见 AllyFilter 类注释：加新单位只改一处）。
    /** 借体 BOSS 本体标记（= {@link AllyFilter#PHANTOM_TAG}） */
    public static final String PHANTOM_TAG = AllyFilter.PHANTOM_TAG;
    /** 借体 BOSS 演出期间召出的随从标记（= {@link AllyFilter#PHANTOM_MINION_TAG}） */
    public static final String PHANTOM_MINION_TAG = AllyFilter.PHANTOM_MINION_TAG;
    /** 幻灵本体与随从都写入的召唤者玩家 UUID（击杀归属的事实来源） */
    public static final String PHANTOM_OWNER_TAG = "lensouls:phantom_owner";
    /** 幻灵镜魂等级 1-5，决定穿透伤害档位 */
    public static final String PHANTOM_LEVEL_TAG = "lensouls:phantom_level";

    /**
     * 实体本身是否是幻灵（借体 boss 本体 / 召唤物），用于幻灵之间不互殴的隔离判断。
     * <p>口径定义在 {@link AllyFilter#isPhantom}（「谁是自己人」的唯一事实来源）。
     */
    public static boolean isPhantomEntity(Entity e) {
        return AllyFilter.isPhantom(e);
    }

    /**
     * 递归判定实体是否属幻灵来源：本体 / 召唤物 / 弹幕 owner（供效果与推动拦截共用）。
     * <p>口径定义在 {@link AllyFilter#isPhantomSource}。
     */
    public static boolean isPhantomSource(Entity e) {
        return AllyFilter.isPhantomSource(e);
    }

    /**
     * 驯服生物（狼、猫、鹦鹉、马…）：玩家宠物，<b>不算敌人</b>。
     * <p>
     * 口径定义在 {@link AllyFilter#isTamedPet}：<b>只认 {@code isTame()}、不分是谁的宠物</b>——
     * 幻灵选敌、照片弹幕误伤保护、定格选取都共用它（队友的狗挡在弹道上与队友本人一样算误伤）。
     */
    public static boolean isTamedPet(Entity e) {
        return AllyFilter.isTamedPet(e);
    }

    /**
     * 该伤害的召唤者玩家 UUID——<b>击杀归属的唯一事实来源</b>。
     * <p>
     * 沿「直接实体 → 真实攻击者」两路查，各自再沿 {@code Projectile.getOwner()} 链递归，
     * 因为幻灵的伤害有三种落法：本体近战（直接实体=本体）、本体远程（直接实体=它射出的弹）、
     * 随从（直接实体=随从，或随从射出的弹）。只查直接实体会漏掉后两种。
     * <p>
     * {@code owner} 为 null 表示「不是幻灵造成的」，调用方据此原样处理。
     */
    public static UUID resolveOwnerUUID(DamageSource source) {
        if (source == null) return null;
        UUID id = ownerOfChain(source.getDirectEntity());
        if (id != null) return id;
        return ownerOfChain(source.getEntity());
    }

    /** 同 {@link #resolveOwnerUUID(DamageSource)}，但从任意实体出发（供只拿到实体的调用方使用） */
    public static UUID resolveOwnerUUID(Entity start) {
        return ownerOfChain(start);
    }

    /** 沿弹射物 owner 链向上找幻灵标记实体；限深防自引用（幻灵弹 owner 指向自己）导致的死循环 */
    private static UUID ownerOfChain(Entity start) {
        Entity e = start;
        for (int depth = 0; e != null && depth < 8; depth++) {
            CompoundTag tag = e.getPersistentData();
            if (tag.getBoolean(PHANTOM_TAG) || tag.getBoolean(PHANTOM_MINION_TAG)) {
                return tag.hasUUID(PHANTOM_OWNER_TAG) ? tag.getUUID(PHANTOM_OWNER_TAG) : null;
            }
            e = e instanceof Projectile proj ? proj.getOwner() : null;
        }
        return null;
    }

    /** 解析召唤者并取到在线的 {@link ServerPlayer}（离线＝幻灵残留，归属无处可落） */
    public static ServerPlayer resolveOwnerPlayer(DamageSource source) {
        UUID id = resolveOwnerUUID(source);
        if (id == null) return null;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(id);
    }

    /**
     * 把幻灵造成的死亡事件的 {@code DamageSource} 改写成「召唤者玩家造成」，其余原样保留。
     * <p>
     * 只换<b>造成者</b>（{@code getEntity()}），<b>直接实体</b>（{@code getDirectEntity()}）保持幻灵/
     * 幻灵的弹原样：自家那套读直接实体的判定（{@code isSelfHit}、{@code isBarrageDamage}、
     * 穿透伤害档位、伤害类型标签）因此完全不受影响。
     * <p>
     * <b>为什么非改不可</b>：1.21.1 的 {@code DamageSource} 是不可变的，而 NeoForge 既没给
     * {@code LivingDamageEvent.Pre} 提供换 source 的口子（{@code DamageContainer.source} 是
     * {@code final} 无 setter）、也已经移除了 {@code LivingAttackEvent}，所以事件里根本改不动它。
     * 只能 mixin {@code CommonHooks.onLivingDeath} 在构造事件时换。
     * <p>
     * <b>为什么只在死亡时换</b>：击杀类检测（FTB Quests 走 Architectury 的
     * {@code EntityEvent.LIVING_DEATH}、判 {@code source.getEntity() instanceof ServerPlayer}）
     * 全都只在这一刻取 source。只要在这换，击杀就算玩家的；
     * 而 {@code LivingDamageEvent} 阶段拿到的仍是原始 source，元素活性、命中率掷骰、
     * 韧性减伤、口哨 -80%、弹幕远程命中触发等十几处「玩家造成」的判定一律维持原样。
     * <p>
     * 唯一代价：「累计造成 N 点伤害」类的任务不统计幻灵伤害（击杀类照常）。
     * <p>
     * 非幻灵来源、受害者是玩家、召唤者已离线、或本来就是该玩家 → 原样返回。
     */
    public static DamageSource creditOwner(DamageSource source, LivingEntity victim) {
        if (source == null || victim == null) return source;
        if (victim instanceof Player) return source;   // 不改玩家的死亡归属
        ServerPlayer owner = resolveOwnerPlayer(source);
        if (owner == null || source.getEntity() == owner) return source;
        return new DamageSource(source.typeHolder(), source.getDirectEntity(), owner,
                source.getSourcePosition());
    }

    /**
     * 幻灵击杀的<b>掉落与经验</b>归属玩家。
     * <p>
     * 与 {@link #creditOwner} 是同一件事的两半，缺一不可：那边改的是事件携带的
     * {@code DamageSource}（给击杀类检测看），这边补的是实体身上的 {@code lastHurtByPlayer}
     * （给掉落/经验看）——后者在 {@code actuallyHurt} 阶段就按<b>原始</b> source 算过了
     * （那时造成者是幻灵，值为 null），改写事件 source 追溯不到它。
     * <p>
     * 掉落与经验的<b>真正判定源</b>正是 {@code lastHurtByPlayer}/{@code lastHurtByPlayerTime}——
     * {@code LivingEntity.die} 的顺序是 {@code CommonHooks.onLivingDeath(→ LivingDeathEvent)} →
     * {@code getKillCredit()} → {@code dropAllDeathLoot()}（用 {@code lastHurtByPlayerTime > 0} 与
     * {@code LAST_DAMAGE_PLAYER}）→ {@code dropExperience()}（同样查 {@code lastHurtByPlayer}），
     * 全程不跨 tick。
     * <p>
     * ⇒ 在本事件里 {@code setLastHurtByPlayer} 来得及生效，且恰好把击杀分算给玩家：
     * 战利品（含 {@code LAST_DAMAGE_PLAYER} 与玩家幸运）、经验球、击杀分（{@code MOB_KILLS}）、
     * {@code LivingDropsEvent#isRecentlyHit()} 全部按玩家算。
     * <p>
     * {@code setLastHurtByPlayer} 会把计时器设成 {@code tickCount} 而非原版的 100，
     * 但死亡当刻就被消费、中间不 tick，实际无影响——不需要为此加 accessor mixin。
     * <p>
     * 事件里的 source 已被 mixin 换过（造成者是玩家），但直接实体仍是幻灵，
     * 所以 {@link #resolveOwnerPlayer} 照旧从直接实体解析，不会失效。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) return;   // 不改玩家的死亡归属
        if (isPhantomEntity(victim)) return;    // 幻灵互殴（本就已被防住），不再叠一层

        ServerPlayer owner = resolveOwnerPlayer(event.getSource());
        if (owner == null || owner == victim) return;

        victim.setLastHurtByPlayer(owner);
        victim.setLastHurtByMob(owner);
    }

    /**
     * 两层保护（顺序就是原版 {@code LivingEntity.hurt} 的判定顺序，已用 {@code javap -c} 核过：
     * {@code isInvulnerableTo} 在第 2 条指令，{@code CommonHooks.onEntityIncomingDamage} 在第 81 条）。
     *
     * <ol>
     *   <li><b>自家召唤物无敌</b>（幻灵 + 幻翼）：两者都带原版无敌标签——幻灵在
     *       {@code BossPhantomManager.startBorrowedEntity}，幻翼在
     *       {@code BossPhotoProjHelper} 的生成处，各调用一次 {@code setInvulnerable(true)}。
     *       常规伤害在 {@code isInvulnerableTo} 就被挡掉，<b>本事件根本不会触发</b>
     *       ——这也正是"连受击闪红/击退/音效都没有"的原因。能进到本方法的只有两类：
     *       <b>创造模式玩家</b>（{@code isInvulnerableTo} 里 {@code !source.isCreativePlayer()} 是放行条件）
     *       与 {@code BYPASSES_INVULNERABILITY}（如 {@code /kill}、虚空）。前者在这里补一刀取消，
     *       后者<b>刻意放行</b>，留一条管理员/环境的后路（幻灵/幻翼本来就都有时效，到点自会回收）。</li>
     *   <li><b>幻灵来源不伤玩家</b>：原始实体或真实攻击者（含弹幕 owner）属幻灵时，
     *       对玩家的伤害全免；幻灵之间也不互殴。</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getAmount() <= 0f) return;

        // ① 自家召唤物（幻灵 + 幻翼）：原版无敌标签已挡掉常规伤害，
        //    这里只补它放行的「创造模式玩家挥砍」；BYPASS 类刻意放行留后路
        if (AllyFilter.isOwnSummon(event.getEntity())) {
            if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                event.setCanceled(true);
            }
            return;
        }

        // ② 幻灵来源对玩家免伤
        if (!(event.getEntity() instanceof Player)) return;
        if (isPhantomDamageSource(event.getSource())) {
            event.setCanceled(true);
        }
    }

    /**
     * 召唤物特殊效果对玩家赦免：来源属于幻灵、目标为玩家、且效果不是增益 → 不施加。
     * <p>
     * 覆盖「减速 / 牵引 / 虚弱 / 中毒」等一切药水效果形态的特殊效果；增益效果（若有）放行。
     * 来源无法归属（{@code getEffectSource()} 为 null，例如无主范围云）时不拦截，避免误伤正常玩法。
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Player)) return;
        Entity source = event.getEffectSource();
        if (source == null || !isPhantomSource(source)) return;
        if (event.getEffectInstance() != null && event.getEffectInstance().getEffect().value().isBeneficial()) {
            return;
        }
        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    /** 递归判定伤害来源是否属幻灵：同时查直接来源与真实攻击者（getEntity），覆盖水花/弹幕 attrib 到 boss 的情形 */
    private static boolean isPhantomDamageSource(DamageSource src) {
        return isPhantomSource(src.getDirectEntity()) || isPhantomSource(src.getEntity());
    }

    /** Goety 式：幻灵/召唤物永不把玩家或另一只幻灵设为目标（硬拦截 AI 锁目标，根治“虚灵打我”与虚灵互殴） */
    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (!(entity instanceof Mob mob)) return;
        if (!isPhantomEntity(mob)) return;
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget == null) return;
        if (newTarget instanceof Player || isPhantomEntity(newTarget)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getOriginalDamage() <= 0f) return;

        Entity attacker = event.getSource().getDirectEntity();
        if (attacker == null) return;

        // 仅借体 BOSS 本体穿透；召唤物按正常伤害结算
        if (!attacker.getPersistentData().getBoolean(PHANTOM_TAG)) return;

        // 玩家已在 LivingIncomingDamageEvent 拦截，此处仅处理非玩家
        if (event.getEntity() instanceof Player) return;

        int level = attacker.getPersistentData().getInt(PHANTOM_LEVEL_TAG);
        if (level < 1 || level > 5) level = 1;
        event.setNewDamage(PEN_DAMAGE[level - 1]);
    }
}
