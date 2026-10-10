package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.config.BossEntityLoader;
import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.feather.CurseDef;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import com.plumejade.lensouls.feather.FeatherEquip;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ① 羽·荒厄遗咒（新改案 · 3 条 · 整款共用反转条件）。
 *
 * <pre>
 * e1 蚀灵(0)：诅咒 = 失去所有元素亲和力、药水活性对你无效
 *             反转 = 元素亲和恢复；活性效果 -2；每有 1 级正数活性额外提供 1 点附加伤害
 * e2 封器(1)：诅咒 = 无法使用相机（只禁能力照片）
 *             反转 = 相机解禁；韧性伤害 -2（点数加算）；被拍摄的敌对生物受到「韧性伤害 × 当前近战伤害」的伤害
 * e3 蚀峰(2)：诅咒 = 造成伤害 -35%、受到伤害 +35%；额外造成「当前伤害 × 空余照片饰品栏位 × 5%」的附加伤害（真伤）
 *             反转 = 〔保留〕-35% / +35% 照旧；护甲 +5；系数改为 6%（真伤）
 * 反转条件（整款共用）：空余照片饰品栏位 ≥5 时击杀 4 只不同 BOSS（bosslist）
 * </pre>
 *
 * <b>叠加口径</b>（设计文档 §2）：受伤侧一律乘算；e3 的附加伤害与「每级活性 +1 点」属点数类、直接加算，
 * 且因为是「真伤」所以在 {@link EventPriority#LOWEST} 才并入（此时韧性减伤与武器匹配惩罚都已结算完）。
 * <p>
 * 佩戴判定统一走 {@link FeatherEquip#has}；条目门控走 {@link CurseManager#on} / {@link CurseManager#rev}，
 * 进度走 {@link CurseManager#tick}（逐条累加，达标即该条反转）。
 */
public class FeatherHardmanHandler {

    private static final CurseDef D = CurseDefs.HARDMAN;

    // ==================== 反转条件（整款共用） ====================

    /** 空余照片饰品栏位要求（≥5） */
    public static final int REQUIRED_FREE_PHOTO_SLOTS = 5;
    /** 不同 BOSS 击杀数要求（4） */
    public static final int DISTINCT_BOSS_GOAL = 4;
    /** 「已击杀过的不同 BOSS」持久化键（逗号分隔的实体 id 列表） */
    private static final String KEY_BOSS_KILLS = "lensouls:hardman_boss_kills";

    // ==================== e1 蚀灵 ====================

    /** 反转后「活性效果 -2」（级） */
    public static final int ACTIVITY_PENALTY = 2;
    /** 反转后「每有 1 级正数活性 → 额外 1 点附加伤害」（点数加算，§2③） */
    public static final float ACTIVITY_FLAT_BONUS_PER_LEVEL = 1.0f;

    // ==================== e2 封器 ====================

    /** 反转后「韧性伤害 -2」（点数加算，§2③；由 BossToughnessManager 读取） */
    public static final float REVERSED_TOUGHNESS_DAMAGE_DELTA = -2.0f;

    // ==================== e3 蚀峰 ====================

    /** 造成伤害 -35%（诅咒态与反转态都保留） */
    public static final float E3_DEALT_MULTIPLIER = 0.65f;
    /** 受到伤害 +35%（诅咒态与反转态都保留；受伤侧乘算） */
    public static final float E3_TAKEN_MULTIPLIER = 1.35f;
    /** 反转后护甲 +5（点数加算） */
    public static final int E3_REVERSED_ARMOR = 5;
    /** 附加伤害公式系数（诅咒态 5%） */
    public static final float E3_BONUS_COEF_CURSE = 0.05f;
    /** 附加伤害公式系数（反转态 6%） */
    public static final float E3_BONUS_COEF_REVERSED = 0.06f;

    /** 护甲修饰符 ID（transient，幂等替换） */
    private static final ResourceLocation ARMOR_MODIFIER_ID = ResourceLocation.parse("lensouls:hardman_armor");

    /** 元素活性效果（e1 的「元素亲和 / 药水活性」轴） */
    private static final Holder<MobEffect>[] INFUSIONS = new Holder[]{
            ModEffects.FIRE_INFUSION,
            ModEffects.WATER_INFUSION,
            ModEffects.EARTH_INFUSION,
            ModEffects.ENDER_INFUSION
    };

    /** e1 反转：每元素「玩家自己的基础活性等级」 */
    private static final Map<UUID, int[]> ACTIVITY_BASE = new HashMap<>();
    /** e1 反转：每元素「我们写回去的等级」 */
    private static final Map<UUID, int[]> ACTIVITY_APPLIED = new HashMap<>();
    /** e1 反转：被扣减到 0 而移除时的剩余时长（用于摘下羽毛后还原） */
    private static final Map<UUID, int[]> ACTIVITY_SAVED_DURATION = new HashMap<>();
    /** e3 附加伤害的空余照片栏位数缓存：UUID → {gameTime, freeSlots} */
    private static final Map<UUID, long[]> SLOT_CACHE = new HashMap<>();

    /**
     * 佩戴检测：{@link FeatherEquip#has}（只认本模组羽毛栏的 7 个槽位）。
     */
    public static boolean hasHardman(Player player) {
        return FeatherEquip.has(player, ModItems.FEATHER_HARDMAN.get());
    }

    /**
     * 旧版「每次伤害的 30% 视为真伤」机制已按新改案<b>移除</b>
     * （新版真伤只存在于 e3 的附加伤害里，按公式在 {@link EventPriority#LOWEST} 并入）。
     * <p>
     * 方法保留是因为 {@code ToughnessDamageHandler} 仍会调用它；恒返回 0 即等于关闭该机制。
     */
    public static float trueDamageShare(LivingEntity target, net.minecraft.world.damagesource.DamageSource source) {
        return 0f;
    }

    // ==================== 事件：元素亲和 / 药水活性（e1） ====================

    /**
     * e1 诅咒态「药水活性对你无效」：在效果<b>施加前</b>拦截四种元素活性，
     * 避免「事后移除 → 同 tick 重加」的闪烁（与 {@code SuppressHandler} 同款做法）。
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!CurseManager.on(player, D, 0)) return;
            MobEffectInstance inst = event.getEffectInstance();
            if (inst == null) return;
            for (Holder<MobEffect> infusion : INFUSIONS) {
                if (infusion == inst.getEffect()) {
                    event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                    return;
                }
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ① e1 活性拦截异常", t);
        }
    }

    // ==================== 每 tick ====================

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            tick(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ① 每 tick 结算异常", t);
        }
    }

    private static void tick(ServerPlayer player) {
        boolean worn = hasHardman(player);

        // ── e1 蚀灵 ──
        if (!CurseManager.rev(player, D, 0)) {
            // 非反转态（含诅咒态 / 未选中）：把我们写过的等级还原
            restoreActivity(player);
        }
        if (worn && CurseManager.on(player, D, 0)) {
            // 诅咒：失去元素亲和力、药水活性无效 —— 持续清除四种活性
            for (Holder<MobEffect> infusion : INFUSIONS) {
                player.removeEffect(infusion);
            }
        } else if (CurseManager.rev(player, D, 0)) {
            applyActivityPenalty(player);
        }

        // ── e3 蚀峰：反转后护甲 +5 ──
        if (CurseManager.rev(player, D, 2)) {
            AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
            if (armor != null && armor.getModifier(ARMOR_MODIFIER_ID) == null) {
                armor.addTransientModifier(new AttributeModifier(
                        ARMOR_MODIFIER_ID, E3_REVERSED_ARMOR, AttributeModifier.Operation.ADD_VALUE));
            }
        } else {
            removeArmor(player);
        }

        // 未选中本款 → 共用条件的「不同 BOSS」名单也不累计
        if (!worn) {
            clearBossKills(player);
            SLOT_CACHE.remove(player.getUUID());
        }
    }

    private static void removeArmor(ServerPlayer player) {
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.removeModifier(ARMOR_MODIFIER_ID);
    }

    /**
     * e1 反转：活性效果 -2。逐元素维护「玩家自己的基础等级」与「我们写回去的等级」，
     * 观测值不是我们的产物时说明玩家自己改了等级（喝药水 / 到期），把它采纳为新的基础等级。
     */
    // ==================== e1 反转：活性 -2 的**持久化**账本 ====================
    // ⚠ 必须是持久化（PlayerPersisted 子键），不能只用内存 Map：
    //   重进游戏内存 Map 清空 ⇒ 每次重登都会「再扣一次 2 级」（累积扣减，活性被越扣越少、
    //   看起来就像「反转后活性仍被禁用」），而且 restoreActivity 也因拿不到记录永远还原不了。

    /** 玩家自己的基础等级（未扣减）；下标与 INFUSIONS 对齐 */
    private static final String KEY_ACT_BASE = "lensouls:hardman_act_base";
    /** 我们写回去的等级（已扣减） */
    private static final String KEY_ACT_APPLIED = "lensouls:hardman_act_applied";
    /** 被我们删掉时的剩余时长（用于原样还原） */
    private static final String KEY_ACT_SAVED = "lensouls:hardman_act_saved";

    private static int[] loadAct(ServerPlayer player, String key) {
        int[] a = player.getPersistentData()
                .getCompound(Player.PERSISTED_NBT_TAG).getIntArray(key);
        return a.length == INFUSIONS.length ? a : new int[INFUSIONS.length];
    }

    private static void saveAct(ServerPlayer player, String key, int[] a) {
        CompoundTag tag = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        tag.putIntArray(key, a);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    private static void clearAct(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!tag.contains(KEY_ACT_BASE) && !tag.contains(KEY_ACT_APPLIED) && !tag.contains(KEY_ACT_SAVED)) return;
        tag.remove(KEY_ACT_BASE);
        tag.remove(KEY_ACT_APPLIED);
        tag.remove(KEY_ACT_SAVED);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    private static void applyActivityPenalty(ServerPlayer player) {
        int[] base = loadAct(player, KEY_ACT_BASE);
        int[] applied = loadAct(player, KEY_ACT_APPLIED);
        int[] saved = loadAct(player, KEY_ACT_SAVED);

        for (int i = 0; i < INFUSIONS.length; i++) {
            MobEffectInstance inst = player.getEffect(INFUSIONS[i]);
            int observed = inst == null ? 0 : inst.getAmplifier() + 1;
            if (observed != applied[i]) {
                base[i] = observed;
                saved[i] = 0;
            }
            int desired = Math.max(0, base[i] - ACTIVITY_PENALTY);
            if (desired != observed) {
                if (inst != null) {
                    saved[i] = inst.getDuration();
                    player.removeEffect(INFUSIONS[i]);
                }
                if (desired > 0 && inst != null) {
                    player.addEffect(new MobEffectInstance(INFUSIONS[i], inst.getDuration(), desired - 1,
                            inst.isAmbient(), inst.isVisible(), inst.showIcon()));
                } else if (desired > 0) {
                    player.addEffect(new MobEffectInstance(INFUSIONS[i],
                            MobEffectInstance.INFINITE_DURATION, desired - 1, true, true, true));
                }
            }
            applied[i] = desired;
        }
        // 账本落盘：下一 tick / 下次登录都靠它，避免重复扣减
        saveAct(player, KEY_ACT_BASE, base);
        saveAct(player, KEY_ACT_APPLIED, applied);
        saveAct(player, KEY_ACT_SAVED, saved);
    }

    /** 非反转态：把我们扣减过的活性等级还原成玩家自己的基础等级，并清掉本地状态 */
    private static void restoreActivity(ServerPlayer player) {
        CompoundTag persistedTag = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persistedTag.contains(KEY_ACT_BASE)) return;      // 没有账本 ⇒ 不是我们改过，别动
        int[] base = loadAct(player, KEY_ACT_BASE);
        int[] applied = loadAct(player, KEY_ACT_APPLIED);
        int[] saved = loadAct(player, KEY_ACT_SAVED);

        for (int i = 0; i < INFUSIONS.length; i++) {
            MobEffectInstance inst = player.getEffect(INFUSIONS[i]);
            int observed = inst == null ? 0 : inst.getAmplifier() + 1;
            if (observed != applied[i]) continue;         // 不是我们的产物 → 不动它
            if (observed == base[i]) continue;            // 本来就一致
            if (inst != null) {
                player.removeEffect(INFUSIONS[i]);
                if (base[i] > 0) {
                    player.addEffect(new MobEffectInstance(INFUSIONS[i], inst.getDuration(), base[i] - 1,
                            inst.isAmbient(), inst.isVisible(), inst.showIcon()));
                }
            } else if (base[i] > 0 && saved != null && saved[i] != 0) {
                // 是我们把它扣到 0 才删掉的 → 按当初记下的剩余时长还原
                player.addEffect(new MobEffectInstance(INFUSIONS[i], saved[i], base[i] - 1, true, true, true));
            }
        }
        clearAct(player);
    }

    /** e1 反转：每有 1 级正数活性 → 1 点附加伤害（点数加算，§2③） */
    public static float activityFlatBonus(ServerPlayer player) {
        if (player == null || !CurseManager.rev(player, D, 0)) return 0f;
        int levels = 0;
        for (Holder<MobEffect> infusion : INFUSIONS) {
            MobEffectInstance inst = player.getEffect(infusion);
            if (inst != null) levels += Math.max(0, inst.getAmplifier() + 1);
        }
        return levels * ACTIVITY_FLAT_BONUS_PER_LEVEL;
    }

    // ==================== 受到伤害（e3 保留项） ====================

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Pre event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!CurseManager.on(player, D, 2) && !CurseManager.rev(player, D, 2)) return;
            float d = event.getNewDamage();
            if (d <= 0f) return;
            // 受伤侧一律乘算（§2）
            event.setNewDamage(d * E3_TAKEN_MULTIPLIER);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ① e3 受伤结算异常", t);
        }
    }

    // ==================== 造成伤害（e3 + e1 反转） ====================

    /**
     * LOWEST：韧性减伤 / 武器匹配惩罚都已结算完，此时并入「真伤」符合设计口径
     * （附加伤害不吃韧性减伤，也不被武器匹配 ×0.1 吃掉）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        try {
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
            if (event.getEntity() == player) return;
            float d = event.getNewDamage();
            if (d <= 0f) return;

            // e3 蚀峰：造成伤害 -35%（诅咒态与反转态都保留）
            if (CurseManager.on(player, D, 2) || CurseManager.rev(player, D, 2)) {
                d *= E3_DEALT_MULTIPLIER;
                // 附加伤害（真伤）：当前伤害 × 空余照片饰品栏位 × 系数（5% / 反转 6%）
                int free = freePhotoSlots(player);
                if (free > 0) {
                    float coef = CurseManager.rev(player, D, 2)
                            ? E3_BONUS_COEF_REVERSED : E3_BONUS_COEF_CURSE;
                    // 「不吃韧性减伤」的实现：基数取**韧性减伤之前**的伤害（HIGHEST 阶段记录）。
                    // 本处理器是 LOWEST，此时 d 已是减伤后的值；用减伤前基数算出的附加量直接相加，
                    // 而相加发生在减伤之后 ⇒ 这 5%/6% 就是真伤。
                    // （非 BOSS 目标没有韧性减伤，ThreadLocal 读到的是同一值，行为不变。）
                    Float pre = com.plumejade.lensouls.boss.ToughnessDamageHandler.preToughnessDamage();
                    float trueBase = (pre != null && pre > 0f) ? pre : d;
                    d += trueBase * E3_DEALT_MULTIPLIER * free * coef;
                }
            }

            // e1 蚀灵 反转：每有 1 级正数活性 → 1 点附加伤害
            d += activityFlatBonus(player);

            event.setNewDamage(d);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ① 造成伤害结算异常", t);
        }
    }

    // ==================== 反转条件：击杀 4 只不同 BOSS（空余栏位 ≥5） ====================

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        try {
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
            LivingEntity dead = event.getEntity();
            if (dead == player) return;
            if (!CurseManager.isActive(player, D)) return;
            if (freePhotoSlots(player) < REQUIRED_FREE_PHOTO_SLOTS) return;

            // BOSS 判定：首领清单（bosslist）
            if (!BossEntityLoader.isBoss(dead)) return;
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
            if (id == null) return;

            Set<String> seen = loadBossKills(player);
            if (!seen.add(id.toString())) return;         // 必须是「不同」BOSS
            saveBossKills(player, seen);

            for (int i = 0; i < D.entries(); i++) {
                CurseManager.tick(player, D, i, 1L, DISTINCT_BOSS_GOAL);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ① 反转条件（BOSS 击杀）结算异常", t);
        }
    }

    private static Set<String> loadBossKills(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        String raw = persisted.getString(KEY_BOSS_KILLS);
        Set<String> set = new HashSet<>();
        if (!raw.isEmpty()) {
            for (String part : raw.split(",")) {
                if (!part.isEmpty()) set.add(part);
            }
        }
        return set;
    }

    private static void saveBossKills(Player player, Set<String> set) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putString(KEY_BOSS_KILLS, String.join(",", set));
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static void clearBossKills(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(KEY_BOSS_KILLS)) return;
        persisted.remove(KEY_BOSS_KILLS);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    // ==================== 空余照片饰品栏位 ====================

    /**
     * 「空余照片饰品栏位」= Curios 的 {@code photograph} 槽位总数 − 已占用数（10 tick 缓存）。
     */
    public static int freePhotoSlots(ServerPlayer player) {
        if (player == null) return 0;
        long now = player.level().getGameTime();
        long[] cached = SLOT_CACHE.get(player.getUUID());
        if (cached != null && now - cached[0] < 10L) return (int) cached[1];
        int free = computeFreePhotoSlots(player);
        SLOT_CACHE.put(player.getUUID(), new long[]{now, free});
        return free;
    }

    private static int computeFreePhotoSlots(ServerPlayer player) {
        try {
            var handlerOpt = CuriosApi.getCuriosInventory(player);
            if (handlerOpt.isEmpty()) return 0;
            var stacksHandler = handlerOpt.get().getCurios().get("photograph");
            if (stacksHandler == null) return 0;
            var stacks = stacksHandler.getStacks();
            int total = stacks.getSlots();
            int used = 0;
            for (int i = 0; i < total; i++) {
                if (!stacks.getStackInSlot(i).isEmpty()) used++;
            }
            return Math.max(0, total - used);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ① 空余照片栏位统计异常", t);
            return 0;
        }
    }

    /** 供 e2 判定：是否处于「相机已解禁」的反转态 */
    public static boolean isCameraUnlocked(ServerPlayer player) {
        return CurseManager.rev(player, D, 1);
    }

    /** 供 e2 判定：是否处于「无法使用相机」的诅咒态（只禁能力照片） */
    public static boolean isCameraLocked(ServerPlayer player) {
        return CurseManager.on(player, D, 1);
    }

    /** e2 反转的韧性伤害点数（点数加算，§2③） */
    public static float toughnessDamageDelta(ServerPlayer player) {
        return CurseManager.rev(player, D, 1) ? REVERSED_TOUGHNESS_DAMAGE_DELTA : 0f;
    }

}
