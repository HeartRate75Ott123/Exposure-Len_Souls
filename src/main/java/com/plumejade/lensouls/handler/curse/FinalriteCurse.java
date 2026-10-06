package com.plumejade.lensouls.handler.curse;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.feather.CurseDef;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import com.plumejade.lensouls.mixin.EntityInvulnerableTimeAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
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
 * 条目序号（0 基）与 lang 的 {@code eN} 一致：e1=竭泉 e2=血偿 e3=蚀力 e4=枯井
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
    /** e4 枯井：扣血间隔（5 秒 = 100 tick） */
    private static final long DRAIN_INTERVAL_TICKS = 100L;
    /** e2 / e4 共用的「周围 4 格」检索半径 */
    private static final double NEARBY_RADIUS = 4.0;
    /** e4 枯井 反转：每 1 个掉落物增加 1% 伤害，上限 +20% */
    private static final int DROP_BONUS_CAP = 20;
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
    private static final Map<String, Integer> BOSS_ENTRY = Map.of(
            "minecraft:warden", 0,                                // e1 竭泉 · 监守者
            "legendary_monsters:posessed_paladin", 1,              // e2 血偿 · 堕落圣骑
            "cataclysm:ignis", 2,                                  // e3 蚀力 · 炎魔 Ignis
            "legendary_monsters:overgrown_colossus", 3,             // e4 枯井 · 蔓生巨像
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

        // e4 枯井：周围 4 格没有掉落物 → 持续扣血，每 5 秒 1 点
        if (CurseManager.on(player, D, 3)
                && !player.isCreative() && !player.isSpectator()
                && now % DRAIN_INTERVAL_TICKS == 0L
                && nearbyDrops(player) == 0) {
            // 与 ④ 自闭同口径：先清无敌帧，保证这 1 点必中，不被上一次受击吞掉
            ((EntityInvulnerableTimeAccessor) (Object) player).lensouls$setInvulnerableTime(0);
            player.hurt(player.damageSources().magic(), 1.0f);
        }

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

    /** 玩家周围 4 格内的掉落物数量 */
    private static int nearbyDrops(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(NEARBY_RADIUS);
        return player.level().getEntitiesOfClass(ItemEntity.class, box).size();
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
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;
        try {
            float d = event.getNewDamage();
            if (d <= 0.0f) return;

            // e2 血偿：记录「造成伤害」的时刻（诅咒态的 5 秒禁疗窗口从这一刻起算）
            LAST_DEALT.put(player.getUUID(), player.level().getGameTime());

            // e3 蚀力：全伤害 -10%——输出侧乘算
            if (CurseManager.on(player, D, 2)) {
                d *= 1.0f - EROSION;
            }

            // ---- 反转态加算统一收口（§2：d × (1 + Σ 反转修正)）----
            float bonus = 0.0f;
            // e3 蚀力 反转：全伤害 +10%
            if (CurseManager.rev(player, D, 2)) {
                bonus += EROSION;
            }
            // e4 枯井 反转：周围 4 格内每有 1 个掉落物，造成伤害 +1%（上限 +20%）
            if (CurseManager.rev(player, D, 3)) {
                bonus += 0.01f * Math.min(DROP_BONUS_CAP, nearbyDrops(player));
            }
            if (bonus != 0.0f) {
                d *= 1.0f + bonus;
            }

            event.setNewDamage(d);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑥ 造成伤害结算异常", t);
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
}
