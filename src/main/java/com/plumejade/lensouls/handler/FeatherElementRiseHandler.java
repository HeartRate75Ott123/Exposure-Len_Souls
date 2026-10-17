package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.feather.CurseDef;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import com.plumejade.lensouls.feather.FeatherEquip;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ③ 羽·元素觉醒者（新改案 · 2 条 · 整款共用反转条件）。
 *
 * <pre>
 * e1 躁动(0)：诅咒 = 每 (4 × 活性等级²) 秒，所有活性等级 +1；每个活性等级使受到伤害 +5%
 *             反转 = 每个活性等级使受到伤害改为 +2%（等级成长照旧）
 * e2 余灰(1)：诅咒 = DoT 伤害随目标剩余血量百分比变化（最高 +100%、最低 -50%）
 *             反转 = DoT 伤害下限改为 -20%
 * 反转条件（整款共用）：活性等级 ≥10 且生命值 ≥90% 时，造成一次 10000 点元素附加伤害
 * </pre>
 *
 * 旧版的「受到伤害 +50%」「造成伤害 +40%」「活性等级 +3」「元素 DoT ×3」
 * 与「无法使用复制之魂」<b>全部移除</b>（复制之魂封印见 {@code CopySoulSealHandler}）。
 */
public class FeatherElementRiseHandler {

    private static final CurseDef D = CurseDefs.ELEMENTRISE;

    // ── e1 躁动 ──
    /** 间隔基数：每 (4 × 活性等级**²**) 秒 +1 级（活性等级 = 四元素药水活性之和） */
    public static final int E1_INTERVAL_SECONDS_BASE = 4;
    /** 每级活性使受到伤害 +2.6%（诅咒态） */
    public static final float E1_TAKEN_PER_LEVEL_CURSE = 0.026f;
    /** 每级活性使受到伤害 +2%（反转态） */
    public static final float E1_TAKEN_PER_LEVEL_REVERSED = 0.02f;

    // ── e2 余灰：DoT 曲线 ──
    /** DoT 系数上限（+100% ⇒ ×2.0） */
    public static final float E2_DOT_MAX_MULT = 2.0f;
    /** DoT 系数下限（诅咒态 −50% ⇒ ×0.5） */
    public static final float E2_DOT_MIN_MULT = 0.5f;
    /** DoT 系数下限（反转态 −20% ⇒ ×0.8） */
    public static final float E2_DOT_MIN_MULT_REVERSED = 0.8f;

    // ── 整款共用反转条件 ──
    /** 条件：活性等级 ≥10 */
    public static final int REVERSE_MIN_ACTIVITY_LEVEL = 10;
    /** 条件：生命值 ≥90% */
    public static final float REVERSE_MIN_HEALTH_FRACTION = 0.9f;
    /** 条件：一次 10000 点元素附加伤害 */
    public static final float REVERSE_MIN_ELEMENT_BONUS = 10000f;

    /** 等级成长进度持久化键（PlayerPersisted 子键，跨死亡保留） */
    private static final String KEY_STEPS = "lensouls:rise_steps";
    private static final String KEY_NEXT = "lensouls:rise_next";
    private static final String KEY_BASE = "lensouls:rise_base";

    private static final Holder<MobEffect>[] INFUSIONS = new Holder[]{
            ModEffects.FIRE_INFUSION,
            ModEffects.WATER_INFUSION,
            ModEffects.EARTH_INFUSION,
            ModEffects.ENDER_INFUSION
    };

    /** 高频日志节流（元素附加伤害达标提示） */
    private static final Map<UUID, Long> LAST_BONUS_LOG = new HashMap<>();

    /**
     * 佩戴检测：{@link FeatherEquip#has}（只认本模组羽毛栏的 7 个槽位）。
     */
    public static boolean hasFeather(Player player) {
        return FeatherEquip.has(player, ModItems.FEATHER_ELEMENTRISE.get());
    }

    /** 跨死亡持久化子键（NeoForge 复活只复制 PlayerPersisted 子键） */
    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void writeBack(Player player, CompoundTag tag) {
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    // ==================== 受到伤害（e1 的每级 +5% / +2%） ====================

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Pre event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            float d = event.getNewDamage();
            if (d <= 0f) return;
            int level = activityLevel(player);
            if (level <= 0) return;

            // 受伤侧乘算（§2）
            if (CurseManager.on(player, D, 0)) {
                d *= 1.0f + E1_TAKEN_PER_LEVEL_CURSE * level;
            } else if (CurseManager.rev(player, D, 0)) {
                d *= 1.0f + E1_TAKEN_PER_LEVEL_REVERSED * level;
            }
            event.setNewDamage(d);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ③ e1 受伤结算异常", t);
        }
    }

    // ==================== 每 tick：活性等级成长（e1） ====================

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 10 != 0) return;
        try {
            tick(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ③ 每 tick 结算异常", t);
        }
    }

    private static void tick(ServerPlayer player) {
        if (!CurseManager.isActive(player, D)) {
            clearRamp(player);
            return;
        }

        CompoundTag tag = persisted(player);
        int steps = tag.getInt(KEY_STEPS);
        long lastUp = tag.getLong(KEY_LAST_UP);
        long now = player.level().getGameTime();

        // 计时口径（用户口径）：只存「上次升级时刻」，每次心跳用**当下等级**重算间隔再比较
        // ⇒ 升/降级后剩余时间立刻按新间隔重算，不沿用旧值，也不需要比例折算。
        // 间隔用**名义 Σ**：取「身上实际活性之和」与「步数」的较大者，
        // 否则效果还没上身时 Σ=0 ⇒ 间隔退化成 0 ⇒ 狂涨。
        int curLevel = Math.max(1, Math.max(ElementActivityMath.currentSum(player), steps));
        if (steps <= 0) {
            steps = 1;                       // 初次佩戴直接起步 1 级（0 级会让 (4 × L²) 秒退化成 0）
            lastUp = now;
        } else if (lastUp <= 0L || now - lastUp >= intervalTicks(curLevel)) {
            steps++;
            lastUp = now;
        }

        tag.putInt(KEY_STEPS, steps);
        tag.putLong(KEY_LAST_UP, lastUp);
        writeBack(player, tag);

        // 效果写入不在这里做：{@link ElementActivityMath#push} 每 tick 幂等对齐一次
        // （最终等级 = 来源 + steps − ①惩罚），这里只维护步数。
    }

    /** 卸下 / 未选中：只清旧口径遗留键（步数保留），活性本身由 push 自然降回来源等级 */
    private static void clearRamp(ServerPlayer player) {
        CompoundTag tag = persisted(player);
        // 注意：KEY_STEPS **不清**——它是「元素觉醒者已经叠出来的修改值」，
        // 卸下只代表暂时不贡献，重装后应继承它继续叠（用户口径）。
        if (!tag.contains(KEY_NEXT) && !tag.contains(KEY_BASE)
                && !tag.contains(KEY_INTERVAL_LEVEL) && !tag.contains(KEY_WRITTEN)) {
            return;
        }
        tag.remove(KEY_NEXT);
        tag.remove(KEY_BASE);
        tag.remove(KEY_INTERVAL_LEVEL);
        tag.remove(KEY_WRITTEN);
        writeBack(player, tag);
    }

    /** 供控制器读取步数（③ 只维护 steps，不再自己写效果） */
    static int stepsOf(ServerPlayer p) {
        // 只有当前佩戴 ③ 时才把步数贡献给控制器；但**存储不因卸下而清空**。
        // 这正是"effect clear 后只剩 1 级、重装羽毛要重算"的根因：
        // 现在卸下只是不贡献，重新装配时继承旧值、按新速率继续往上叠。
        if (!com.plumejade.lensouls.feather.CurseManager.isActive(p, D)) return 0;
        return persisted(p).getInt(KEY_STEPS);
    }

    static {
        ElementActivityMath.boostProvider = FeatherElementRiseHandler::stepsOf;
    }

    /** 上次**升级时刻**（gameTime）。只存这个、不存「下次到点时刻」，
     *  每次心跳用当下等级重算间隔再比较 ⇒ 时长永远符合当下状态 */
    private static final String KEY_LAST_UP = "lensouls:rise_last_up";
    /** 旧口径遗留键（上次计时等级；现已不用，仅保留清理） */
    private static final String KEY_INTERVAL_LEVEL = "lensouls:rise_interval_level";
    /** 我们上次写进去的**总量**（外部 + steps），用于判断外部等级有没有改过 */
    private static final String KEY_WRITTEN = "lensouls:rise_written";

    /** 升级间隔：4 × 活性等级² 秒 */
    private static long intervalTicks(int level) {
        // 二次方（用户口径）：间隔 = 4 × 活性等级² 秒（曾试过三次方，已按用户要求改回平方）
        return (long) E1_INTERVAL_SECONDS_BASE * level * level * 20L;
    }

    /**
     * 玩家当前「活性等级」= 四种**药水活性**等级之**和**（无 = 0）。
     * <p>
     * 用户裁定：「求和（只算四种药水活性加和）」——
     * 只累加四种元素灌注（药水活性）的等级，不含武器活性、damage_type 活性、攻击者实体活性。
     * 口径与 ① 羽·荒厄遗咒 e1 的 {@code activityFlatBonus}（同样是四元素求和）保持一致。
     */
    public static int activityLevel(Player player) {
        if (player == null) return 0;
        int sum = 0;
        for (Holder<MobEffect> infusion : INFUSIONS) {
            MobEffectInstance inst = player.getEffect(infusion);
            if (inst != null) sum += Math.max(0, inst.getAmplifier() + 1);
        }
        return sum;
    }

    // ==================== e2 余灰：DoT 曲线 ====================

    /**
     * DoT 伤害系数：随目标剩余血量百分比线性变化 ——
     * 满血 ×2.0（+100%）、空血 ×0.5（-50%）；反转后下限抬到 ×0.8（-20%）。
     */
    public static float dotMultiplier(ServerPlayer attacker, LivingEntity target) {
        if (attacker == null || target == null) return 1.0f;
        boolean curse = CurseManager.on(attacker, D, 1);
        boolean reversed = CurseManager.rev(attacker, D, 1);
        if (!curse && !reversed) return 1.0f;

        float maxHealth = target.getMaxHealth();
        float frac = maxHealth > 0f ? Math.max(0f, Math.min(1f, target.getHealth() / maxHealth)) : 0f;
        float mult = E2_DOT_MIN_MULT + (E2_DOT_MAX_MULT - E2_DOT_MIN_MULT) * frac;
        float floor = reversed ? E2_DOT_MIN_MULT_REVERSED : E2_DOT_MIN_MULT;
        return Math.max(floor, mult);
    }

    // ==================== 整款共用反转条件：一次 10000 点元素附加伤害 ====================

    /**
     * 由 {@code DamageHandler} 在算出本次「元素附加伤害」后回灌（见 {@code DamageHandler#onLivingDamagePre}）。
     */
    public static void onElementBonus(ServerPlayer player, float bonus) {
        if (player == null || bonus <= 0f) return;
        try {
            if (!CurseManager.isActive(player, D)) return;
            if (bonus < REVERSE_MIN_ELEMENT_BONUS) return;
            if (activityLevel(player) < REVERSE_MIN_ACTIVITY_LEVEL) return;
            if (player.getHealth() < player.getMaxHealth() * REVERSE_MIN_HEALTH_FRACTION) return;

            long now = player.level().getGameTime();
            Long last = LAST_BONUS_LOG.get(player.getUUID());
            if (last == null || now - last > 200L) {
                LAST_BONUS_LOG.put(player.getUUID(), now);
                LenSouls.LOGGER.info("[Curse] ③ 元素附加伤害 {} 点达标（玩家 {}）",
                        String.format("%.1f", bonus), player.getName().getString());
            }

            for (int i = 0; i < D.entries(); i++) {
                CurseManager.tick(player, D, i, 1L, 1L);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ③ 反转条件结算异常", t);
        }
    }
}
