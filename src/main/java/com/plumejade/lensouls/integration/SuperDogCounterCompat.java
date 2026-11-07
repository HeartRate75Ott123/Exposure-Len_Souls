package com.plumejade.lensouls.integration;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * 与 <b>superdog（流光狼）</b> 的联动：⑤ 铁序·平势 的「反击」借用它的<b>球面突击技能引擎</b>
 * （冲刺位移 + 拖尾 + 爆炸特效 + 路径群伤），但<b>数值全是我们自己的</b>，也不读它的
 * {@code [wildSkills.<mob>]} 配置、不要求目标生物在它的登记表里。
 * <p>
 * <b>怎么做到「任意生物 + 我们的数值」</b>：它的引擎入口
 * {@code AssaultManager.tryCounterAttack(Mob, LivingEntity, EntitySkillProfile, float chance, boolean force)}
 * 只读传入 {@code profile} 的几个 getter，从不校验 {@code profile.type()} 与生物的对应关系。
 * 所以这里用 {@link ModConfigSpec.Builder} 现造一组配置值（把默认值烤成我们想要的数字），
 * 再 {@code new EntitySkillProfile(...)} 一个「任意生物都匹配」的档案交给它 —— 技能引擎照跑，
 * 段数 / 伤害系数 / 冷却 / 复制体全部由我们决定。
 * <p>
 * 伤害公式（它的引擎内部，逐字保留）：{@code 生物 ATTACK_DAMAGE × 系数}，走
 * {@code hurtIgnoringInvulnerability}（清无敌帧）+ 它自己的伤害类型（{@code superdog:assault_path} 等，
 * 自带 bypasses_armor / enchantments / resistance 标签）。
 * <ul>
 *   <li>主目标：{@code ATTACK_DAMAGE × dashFraction}（每段冲刺一次）</li>
 *   <li>路径群伤：{@code ATTACK_DAMAGE × pathFraction}（冲刺路径半径内的其它生物）</li>
 *   <li>生物没有 {@code ATTACK_DAMAGE} 属性时用 fallback（传负值 = 兜底 1.0）</li>
 * </ul>
 * <p>
 * <b>为什么不用它的登记表</b>：1.0.5 只给 wolf / zombie / husk / cat / ocelot 五种生物配了档案，
 * 而平势的触发条件是「目标生命 ≥ 你的 2 倍」，正常血量下够格的基本是 BOSS —— 用它的档案等于永远不触发。
 * 现造档案后任意 {@link Mob} 都能吃到这套特效。
 * <p>
 * <b>复制体恒为 0</b>：它会 {@code EntityType.create} 复制本体，复制 BOSS 属于灾难，所以本联动不生成复制体。
 * <p>
 * <b>反射而非编译依赖</b>：superdog 是整合包里的可选模组。整条链路 {@code catch (Throwable)} 兜底，
 * 一旦反射签名对不上就停用并只 WARN 一次，绝不影响伤害结算主链；调用方收到 {@code false} 时退回字面反击。
 */
public final class SuperDogCounterCompat {

    /** superdog 的 mod id */
    private static final String MOD_ID = "superdog";

    /** 玩家侧反击的爆炸视觉半径（沿用它的冲刺爆炸量级） */
    private static final float FIELD_COUNTER_FX_RADIUS = 2.0f;

    /** 探针：null = 还没试过；false = 模组缺失 / 反射失败（永久停用） */
    private static Boolean ready;

    private static Method tryCounterAttack;   // AssaultManager.tryCounterAttack(Mob, LivingEntity, EntitySkillProfile, float, boolean)
    private static Method sendExplosionFX;    // NetworkHandler.sendExplosionFX(Entity, x,y,z, nx,ny,nz, radius)
    private static Method sendDashParticles;  // NetworkHandler.sendDashParticles(Entity, dx,dy,dz)
    private static Constructor<?> profileCtor; // new EntitySkillProfile(EntityType, String, Predicate, DoubleValue×4?, ...)
    private static Field cachedValueField;     // ModConfigSpec.ConfigValue.cachedValue（把默认值直接烤进去）
    /** 现造的档案（一次构造、全程复用） */
    private static Object profile;
    /** 调用期失败计数：反射已在 init 证明可用，个别生物抛错不该永久停用（连续 3 次才停） */
    private static int failures;

    private SuperDogCounterCompat() {
    }

    /** superdog 是否可用（模组在且反射成功）。只用于日志/调试。 */
    public static boolean available() {
        return init();
    }

    /**
     * 让 {@code target} 对 {@code attacker} 发动一次「反击」（superdog 的球面突击技能）。
     *
     * @param dashes       冲刺段数
     * @param dashFraction 每段冲刺伤害 = 目标 ATTACK_DAMAGE × 该系数
     * @param pathFraction 路径群伤 = 目标 ATTACK_DAMAGE × 该系数
     * @param cooldownTicks 它的技能冷却（tick），冷却中受击不再触发
     * @return {@code true} = 技能已经真正开始（特效/伤害都会走它那一套）；
     *         {@code false} = 模组缺失 / 反射失败 / 它正在技能中或冷却中 —— 调用方应退回别的反击实现
     */
    public static boolean tryCounter(LivingEntity target, LivingEntity attacker,
                                     int dashes, float dashFraction, float pathFraction, int cooldownTicks) {
        if (!(target instanceof Mob mob) || attacker == null) return false;
        if (!init()) return false;
        try {
            Object started = tryCounterAttack.invoke(null, mob, attacker, ensureProfile(dashes, dashFraction,
                    pathFraction, cooldownTicks), 1.0f, false);
            return Boolean.TRUE.equals(started);
        } catch (Throwable t) {
            // 反射签名已经验证过，这里多半是某只生物调用期异常（AI/位置状态）——
            // 连续 3 次才停用，避免一只怪把整条联动永久关掉，也避免刷屏。
            if (++failures >= 3) {
                disable(t);
            } else if (LenSouls.LOGGER.isDebugEnabled()) {
                LenSouls.LOGGER.debug("[Curse] ⑤ e23 superdog 反击调用失败（第 {} 次）", failures, t);
            }
            return false;
        }
    }

    // ==================== 玩家侧反击：只播特效 ====================

    /**
     * 玩家侧（⑤e23 反转）的反击**只放特效、不跑技能引擎**。
     * <p>
     * <b>为什么不跑引擎</b>：{@code startSkill1} 会设 {@code noPhysics / noGravity} 并把实体整段位移 ——
     * 对怪物是「冲刺」，对玩家就是「强制瞬移、抢操作」，完全不实用。所以玩家侧只发两份表现包：
     * <ul>
     *   <li>目标胸口一团爆炸（{@code SuperDogExplosionFXRenderer}）；</li>
     *   <li>自身 30 颗反射盾粒子朝目标方向飞出（{@code sendDashParticles}，不改变实体位置）。</li>
     * </ul>
     * 拖尾（{@code sendTrailStart}）与突击姿势（{@code sendAssaultPose}）刻意不放：
     * 前者没有位移就没有拖尾形状（只会糊在镜头前），后者会把玩家模型掰弯。
     */
    public static void playCounterFx(ServerPlayer player, LivingEntity target) {
        if (!init() || player == null || target == null) return;
        try {
            double px = player.getX();
            double py = player.getY() + player.getBbHeight() * 0.5;
            double pz = player.getZ();
            double tx = target.getX();
            double ty = target.getY() + target.getBbHeight() * 0.5;
            double tz = target.getZ();

            double dx = tx - px;
            double dy = ty - py;
            double dz = tz - pz;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0e-4) {           // 贴脸重合：方向兜底成「向上」，粒子才不会因为零向量被客户端丢弃
                dx = 0.0;
                dy = 1.0;
                dz = 0.0;
            } else {
                dx /= len;
                dy /= len;
                dz /= len;
            }

            // 爆炸放在目标胸口，法线用「玩家 → 目标」的方向
            sendExplosionFX.invoke(null, player, tx, ty, tz, dx, dy, dz, FIELD_COUNTER_FX_RADIUS);
            // 粒子从自身朝目标飞出（纯表现，不动实体）
            sendDashParticles.invoke(null, player, dx, dy, dz);
        } catch (Throwable t) {
            if (LenSouls.LOGGER.isDebugEnabled()) {
                LenSouls.LOGGER.debug("[Curse] ⑤ e23 反击特效发送失败", t);
            }
        }
    }

    // ==================== 现造档案 ====================

    private static Object ensureProfile(int dashes, float dashFraction, float pathFraction, int cooldownTicks)
            throws Exception {
        if (profile != null) return profile;
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        Object chance = freeze(b.defineInRange("lensouls.field.chance", 1.0, 0.0, 1.0), 1.0);
        Object cooldown = freeze(b.defineInRange("lensouls.field.cooldown", cooldownTicks, 0, 24000),
                cooldownTicks);
        Object dashCount = freeze(b.defineInRange("lensouls.field.dashes", dashes, 1, 100), dashes);
        Object clones = freeze(b.defineInRange("lensouls.field.clones", 0, 0, 10), 0);   // 复制体恒 0
        Object dashFrac = freeze(b.defineInRange("lensouls.field.dashFraction",
                (double) dashFraction, 0.0, 100.0), (double) dashFraction);
        Object pathFrac = freeze(b.defineInRange("lensouls.field.pathFraction",
                (double) pathFraction, 0.0, 100.0), (double) pathFraction);
        Object fallback = freeze(b.defineInRange("lensouls.field.fallbackAttack", -1.0, -1.0, 1000.0), -1.0);
        Object deathSave = freeze(b.define("lensouls.field.deathSave", false), false);
        Object deathSaveCooldown = freeze(b.defineInRange("lensouls.field.deathSaveCooldown", 0, 0, 24000), 0);
        Object deathSaveMult = freeze(b.defineInRange("lensouls.field.deathSaveMult", 0.0, 0.0, 100.0), 0.0);
        Object deathSaveHealth = freeze(b.defineInRange("lensouls.field.deathSaveHealth", 1.0, 1.0, 1024.0), 1.0);

        // 伤害归属走它自己的 resolveAttributor；deathSave 系列我们不用（也永远不会被它读到，因为没登记进它的表）
        profile = profileCtor.newInstance(
                EntityType.PIG, "lensouls:field_counter", (Predicate<Mob>) m -> true,
                chance, cooldown, dashCount, clones,
                dashFrac, pathFrac, fallback,
                deathSave, deathSaveCooldown, deathSaveMult, deathSaveHealth);
        return profile;
    }

    /**
     * 把这组配置值的「缓存值」直接写成我们的数字。
     * <p>
     * {@code ModConfigSpec.ConfigValue#get()} 优先返回 {@code cachedValue}，只有它是 null 时才去读
     * 真实配置文件（会因「spec 未加载」抛异常）。所以现造的 spec 不必 build/load，写死缓存即得常量。
     */
    private static Object freeze(Object value, Object number) throws Exception {
        if (cachedValueField == null) {
            cachedValueField = ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
            cachedValueField.setAccessible(true);
        }
        cachedValueField.set(value, number);
        return value;
    }

    // ==================== 反射初始化 ====================

    private static synchronized boolean init() {
        if (ready != null) return ready;
        try {
            if (!ModList.get().isLoaded(MOD_ID)) {
                ready = false;
                return false;
            }
            Class<?> profileClass = Class.forName("com.superdog.core.skill.EntitySkillProfile");
            Class<?> assault = Class.forName("com.superdog.core.AssaultManager");
            Class<?> network = Class.forName("com.superdog.network.NetworkHandler");
            tryCounterAttack = assault.getMethod("tryCounterAttack",
                    Mob.class, LivingEntity.class, profileClass, float.class, boolean.class);
            sendExplosionFX = network.getMethod("sendExplosionFX",
                    Entity.class, double.class, double.class, double.class,
                    double.class, double.class, double.class, float.class);
            sendDashParticles = network.getMethod("sendDashParticles",
                    Entity.class, double.class, double.class, double.class);
            profileCtor = profileClass.getConstructor(
                    EntityType.class, String.class, Predicate.class,
                    ModConfigSpec.DoubleValue.class, ModConfigSpec.IntValue.class,
                    ModConfigSpec.IntValue.class, ModConfigSpec.IntValue.class,
                    ModConfigSpec.DoubleValue.class, ModConfigSpec.DoubleValue.class,
                    ModConfigSpec.DoubleValue.class, ModConfigSpec.BooleanValue.class,
                    ModConfigSpec.IntValue.class, ModConfigSpec.DoubleValue.class,
                    ModConfigSpec.DoubleValue.class);
            ready = true;
            LenSouls.LOGGER.info("[Curse] ⑤ e23 已接入 superdog 反击技能引擎（自造档案，任意生物可用）");
        } catch (Throwable t) {
            disable(t);
        }
        return Boolean.TRUE.equals(ready);
    }

    private static synchronized void disable(Throwable t) {
        if (!Boolean.FALSE.equals(ready)) {
            ready = false;
            LenSouls.LOGGER.warn("[Curse] ⑤ e23 superdog 反击联动已停用（反射失败，退回字面反击）", t);
        }
    }
}
