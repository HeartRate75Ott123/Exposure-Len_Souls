package com.plumejade.lensouls.handler.curse;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.feather.CurseDef;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import com.plumejade.lensouls.integration.ParagliderStamina;
import com.plumejade.lensouls.util.AllyFilter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ⑥ 羽·终焉秘仪（秘仪 · 代价之律）7 条诅咒的效果。
 * <p>
 * <b>统一写法</b>：每条都先过 {@link CurseManager#on} / {@link CurseManager#rev}
 * （= 该款被选进槽位 ∧ 该条未反转 / 已反转），反转条件 = 击杀指定 BOSS，
 * 命中即 {@link CurseManager#tick} 一次（{@code goal = 1}）→ 达标自动反转。
 * <p>
 * <b>叠加口径</b>（见设计文档 §2「叠加口径（定稿）」）：诅咒态**乘算**（{@code d * k}）、
 * 反转态**加算**（{@code d * (1 + Σ 加算值)}）；**受伤侧一律乘算**、**治疗侧一律乘算**。
 * 本类 输出侧的 e3（+10%）与 e4（+1%/掉落物）两条反转加算在同一个事件里**统一收口一次**。
 * <p>
 * 条目序号（0 基）与 lang 的 {@code eN} 一致：e1=竭泉 e2=血偿 e3=蚀力 e4=力竭
 * e5=篡命 e6=献祭 e7=穿甲。
 * <p>
 * ⚠ 本类只写效果，**注册由 LenSouls 主构造器统一做**（{@code NeoForge.EVENT_BUS.register(FinalriteCurse.class)}）。
 */
public final class FinalriteCurse {

    private static final CurseDef D = CurseDefs.FINALRITE;

    /** e5 篡命：最大生命上限修饰符（transient，可重复添加幂等替换） */
    private static final ResourceLocation HEALTH_MOD = ResourceLocation.parse("lensouls:curse_finalrite_health");

    /** e1 竭泉：每 1 级生命恢复的治疗衰减 */
    private static final float REGEN_HEAL_CUT_PER_LEVEL = 0.25f;
    /** e1 竭泉：治疗衰减上限（-50%） */
    private static final float REGEN_HEAL_CUT_CAP = 0.50f;
    /** e2 血偿：造成伤害后的禁疗窗口（5 秒 = 100 tick） */
    private static final long BLOOD_WINDOW_TICKS = 100L;
    /** e2 血偿：窗口中自身治疗量倍率（-50%） */
    private static final float BLOOD_HEAL_MULTIPLIER = 0.5f;
    /** e2 血偿 反转：对 4 格内敌人造成「治疗量 ×50%」的魔法伤害 */
    private static final float BLOOD_REV_DAMAGE_RATIO = 0.50f;
    /** e3 蚀力：诅咒态全伤害 -10% / 反转态加算 +10% */
    private static final float EROSION = 0.10f;
    /** e4 力竭：**每造成一次伤害**固定消耗的精力值（Paraglider 默认一管 1000 ⇒ 约 6 次命中耗尽） */
    private static final double STAMINA_PER_HIT = 180.0;
    /** e4 力竭 诅咒态：精力比例低于此值时，造成伤害 -30% */
    private static final double LOW_STAMINA_RATIO = 0.10;
    /** e4 力竭 诅咒态：低精力时的伤害损失 */
    private static final float LOW_STAMINA_LOSS = 0.30f;
    /** e4 力竭 反转条件：精力比例低于此值时，一次打出 5000 点伤害即达成 */
    private static final double ONESHOT_STAMINA_RATIO = 0.50;
    /** e4 力竭 反转条件：单次造成伤害门槛 */
    private static final float ONESHOT_DAMAGE = 5000.0f;
    /** e4 力竭 反转：精力比例高于此值时，造成伤害 +15% */
    private static final double HIGH_STAMINA_RATIO = 0.50;
    /** e4 力竭 反转：高精力时的伤害加成 */
    private static final float HIGH_STAMINA_BONUS = 0.15f;
    /** e2 血偿 反转：对 4 格内敌人生效的检索半径 */
    private static final double NEARBY_RADIUS = 4.0;
    /** e5 篡命：每装备一件护甲，最大生命上限 ×90% */
    private static final double HEALTH_PER_ARMOR = 0.9;
    /** e6 献祭：满血时受到伤害 +70% */
    private static final float FULL_HEALTH_TAKEN = 1.7f;
    /** e6 献祭 反转：每损失 1% 最大生命，受到伤害 -0.5% */
    private static final float LOST_HEALTH_REDUCTION = 0.005f;
    /** e7 穿甲：每次受伤有 50% 伤害无法吃到护甲值 */
    private static final float ARMOR_PIERCE_RATIO = 0.50f;
    /** e7 穿甲 反转：每次受伤有 10% 概率无视这次伤害 */
    private static final float DODGE_CHANCE = 0.10f;
    /** 浮点比较容差 */
    private static final double EPS = 1.0e-4;

    /** e2 血偿：最近一次「造成伤害」的 gameTime（每 tick 清理过期项） */
    private static final Map<UUID, Long> LAST_DEALT = new HashMap<>();

    /**
     * 反转条件（击杀指定 BOSS）：实体 ID → 条目序号（0 基）。
     * <p>
     * ⚠ 传奇怪物那只的注册名是 {@code legendary_monsters:posessed_paladin}
     * （模组自身拼写如此，只有一个 s；已按 jar 内 {@code ModEntities} 实测核对）。
     */
    /**
     * 反转条件（击杀指定 BOSS）：实体 ID → 条目序号（0 基）。
     * <p>
     * ⚠ **e4 力竭 不在这里**：它的反转条件是「精力低于 50% 时一次打出 5000 点伤害」，
     * 由 {@link #onDealDamagePost} 直判，所以这条表只剩 6 只 boss。
     * <p>
     * ⚠ 传奇怪物那只的注册名是 {@code legendary_monsters:posessed_paladin}
     * （模组自身拼写如此，只有一个 s；已按 jar 内 {@code ModEntities} 实测核对）。
     */
    private static final Map<String, Integer> BOSS_ENTRY = Map.of(
            "minecraft:warden", 0,                                // e1 竭泉 · 监守者
            "legendary_monsters:posessed_paladin", 1,              // e2 血偿 · 堕落圣骑
            "cataclysm:ignis", 2,                                  // e3 蚀力 · 炎魔 Ignis
            "cataclysm:scylla", 4,                                  // e5 篡命 · 斯库拉 Scylla
            "cataclysm:ender_guardian", 5,                          // e6 献祭 · 末影守卫
            "cataclysm:the_harbinger", 6                            // e7 穿甲 · 先驱者
    );

    private FinalriteCurse() {
    }

    // ==================== 每 tick ====================

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            tick(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑥ 每 tick 结算异常", t);
        }
    }

    private static void tick(ServerPlayer player) {
        long now = player.level().getGameTime();

        // e5 篡命：每装备一件护甲，最大生命上限 ×90%（0.9^n - 1，ADD_MULTIPLIED_TOTAL）
        var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            double target = CurseManager.on(player, D, 4)
                    ? Math.pow(HEALTH_PER_ARMOR, armorPieceCount(player)) - 1.0
                    : 0.0;
            AttributeModifier cur = maxHealth.getModifier(HEALTH_MOD);
            if (target > -EPS) {
                if (cur != null) maxHealth.removeModifier(HEALTH_MOD);
            } else if (cur == null || Math.abs(cur.amount() - target) > EPS) {
                maxHealth.addOrUpdateTransientModifier(new AttributeModifier(
                        HEALTH_MOD, target, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }

        // e4 力竭 不再有「持续扣血」的 tick 结算：诅咒态改成「造成伤害消耗精力值 + 低精力掉伤害」，
        // 全部在 onDealDamage / onDealDamagePost 里，见下方「造成伤害」段。

        // e2 血偿：禁疗窗口过期即清理
        Long at = LAST_DEALT.get(player.getUUID());
        if (at != null && now - at > BLOOD_WINDOW_TICKS) {
            LAST_DEALT.remove(player.getUUID());
        }
    }

    /** 已装备的人形护甲件数（头盔 / 胸甲 / 护腿 / 靴子，0~4） */
    private static int armorPieceCount(ServerPlayer player) {
        int n = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !player.getItemBySlot(slot).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    // ==================== 治疗 ====================

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            float amount = event.getAmount();
            if (amount <= 0.0f) return;

            // e1 竭泉：每持有 1 级生命恢复，受到治疗 -25%（上限 -50%）——治疗侧乘算
            if (CurseManager.on(player, D, 0)) {
                MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
                int level = regen == null ? 0 : regen.getAmplifier() + 1;
                if (level > 0) {
                    float cut = Math.min(REGEN_HEAL_CUT_CAP, REGEN_HEAL_CUT_PER_LEVEL * level);
                    event.setAmount(amount * (1.0f - cut));
                }
            }

            // e2 血偿：造成伤害后 5 秒内，自身治疗量 -50%——治疗侧乘算
            if (CurseManager.on(player, D, 1)) {
                Long at = LAST_DEALT.get(player.getUUID());
                if (at != null && player.level().getGameTime() - at <= BLOOD_WINDOW_TICKS) {
                    event.setAmount(event.getAmount() * BLOOD_HEAL_MULTIPLIER);
                }
            }

            float healed = event.getAmount();
            if (healed <= 0.0f) return;

            // e1 竭泉 反转：受到治疗 +50%——治疗侧乘算
            if (CurseManager.rev(player, D, 0)) {
                healed *= 1.5f;
                event.setAmount(healed);
            }

            // e2 血偿 反转：每次受到治疗时，对 4 格内敌人造成「治疗量 ×50%」的魔法伤害
            if (CurseManager.rev(player, D, 1)) {
                float dmg = healed * BLOOD_REV_DAMAGE_RATIO;
                if (dmg > 0.0f) {
                    AABB box = player.getBoundingBox().inflate(NEARBY_RADIUS);
                    for (LivingEntity enemy : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
                        if (enemy == player || !(enemy instanceof Enemy) || !enemy.isAlive()) continue;
                        enemy.hurt(player.damageSources().indirectMagic(player, player), dmg);
                    }
                }
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑥ 治疗结算异常", t);
        }
    }

    // ==================== 受伤 ====================

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            float d = event.getAmount();
            if (d <= 0.0f) return;

            // e7 穿甲 反转：每次受伤有 10% 概率无视这次伤害
            if (CurseManager.rev(player, D, 6) && player.getRandom().nextFloat() < DODGE_CHANCE) {
                event.setCanceled(true);
                return;
            }

            // e6 献祭：满血时受到伤害 +70%——受伤侧乘算
            if (CurseManager.on(player, D, 5)) {
                if (player.getHealth() >= player.getMaxHealth() - EPS) {
                    d *= FULL_HEALTH_TAKEN;
                }
            }
            // e6 献祭 反转：每损失 1% 最大生命，受到伤害 -0.5%——受伤侧乘算
            else if (CurseManager.rev(player, D, 5)) {
                float lostRatio = 1.0f - player.getHealth() / player.getMaxHealth();
                if (lostRatio > 0.0f) {
                    d *= Math.max(0.0f, 1.0f - LOST_HEALTH_REDUCTION * lostRatio * 100.0f);
                }
            }

            event.setAmount(d);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑥ 受伤结算异常", t);
        }
    }

    /**
     * e7 穿甲（诅咒态）：每次受伤有 **50% 的伤害无法吃到护甲值**。
     * <p>
     * NeoForge 1.21.1 的护甲削减在 {@link LivingDamageEvent.Pre} 之前已算完
     * （{@link DamageContainer.Reduction#ARMOR}），因此按 {@code ArmorPenHandler} 同款口径
     * 把「50% 的护甲削减量」加回：{@code 最终 = D0 - R/2}（D0 = 护甲前伤害，R = 护甲削减量）。
     */
    @SubscribeEvent
    public static void onArmorPierce(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            if (!CurseManager.on(player, D, 6)) return;
            float armorReduction = event.getContainer().getReduction(DamageContainer.Reduction.ARMOR);
            if (armorReduction <= 0.0f) return;
            event.setNewDamage(event.getNewDamage() + armorReduction * ARMOR_PIERCE_RATIO);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑥ 穿甲结算异常", t);
        }
    }

    // ==================== 造成伤害 ====================

    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        // 两个口径分开：
        //   direct = 本体直接挥出的伤害（e2 血偿 / e3 蚀力 只认这个，维持原有语义）；
        //   owner  = 这次伤害「算谁打的」——本体 / 弹幕 / 召唤物 / 第三方构造体（gytrinket）
        //            都沿归属链回退到这个玩家；e4 力竭按这个更宽的口径扣精力。
        ServerPlayer direct = event.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        ServerPlayer owner = direct != null ? direct : damageOwner(event.getSource());
        if (owner == null) return;
        try {
            float d = event.getNewDamage();
            if (d <= 0.0f) return;

            boolean byDirect = direct != null && target != direct;

            // e2 血偿：记录「造成伤害」的时刻（诅咒态的 5 秒禁疗窗口从这一刻起算）
            if (byDirect) {
                LAST_DEALT.put(direct.getUUID(), direct.level().getGameTime());
            }

            // e3 蚀力：全伤害 -10%——输出侧乘算（只认本体直伤）
            if (byDirect && CurseManager.on(direct, D, 2)) {
                d *= 1.0f - EROSION;
            }

            // e4 力竭 诅咒态：**每造成一次伤害**固定 -60 精力，来源只要是这个玩家（本体 / 弹幕 /
            // 召唤物 / 构造体）都算；**出手时**精力低于 10% ⇒ 这一击伤害 -30%
            // （用消耗前的比例判定：挥出时状态如何就如何结算，避免这一击自己把精力打空又反过来罚自己）
            boolean byOwner = target != owner && CurseManager.on(owner, D, 3);
            if (byOwner) {
                double ratio = ParagliderStamina.ratio(owner);
                ParagliderStamina.consume(owner, STAMINA_PER_HIT);
                if (!Double.isNaN(ratio) && ratio < LOW_STAMINA_RATIO) {
                    d *= 1.0f - LOW_STAMINA_LOSS;
                }
            }

            // ---- 反转态加算统一收口（§2：d × (1 + Σ 反转修正)）----
            float bonus = 0.0f;
            // e3 蚀力 反转：全伤害 +10%（只认本体直伤）
            if (byDirect && CurseManager.rev(direct, D, 2)) {
                bonus += EROSION;
            }
            // e4 力竭 反转：精力高于 50% 时，造成伤害 +15%（并入 Σ 加算；同样按归属玩家算）
            if (target != owner && CurseManager.rev(owner, D, 3)) {
                double ratio = ParagliderStamina.ratio(owner);
                if (!Double.isNaN(ratio) && ratio > HIGH_STAMINA_RATIO) {
                    bonus += HIGH_STAMINA_BONUS;
                }
            }
            if (bonus != 0.0f) {
                d *= 1.0f + bonus;
            }

            event.setNewDamage(d);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑥ 造成伤害结算异常", t);
        }
    }

    /**
     * 这次伤害「算谁打的」：先看 causing entity，再沿归属链回退一层。
     * <p>
     * 覆盖范围（按 {@link com.plumejade.lensouls.util.AllyFilter#playerOwner}）：玩家本体 /
     * 玩家射出的弹幕（{@code Projectile#getOwner}）/ 实现 {@code OwnableEntity} 或
     * {@code TraceableEntity} 的召唤物与第三方构造体（gytrinket 的
     * {@code AbstractConstructEntity} 就实现 {@code OwnableEntity}，无人机/僚机打的都回落到玩家）。
     * <p>
     * 找不到玩家（野生怪、别人的构造体）返回 {@code null}，调用方直接跳过。
     */
    private static ServerPlayer damageOwner(DamageSource src) {
        if (src == null) return null;
        Entity causing = src.getEntity();
        Player p = AllyFilter.playerOwner(causing);
        if (p instanceof ServerPlayer sp) return sp;
        Entity direct = src.getDirectEntity();
        p = AllyFilter.playerOwner(direct);
        return p instanceof ServerPlayer sp ? sp : null;
    }

    /**
     * e4 力竭 的反转条件：**精力低于 50% 时，一次打出 5000 点伤害**。
     * <p>
     * 用 {@code LivingDamageEvent.Post} 的最终伤害判（护甲 / 减伤 / 我们的乘区都算完），
     * 不是面板值 —— 与 ⑤ 的统计口径保持一致；单次命中即达标，不做累计。
     */
    @SubscribeEvent
    public static void onDealDamagePost(LivingDamageEvent.Post event) {
        ServerPlayer owner = damageOwner(event.getSource());
        if (owner == null || event.getEntity() == owner) return;
        try {
            if (!CurseManager.on(owner, D, 3)) return;
            if (event.getNewDamage() < ONESHOT_DAMAGE) return;
            double ratio = ParagliderStamina.ratio(owner);
            if (Double.isNaN(ratio) || ratio >= ONESHOT_STAMINA_RATIO) return;
            CurseManager.tick(owner, D, 3, 1L, 1L);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑥ e4 反转条件结算异常", t);
        }
    }

    // ==================== 死亡 / 击杀（反转条件） ====================

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            LAST_DEALT.remove(player.getUUID());
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑥ 死亡清理异常", t);
        }
    }

    /**
     * 反转条件：击杀指定 BOSS。
     * <p>
     * 7 条各配一只，命中即 {@link CurseManager#tick} 一次（{@code goal = 1}）→ 立即永久反转。
     */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() == player) return;
        try {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
            if (id == null) return;
            Integer entry = BOSS_ENTRY.get(id.toString());
            if (entry == null) return;
            CurseManager.tick(player, D, entry, 1L, 1L);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑥ 反转条件结算异常", t);
        }
    }

    // ==================== 生命周期 / 防跨存档污染 ====================

    /** 清掉该玩家的瞬时计时（退出世界时调用） */
    public static void clear(UUID id) {
        if (id != null) LAST_DEALT.remove(id);
    }

    /**
     * 退出世界即清瞬时计时。
     * <p>
     * 静态 Map 按 UUID 存，而单机玩家 UUID 由名字派生 —— 换个存档还是同一个 UUID，
     * 不清会把上个存档的「最近一次造成伤害的时刻」带进新存档（跨存档污染）。
     * 真正的进度在 {@code CurseStateData}（玩家附件 + copyOnDeath），**跨死亡保留**。
     */
    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) clear(event.getEntity().getUUID());
    }
}
