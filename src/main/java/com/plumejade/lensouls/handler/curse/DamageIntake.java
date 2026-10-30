package com.plumejade.lensouls.handler.curse;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「累计受伤 / 受伤阈值」类反转条件的**唯一口径与唯一入口**。
 *
 * <h2>为什么不能拿 {@code LivingIncomingDamageEvent.getAmount()}</h2>
 * 那个事件在 {@code LivingEntity.hurt()} 的**入口**就派发了（NeoForge 的
 * {@code CommonHooks.onEntityIncomingDamage}），此时：
 * <ul>
 *   <li>护甲、附魔保护、抗性提升都**还没结算** —— 它们要到 {@code actuallyHurt} 里才扣，
 *       {@code LivingDamageEvent.Pre} 才看得到护甲后的值；</li>
 *   <li>别的模组可能在这之后取消它、或者改用另一个伤害源重打；</li>
 *   <li>被伤害吸收（黄血）、被护盾挡掉多少，在那个时间点完全不知道。</li>
 * </ul>
 * 所以拿它当「承受的伤害」会得到一个虚高的名义值，和玩家血条上看到的数对不上。
 *
 * <h2>这里用的是什么</h2>
 * {@code LivingDamageEvent.Post}：它在 {@code actuallyHurt} 的**末尾**派发
 * （{@code CommonHooks.onLivingDamagePost}，在 {@code setHealth} 与伤害吸收扣减都跑完之后）。
 * {@code Post.getNewDamage()} 就是「算完那么多之后真正落到身上的那一下」——
 * 护甲/附魔/抗性/免伤/本模组全部倍率都算进去了，**含**被伤害吸收吃掉的那部分
 * （伤害吸收的扣减不回写 {@code DamageContainer.newDamage}，故 Post 里仍是含吸收的完整量）。
 *
 * <h2>满血 / 打前状态</h2>
 * 满血这类「打之前」的状态，必须在伤害生效前取：所以在 Incoming 阶段（{@code HIGHEST}，
 * 比谁都早）快照一次血量，Post 里跟这一击配对使用。这样即使同 tick 里血量被别的东西改过，
 * 也不会把「打之前是否满血」判错。
 *
 * <h2>计数裁剪</h2>
 * 单次不超过「10 倍最大生命」（{@code /kill} 的 {@code genericKill} 是 {@code Float.MAX_VALUE}，
 * 不裁会一次把 3000/5000 灌满并留下天文数字）；{@code BYPASSES_INVULNERABILITY}
 * （虚空、必死级）一律不计 —— 那不是「承受的伤害」。
 */
public final class DamageIntake {

    /** 满血容差：血量是 float，属性变动/治疗溢出后可能差一个 ulp */
    private static final float FULL_EPS = 1.0e-3f;

    /** 单次计入上限 = 最大生命 × 这个倍数 */
    private static final float CAP_MULTIPLIER = 10.0f;

    /** 每玩家「这一击落下之前」的血量快照 [health, maxHealth]（UUID → 两元素数组） */
    private static final Map<UUID, float[]> PRE = new ConcurrentHashMap<>();

    private DamageIntake() {
    }

    /**
     * 伤害生效**前**的血量快照。
     * <p>
     * 必须挂在 {@code HIGHEST}：再晚一点就可能被「减伤 / 治疗 / 别的模组改血量」污染。
     * 这里不碰事件本身，只读不写。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void snapshotBefore(LivingIncomingDamageEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            PRE.put(player.getUUID(), new float[]{player.getHealth(), player.getMaxHealth()});
        } catch (Throwable t) {
            warn("快照", t);
        }
    }

    /** 伤害结算**后**：取真实扣血，统一派发给所有受伤类条件。 */
    @SubscribeEvent
    public static void afterSettled(LivingDamageEvent.Post event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            float[] pre = PRE.remove(player.getUUID());
            float dealt = event.getNewDamage();
            if (!Float.isFinite(dealt) || dealt <= 0.0f) return;

            DamageSource src = event.getSource();
            // 必死级来源（/kill 的 genericKill、虚空）不算「承受的伤害」
            if (src != null && src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

            // 没配上快照（伤害不是从 hurt 进来、或嵌套结算打乱了配对）时按「当前血量 + 这一下」反推
            float preHealth = pre != null ? pre[0] : player.getHealth() + dealt;
            float maxHealth = pre != null ? pre[1] : player.getMaxHealth();
            if (!Float.isFinite(preHealth) || maxHealth <= 0.0f) return;

            long cap = Math.max(1L, (long) Math.ceil(maxHealth * CAP_MULTIPLIER));
            long points = Math.min((long) Math.ceil(dealt), cap);
            if (points <= 0L) return;

            boolean wasFull = preHealth >= maxHealth - FULL_EPS;
            AttackDiag.log("⑤真实扣血 {} 打前={}/{} 满血={} 计入={}", dealt, preHealth, maxHealth, wasFull, points);

            TimecoreCurse.onDamageTaken(player, src, dealt, points, wasFull);
        } catch (Throwable t) {
            warn("派发", t);
        }
    }

    /** 掉线/重登时清掉残影（避免 UUID 在内存里长期残留） */
    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) PRE.remove(event.getEntity().getUUID());
    }

    private static void warn(String stage, Throwable t) {
        LenSouls.LOGGER.error("[Curse] 受伤口径{}异常", stage, t);
    }
}
