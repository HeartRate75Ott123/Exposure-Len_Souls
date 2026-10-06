package com.plumejade.lensouls.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>「谁是自己人」的唯一事实来源</b>——所有友方/敌我判定必须先在这里找分类，不允许再各写一份。
 *
 * <p><b>为什么要有这个类</b>：这些判定曾经散在五处以上——
 * {@code PhantomDamageHandler.isTamedPet/isPhantomEntity}（驯服宠物、幻灵标记）、
 * {@code BossPhotoProjHelper.isSwarmPhantom}（幻翼标记）、
 * {@code PhotoTargetFilter.isIgnored}（gytrinket 无人机/蜂群/僚机，<b>该类已并入本类</b>）、
 * {@code GunBulletEntity.isFriendlyTarget}（结盟/同乘/归属构造体），
 * 外加各调用点自己写的 {@code instanceof Player}。结果就是<b>新增一种自家单位时必然漏掉某一处</b>
 * （真实发生过：gytrinket 无人机漏进弹幕选敌、幻影幻翼差点被定格）。
 *
 * <p><b>怎么用（两层，别再混）</b>：
 * <ol>
 *   <li><b>分类</b>（{@code isPlayer / isTamedPet / isPhantom / isSwarmPhantom / isOwnSummon /
 *       isAssistConstruct}）——只回答"属不属于这一类"，定义全在本类；</li>
 *   <li><b>大过滤</b> {@link #isFriendly}（类别 + 同乘）与它的补集 {@link #isEnemyOf}（可打目标）；
 *       <b>所有"友方/敌我"判定一律走这两个</b>，不要在调用点手写组合。
 *       {@link #isFriendlyUnit} 是其中的"类别层"（一元、无视角），供上面两者内部使用，
 *       也可以直接用于确实拿不到视角的场合。</li>
 * </ol>
 *
 * <p><b>友方口径（2026-10 统一，取代先前的"免伤/定格/选敌/枪械"四档）</b>：
 * <pre>
 * isFriendlyUnit = 玩家 ‖ 任意驯服宠物 ‖ 自家召唤物（幻灵 + 幻翼）‖ 任意第三方辅助作战单位
 * isFriendly(viewer, target) = isFriendlyUnit(target) ‖ 同乘(viewer, target)   ← 大过滤
 * </pre>
 * 消费方与各自的视角：
 * <table border="1">
 *   <tr><th>消费方</th><th>调用</th><th>视角 viewer</th></tr>
 *   <tr><td>照片弹幕误伤免伤（{@code PhotoProjSafetyHandler}）</td><td>{@code isFriendly}</td><td>弹幕发射者</td></tr>
 *   <tr><td>时间定格目标（{@code PhotoTargets.freezable}）</td><td>{@code isFriendly}</td><td>拍摄者</td></tr>
 *   <tr><td>弹幕选敌（{@code BossPhotoProjHelper.findNearestNonPlayer}）</td><td>{@code isEnemyOf}</td><td>玩家自己</td></tr>
 *   <tr><td>次元枪子弹（{@code GunBulletEntity.canHitEntity}）</td><td>{@code isFriendly}</td><td>子弹 owner</td></tr>
 * </table>
 * <b>刻意不区分归属</b>：自己/队友/路人的宠物与构造体一律算友方（代价：PvP 下敌方构造体也打不到，
 * 属用户明确接受的取舍）。
 * <p>
 * <b>刻意不含"结盟"（{@code Entity#isAlliedTo}，即计分板同队）</b>——原版语义就是
 * {@code Team.isAlliedTo(t) = (t == 自己所在的那个队伍)}，且没有队伍时连自己都不算同盟。
 * 并进基础判定的三个害处：① 它是<b>二元相对</b>判定，塞进一元判定就得给所有调用点强加视角
 * （弹幕免伤那条链路本来就没有可靠视角）；② 粒度是"队伍"而非"单位类别"，一句 {@code /team join}
 * 就能整体改变"谁算友方"；③ 它<b>可被任何模组覆写</b>、没组队时恒 false ⇒ 判定权外移、行为不可复现。
 * 真需要按队伍保护，就在那一处单独写一行并把视角写清楚。
 *
 * <p><b>服务端注意</b>：{@code getPersistentData()}（幻灵/幻翼标记）<b>不会同步到客户端</b>，
 * 所以这些标记判定只能在服务端用；客户端要知道"这实体是不是自家幻灵"走
 * {@code ClientPhantomHandler} 的包同步 id 集合，不是本类。
 * gytrinket 那类第三方构造体按**类名**判定，两端都能用。
 */
public final class AllyFilter {

    // ==================== 标记键（唯一事实来源） ====================

    /** 幻灵本体（借体 boss 实体被打了这个标记） */
    public static final String PHANTOM_TAG = "lensouls:phantom";
    /** 幻灵召出的随从 */
    public static final String PHANTOM_MINION_TAG = "lensouls:phantom_minion";
    /** 幻影幻翼（照片能力召唤的自家作战单位） */
    public static final String SWARM_PHANTOM_TAG = "lensouls:photo_swarm_phantom";

    /** gytrinket 作战单位的公共包名前缀（drone / swarm / wingman 都在其下） */
    private static final String GYTRINKET_CONSTRUCT_PACKAGE = "com.gytrinket.gytrinket.core.entity.construct.";

    /** 实体类 → 是否第三方辅助作战单位（同一个类只做一次字符串比较） */
    private static final Map<Class<?>, Boolean> ASSIST_CACHE = new ConcurrentHashMap<>();
    private static volatile Boolean gytrinketLoaded;

    private AllyFilter() {
    }

    // ==================== 分类 ====================

    /** 玩家（所有玩家；要"某个玩家"请自己比较引用）。 */
    public static boolean isPlayer(Entity e) {
        return e instanceof Player;
    }

    /**
     * 驯服宠物（狼/猫/鹦鹉/马…），<b>只认 {@code isTame()}、不分是谁的</b>。
     * <p>
     * 口径由来：弹幕免伤与选敌都按「所有驯服生物一律免疫/不作为目标」——
     * 队友的宠物挡在弹道上与队友本人一样属于误伤。
     */
    public static boolean isTamedPet(Entity e) {
        return e instanceof TamableAnimal tame && tame.isTame();
    }

    /** 虚影幻灵：借体 boss 本体或它召出的随从（标记键见 {@link #PHANTOM_TAG}）。 */
    public static boolean isPhantom(Entity e) {
        if (e == null) return false;
        var tag = e.getPersistentData();
        return tag.getBoolean(PHANTOM_TAG) || tag.getBoolean(PHANTOM_MINION_TAG);
    }

    /**
     * 该实体是否属幻灵来源（本体 / 随从 / <b>其弹幕的 owner 链</b>）。
     * 用于"这伤害是不是幻灵打的"这类归属判定，与 {@link #isPhantom} 的纯实体判定区分开。
     */
    public static boolean isPhantomSource(Entity e) {
        if (e == null) return false;
        if (isPhantom(e)) return true;
        return e instanceof Projectile proj && isPhantomSource(proj.getOwner());
    }

    /** 幻影幻翼：照片能力召唤的自家作战单位（飞在玩家旁边，不能当敌人、也不该被自己定住）。 */
    public static boolean isSwarmPhantom(Entity e) {
        return e != null && e.getPersistentData().getBoolean(SWARM_PHANTOM_TAG);
    }

    /** 自家召唤物：幻灵（含随从）+ 幻影幻翼。新增自家召唤物时<b>只需在这里加一条</b>。 */
    public static boolean isOwnSummon(Entity e) {
        return isPhantom(e) || isSwarmPhantom(e);
    }

    /**
     * 第三方辅助作战单位（gytrinket 的无人机 / 蜂群 / 僚机等 construct）。
     * <p>
     * 它们在对方模组里的公共父类是
     * {@code com.gytrinket.gytrinket.core.entity.construct.AbstractConstructEntity extends PathfinderMob}
     * ——<b>是 LivingEntity</b>，所以不能靠"非生物"筛掉，必须显式按类忽略。
     * 用<b>包名前缀</b>而不是逐个类名：对方以后再加第四种 construct 也自动覆盖；
     * 结果按类缓存，模组没装时直接短路。
     * <p>
     * 忽略后调用方会继续看下一个候选（本类只回答"是不是"，不做选取）。
     */
    public static boolean isAssistConstruct(Entity entity) {
        if (entity == null) return false;
        if (!gytrinketPresent()) return false;
        return ASSIST_CACHE.computeIfAbsent(entity.getClass(),
                cls -> cls.getName().startsWith(GYTRINKET_CONSTRUCT_PACKAGE));
    }

    /**
     * 同乘关系（骑或被骑）——归入大过滤：不长眼地打/锁/牵引"自己正骑着的东西"永远是误伤。
     * <p>视角为空时恒 false（安全降级）。
     */
    public static boolean isVehicleRelated(Entity a, Entity b) {
        return a != null && b != null && (a.getVehicle() == b || b.getVehicle() == a);
    }

    // ==================== 大过滤（所有友方/敌我判定都走这两个） ====================

    /**
     * <b>类别层</b>：玩家 ‖ 任意驯服宠物 ‖ 自家召唤物（幻灵 + 幻翼）‖ 任意第三方辅助作战单位。
     * <p>
     * 一元判定、不需要视角，所以四种消费方<b>共用同一份类别定义</b>；
     * 「新增一种自家单位或辅助单位」只需在分类里加一条，四个功能自动同步。
     * <p>
     * <b>刻意不区分归属</b>：不管自己、队友还是路人的宠物与构造体，一律算友方。
     * 已知代价：PvP 下敌方构造体同样打不到（用户明确接受的取舍）。
     * <p>
     * 需要"视角"的调用请用 {@link #isFriendly}（它在本层之上再加同乘）。
     */
    public static boolean isFriendlyUnit(Entity e) {
        return isPlayer(e) || isTamedPet(e) || isOwnSummon(e) || isAssistConstruct(e);
    }

    /**
     * <b>大过滤（友方判定）</b> = {@link #isFriendlyUnit 类别} ‖ {@link #isVehicleRelated 同乘}。
     * <p>
     * {@code viewer} = "站在谁的角度"：弹幕免伤用弹幕发射者、定格用拍摄者、选敌用玩家自己、
     * 次元枪子弹用子弹 owner。可为 {@code null}（= 只看类别，同乘那档自动失效）。
     * <p>
     * <b>结盟（计分板同队）刻意不在内</b>，理由见类注释（判定权外移 / 行为不可复现）。
     */
    public static boolean isFriendly(Entity viewer, Entity target) {
        if (target == null) return false;
        return isFriendlyUnit(target) || isVehicleRelated(viewer, target);
    }

    /**
     * 可打的敌人 = 非友方（见 {@link #isFriendly}）、且不是 {@code viewer} 自己。
     * <b>不含存活判定</b>（调用方按需再查 {@code isAlive()}）。
     */
    public static boolean isEnemyOf(Entity viewer, Entity target) {
        if (target == null || target == viewer) return false;
        return !isFriendly(viewer, target);
    }

    private static boolean gytrinketPresent() {
        Boolean cached = gytrinketLoaded;
        if (cached == null) {
            cached = ModList.get().isLoaded("gytrinket");
            gytrinketLoaded = cached;
        }
        return cached;
    }
}
