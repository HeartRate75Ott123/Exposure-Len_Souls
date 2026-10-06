package com.plumejade.lensouls.handler.curse;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.feather.CurseDef;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ⑤ 羽·断时炉心（铁序 · 归一之律）23 条枷锁的效果。
 * <p>
 * <b>统一写法</b>：每条都先过 {@link CurseManager#on} / {@link CurseManager#rev}
 * （= 该款被选进槽位 ∧ 该条未反转 / 已反转），条件进度用
 * {@link CurseManager#tick} 累加，达标即自动反转。
 * <p>
 * <b>叠加口径</b>：诅咒态**乘算**、反转态**加算**（见设计文档 §2「叠加口径（定稿）」）。
 * 本类里的诅咒态一律用 {@code d * k}，反转态用 {@code d * (1 + 加算值)}。
 * <p>
 * 条目序号（0 基）与 lang 的 {@code eN} 一致：e1=绝疗 e2=饥禁 e3=迟凿 e4=徒劳 e5=盈伤 e6=坠渊
 * e7=钝锋 e8=止息 e9=断粮 e10=夺佑 e11=锈甲 e12=缚己 e13=余悸 e14=裁影 e15=厄运 e16=释刃
 * e17=重负 e18=滞步 e19=障目 e20=垂翅 e21=无援 e22=封镜 e23=平势。
 */
public final class TimecoreCurse {

    private static final CurseDef D = CurseDefs.TIMECORE;

    private static final ResourceLocation ARMOR_MOD = ResourceLocation.parse("lensouls:curse_rust_armor");
    private static final ResourceLocation WEIGHT_MOD = ResourceLocation.parse("lensouls:curse_weight");

    /** e8 止息：连续无治疗计时（tick） */
    private static final Map<UUID, Long> NO_HEAL_TICKS = new HashMap<>();
    /** e2 饥禁：饱食度 <5 的累计 tick */
    private static final Map<UUID, Long> STARVE_TICKS = new HashMap<>();
    /** e2/e9：上一 tick 的饱食度（检测"进食"） */
    private static final Map<UUID, Integer> LAST_FOOD = new HashMap<>();
    /** e17 重负：上一 tick 的位置（累计移动距离） */
    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
    /** e18 滞步：缓慢状态下的走地距离（格） */
    private static final Map<UUID, Double> SLOW_WALK = new HashMap<>();
    /** e13 余悸：大击窗口到期 tick */
    private static final Map<UUID, Long> FEAR_UNTIL = new HashMap<>();

    private TimecoreCurse() {
    }

    // ==================== 每 tick ====================

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            tick(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑤ 每 tick 结算异常", t);
        }
    }

    private static void tick(ServerPlayer player) {
        boolean active = CurseManager.isActive(player, D);

        // e10 夺佑：抗性提升无效（持续清除）
        if (CurseManager.on(player, D, 9)) {
            player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }

        // e9 断粮：永久饥饿 3
        if (CurseManager.on(player, D, 8)) {
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 2, true, false, false));
        }

        // e2/e9：进食检测（饱食度上升 = 刚吃过东西）
        int food = player.getFoodData().getFoodLevel();
        Integer prev = LAST_FOOD.put(player.getUUID(), food);
        boolean ate = prev != null && food > prev;

        // e9 断粮 反转：每恢复 1 点饱食度 → 恢复 1 点生命
        if (ate && CurseManager.rev(player, D, 8)) {
            player.heal(food - prev);
        }

        // e2 饥禁 反转：进食额外获得 3 秒生命恢复 II
        if (ate && CurseManager.rev(player, D, 1)) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, true, false, false));
        }

        // e2 饥禁 条件：饱食度 <5 状态下存活 10 分钟（12000 tick）
        if (CurseManager.on(player, D, 1) && food < 5) {
            long n = STARVE_TICKS.merge(player.getUUID(), 1L, Long::sum);
            CurseManager.tick(player, D, 1, 1L, 12000L);
            if (n >= 12000L) STARVE_TICKS.remove(player.getUUID());
        } else {
            STARVE_TICKS.remove(player.getUUID());
        }

        // e8 止息 条件：连续 5 分钟不获得任何治疗并存活（6000 tick）
        if (CurseManager.on(player, D, 7)) {
            long n = NO_HEAL_TICKS.merge(player.getUUID(), 1L, Long::sum);
            CurseManager.tick(player, D, 7, 1L, 6000L);
            if (n >= 6000L) NO_HEAL_TICKS.remove(player.getUUID());
        } else {
            NO_HEAL_TICKS.remove(player.getUUID());
        }

        // e11 锈甲：护甲 ×40%（诅咒态）/ 取消（反转态）
        var armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            boolean rust = CurseManager.on(player, D, 10);
            boolean has = armor.getModifier(ARMOR_MOD) != null;
            if (rust && !has) {
                armor.addOrUpdateTransientModifier(new AttributeModifier(
                        ARMOR_MOD, -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            } else if (!rust && has) {
                armor.removeModifier(ARMOR_MOD);
            }
        }

        // e17 重负：背包每有 1 个物品移速 -0.5%（上限 -70%）；反转后不再减速
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            boolean on = CurseManager.on(player, D, 16);
            double amount = 0.0;
            if (on) {
                int count = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (!player.getInventory().getItem(i).isEmpty()) count++;
                }
                amount = Math.max(-0.70, -0.005 * count);
            }
            AttributeModifier cur = speed.getModifier(WEIGHT_MOD);
            if (amount < 0.0) {
                if (cur == null || Math.abs(cur.amount() - amount) > 1.0e-4) {
                    speed.addOrUpdateTransientModifier(new AttributeModifier(
                            WEIGHT_MOD, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            } else if (cur != null) {
                speed.removeModifier(WEIGHT_MOD);
            }
        }

        // e17 条件：移速 ≤-30% 时累计移动 3000 格
        double[] last = LAST_POS.get(player.getUUID());
        double[] now = {player.getX(), player.getY(), player.getZ()};
        if (last != null && active) {
            double dist = Math.sqrt(Math.pow(now[0] - last[0], 2) + Math.pow(now[2] - last[2], 2));
            if (dist > 0 && dist < 8.0) {
                // 移速加成 ≤ -30%（即属性修饰符 ≤ -0.3）
                AttributeModifier mod = speed != null ? speed.getModifier(WEIGHT_MOD) : null;
                if (CurseManager.on(player, D, 16) && mod != null && mod.amount() <= -0.30) {
                    CurseManager.tick(player, D, 16, (long) Math.ceil(dist), 3000L);
                }
                // e18 滞步 条件：缓慢状态下累计走地 10000 格
                if (CurseManager.on(player, D, 17) && player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    double walked = SLOW_WALK.merge(player.getUUID(), dist, Double::sum);
                    CurseManager.tick(player, D, 17, (long) Math.ceil(dist), 10000L);
                    if (walked >= 10000.0) SLOW_WALK.remove(player.getUUID());
                }
            }
        }
        LAST_POS.put(player.getUUID(), now);

        // e18 滞步 反转：免疫缓慢
        if (CurseManager.rev(player, D, 17)) {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        // e19 障目 反转：免疫失明
        if (CurseManager.rev(player, D, 18)) {
            player.removeEffect(MobEffects.BLINDNESS);
        }

        // e13 余悸：大击窗口过期即清除
        Long until = FEAR_UNTIL.get(player.getUUID());
        if (until != null && player.level().getGameTime() > until) FEAR_UNTIL.remove(player.getUUID());
    }

    // ==================== 治疗 ====================

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float amount = event.getAmount();
        if (amount <= 0.0f) return;

        // e1 绝疗：受到治疗 -80%
        if (CurseManager.on(player, D, 0)) {
            event.setAmount(amount * 0.2f);
        }
        // e2 饥禁：饱食度 <5 时禁疗
        if (CurseManager.on(player, D, 1) && player.getFoodData().getFoodLevel() < 5) {
            event.setAmount(0.0f);
            return;
        }
        // e8 止息：生命自然恢复与再生类效果无效
        if (CurseManager.on(player, D, 7)) {
            event.setAmount(0.0f);
            return;
        }

        float healed = event.getAmount();
        if (healed <= 0.0f) return;

        // e8 止息：有治疗 ⇒ 计时清零
        NO_HEAL_TICKS.remove(player.getUUID());

        // e1 绝疗 条件：累计治疗 2000 点生命（按实际到手计）
        if (CurseManager.on(player, D, 0)) {
            CurseManager.tick(player, D, 0, (long) Math.ceil(healed), 2000L);
        }

        // e1 绝疗 反转：治疗溢出 ×2 转为吸收生命（上限 30% 最大生命）
        if (CurseManager.rev(player, D, 0)) {
            float missing = player.getMaxHealth() - player.getHealth();
            float overflow = healed - missing;
            if (overflow > 0.0f) {
                int cap = (int) (player.getMaxHealth() * 0.30f);
                int gain = (int) Math.min(cap, overflow * 2.0f);
                int levels = Math.max(1, gain / 4);   // 原版吸收每级 4 点
                MobEffectInstance cur = player.getEffect(MobEffects.ABSORPTION);
                int keep = cur == null ? 0 : cur.getAmplifier() + 1;
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600,
                        Math.min(4, Math.max(keep, levels) - 1), true, false, false));
            }
        }
    }

    // ==================== 挖掘 ====================

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        // e3 迟凿：挖掘速度 -50% / 反转 +50%
        if (CurseManager.on(sp, D, 2)) {
            event.setNewSpeed(event.getNewSpeed() * 0.5f);
        } else if (CurseManager.rev(sp, D, 2)) {
            event.setNewSpeed(event.getNewSpeed() * 1.5f);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        BlockState state = event.getState();

        // e3 迟凿 条件：累计挖掘 1000 个方块
        if (CurseManager.on(player, D, 2)) {
            CurseManager.tick(player, D, 2, 1L, 1000L);
        }

        // e4 徒劳：挖任意原版方块 30% 概率不掉落（用 BlockDropsEvent 清空；这里只做概率与计数）
        if (CurseManager.on(player, D, 3) && isVanillaBlock(state)) {
            if (player.getRandom().nextFloat() < 0.30f) {
                SWALLOW_FLAG.add(player.getUUID());
                CurseManager.tick(player, D, 3, 1L, 150L);
            }
        }
    }

    /** 本次破坏是否要吞掉落（由 BlockDropsEvent 消费） */
    private static final java.util.Set<UUID> SWALLOW_FLAG = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onBlockDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;
        if (SWALLOW_FLAG.remove(player.getUUID())) {
            event.getDrops().clear();
            return;
        }
        // e4 徒劳 反转：不再吞掉落，且**时运 +2**（按原版公式模拟，等同于工具上带时运 II）
        if (CurseManager.rev(player, D, 3) && !event.getDrops().isEmpty()) {
            int extra = fortuneExtra(player, 2);
            var src = event.getDrops().get(0);
            for (int i = 0; i < extra; i++) {
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                        player.level(), src.getX(), src.getY(), src.getZ(), src.getItem().copy()));
            }
        }
    }

    /** 原版「时运」掉落公式：额外数量 = rand(0..level+1) - 1，下限 0 */
    private static int fortuneExtra(ServerPlayer player, int level) {
        return Math.max(0, player.getRandom().nextInt(level + 2) - 1);
    }

    /** 原版「抢夺」掉落公式：额外数量 = rand(0..level) */
    private static int lootingExtra(ServerPlayer player, int level) {
        return player.getRandom().nextInt(level + 1);
    }

    /**
     * e15 厄运：时运 -2 / 抢夺 -2（诅咒态）→ 时运 +2 / 抢夺 +2（反转态）。
     * <p>
     * 原版「时运/抢夺」是附魔等级，没有对应属性可加，这里用**掉落物数量**近似：
     * 诅咒态约一半概率少掉一件，反转态追加最多 2 件。BOSS 掉魂 +50% 在
     * {@code CopySoulDropHandler} 侧（未接，见交付说明）。
     */
    @SubscribeEvent
    public static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() instanceof Player) return;
        var drops = event.getDrops();
        if (drops.isEmpty()) return;

        if (CurseManager.on(player, D, 14)) {
            if (player.getRandom().nextFloat() < 0.5f) drops.remove(drops.size() - 1);
            return;
        }
        if (CurseManager.rev(player, D, 14)) {
            // e15 厄运 反转：**抢夺 +2**（按原版公式模拟，等同于武器上带抢夺 II）
            java.util.List<net.minecraft.world.entity.item.ItemEntity> copies = new java.util.ArrayList<>();
            int extra = lootingExtra(player, 2);
            int n = 0;
            for (var src : drops) {
                if (n++ >= extra) break;
                copies.add(new net.minecraft.world.entity.item.ItemEntity(
                        player.level(), src.getX(), src.getY(), src.getZ(), src.getItem().copy()));
            }
            drops.addAll(copies);
        }
    }

    private static boolean isVanillaBlock(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    // ==================== 受伤 ====================

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float d = event.getAmount();
        if (d <= 0.0f) return;

        // e5 盈伤：生命 >50% 时受到伤害 +50%（乘算）/ 反转 -25%（加算）
        if (player.getHealth() > player.getMaxHealth() * 0.5f) {
            if (CurseManager.on(player, D, 4)) {
                d *= 1.5f;
            } else if (CurseManager.rev(player, D, 4)) {
                d *= 0.75f;
            }
        }

        // e6 坠渊：摔落伤害 +500%（乘算）/ 反转免疫
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            if (CurseManager.on(player, D, 5)) {
                d *= 6.0f;
            } else if (CurseManager.rev(player, D, 5)) {
                event.setCanceled(true);
                return;
            }
            // e6 条件：单次摔落伤害 ≥15 点并存活
            if (CurseManager.on(player, D, 5) && d >= 15.0f) {
                CurseManager.tick(player, D, 5, 1L, 1L);
            }
        }

        // e13 余悸：受到超过 40% 最大生命的一击后，5 秒内受伤 +20%（乘算）/ 反转 -70%（加算）
        if (d > player.getMaxHealth() * 0.40f) {
            FEAR_UNTIL.put(player.getUUID(), player.level().getGameTime() + 100L);
            // 条件：累计承受 30 次（死亡即重置）
            if (CurseManager.on(player, D, 12)) {
                CurseManager.tick(player, D, 12, 1L, 30L);
            }
        } else if (FEAR_UNTIL.containsKey(player.getUUID())) {
            if (CurseManager.on(player, D, 12)) {
                d *= 1.2f;
            } else if (CurseManager.rev(player, D, 12)) {
                d *= 0.3f;
            }
        }

        // e18/e19 受击概率施加负面（仅诅咒态）
        if (CurseManager.on(player, D, 17) && player.getRandom().nextFloat() < 0.25f) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, false, false));
        }
        if (CurseManager.on(player, D, 18) && player.getRandom().nextFloat() < 0.25f) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false, false));
        }

        // e10 夺佑 条件：无任何正面效果时累计承受 5000 点伤害并存活
        if (CurseManager.on(player, D, 9) && player.getActiveEffects().isEmpty()) {
            CurseManager.tick(player, D, 9, (long) Math.ceil(d), 5000L);
        }

        // e11 锈甲 条件：护甲值 ≤5 时累计承受 2000 点物理伤害
        if (CurseManager.on(player, D, 10)
                && player.getArmorValue() <= 5
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE) == false
                && event.getSource().getDirectEntity() instanceof LivingEntity) {
            CurseManager.tick(player, D, 10, (long) Math.ceil(d), 2000L);
        }

        event.setAmount(d);
    }

    // ==================== 造成伤害 ====================

    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;
        float d = event.getNewDamage();
        if (d <= 0.0f) return;

        // e7 钝锋：造成所有伤害 -10%（乘算）；反转：无视 10% 护甲（在本事件里体现为 +10% 伤害）
        if (CurseManager.on(player, D, 6)) {
            d *= 0.9f;
            CurseManager.tick(player, D, 6, (long) Math.ceil(d), 10000L);
        } else if (CurseManager.rev(player, D, 6)) {
            d *= 1.10f;
        }

        // e12 缚己：每有一点护甲值，伤害 -0.5%（乘算）
        if (CurseManager.on(player, D, 11)) {
            d *= Math.max(0.1f, 1.0f - 0.005f * player.getArmorValue());
        } else if (CurseManager.rev(player, D, 11)) {
            // 反转：不再因护甲损失伤害 + 每点护甲 1% 概率清空对方护甲
            if (player.getRandom().nextFloat() < Math.min(1.0f, 0.01f * player.getArmorValue())) {
                var armor = target.getAttribute(Attributes.ARMOR);
                if (armor != null) {
                    armor.addOrUpdateTransientModifier(new AttributeModifier(
                            ResourceLocation.parse("lensouls:curse_armor_strip"),
                            -armor.getValue(), AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }

        // e16 释刃：造成伤害时 2% 概率把主手武器丢出去
        if (CurseManager.on(player, D, 15) && player.getRandom().nextFloat() < 0.02f) {
            ItemStack main = player.getMainHandItem();
            if (!main.isEmpty()) {
                ItemStack drop = main.copy();
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.drop(drop, true);
                CurseManager.tick(player, D, 15, 1L, 200L);
            }
        }

        // e20 垂翅：飞行/腾空时造成伤害 -50%（乘算）
        if (CurseManager.on(player, D, 19) && !player.onGround()) {
            d *= 0.5f;
            CurseManager.tick(player, D, 19, (long) Math.ceil(d), 1000L);
        }

        // e10 夺佑 反转：每有一级抗性提升，伤害 +5%（加算）
        if (CurseManager.rev(player, D, 9)) {
            MobEffectInstance res = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
            int level = res == null ? 0 : res.getAmplifier() + 1;
            if (level > 0) d *= (1.0f + 0.05f * level);
        }

        // e23 平势：目标最大生命比你高一倍时，它被你打就反击你
        levelFieldCounter(player, target);

        event.setNewDamage(d);
    }

    // ==================== 死亡 / 击杀 ====================

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // e13 余悸：死亡即重置计数
        CurseManager.resetProgress(player, D, 12);
        FEAR_UNTIL.remove(player.getUUID());
        NO_HEAL_TICKS.remove(player.getUUID());
        STARVE_TICKS.remove(player.getUUID());
        LAST_POS.remove(player.getUUID());
        LAST_FOOD.remove(player.getUUID());
        SLOW_WALK.remove(player.getUUID());

        // e11 锈甲 反转：受近战伤害时反弹 25%（放在死亡前结算里不合适，见 onDealDamage 外的反弹逻辑）
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() == player) return;

        // e15 厄运 条件：累计击杀 500 只生物
        if (CurseManager.on(player, D, 14)) {
            CurseManager.tick(player, D, 14, 1L, 500L);
        }
        // e19 障目 条件：在失明状态下击杀 50 只生物
        if (CurseManager.on(player, D, 18) && player.hasEffect(MobEffects.BLINDNESS)) {
            CurseManager.tick(player, D, 18, 1L, 50L);
        }
        // e23 平势 条件：击杀暮色巫妖
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (id != null && "twilightforest:lich".equals(id.toString())) {
            CurseManager.tick(player, D, 22, 1L, 1L);
        }
    }

    /** e11 锈甲 反转：受近战伤害时反弹 25% 给攻击者 */
    @SubscribeEvent
    public static void onReflect(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CurseManager.rev(player, D, 10)) return;
        if (!(event.getSource().getDirectEntity() instanceof LivingEntity attacker)) return;
        if (event.getSource().getDirectEntity() instanceof Projectile) return;
        attacker.hurt(player.damageSources().thorns(player), event.getAmount() * 0.25f);
    }

    // ==================== e21 无援 ====================

    /** e21 无援：不死图腾失效（诅咒态）；反转后照常触发并给 10 秒强化 */
    @SubscribeEvent
    public static void onUseTotem(net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (CurseManager.on(player, D, 20)) {
            event.setCanceled(true);
            return;
        }
        if (CurseManager.rev(player, D, 20)) {
            // 反转：保命手段恢复；触发后 10 秒伤害提升 + 抗性提升（"免疫负面"用高抗性近似）
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1, true, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2, true, false, false));
        }
    }

    /** e21 无援 条件：生命 ≤2 时用拍立得拍 10 张瞬间照片（由 PhotoInjectionHandler 回调） */
    public static void onInstantPhotoAtLowHealth(ServerPlayer player) {
        if (!CurseManager.on(player, D, 20)) return;
        if (player.getHealth() > 2.0f) return;
        CurseManager.tick(player, D, 20, 1L, 10L);
    }

    /** e23 平势：目标最大生命比你高一倍时，它被你打就反击你（诅咒态） */
    public static void levelFieldCounter(ServerPlayer player, LivingEntity target) {
        if (!CurseManager.on(player, D, 22)) return;
        if (target.getMaxHealth() < player.getMaxHealth() * 2.0f) return;
        player.hurt(player.damageSources().mobAttack(target), 2.0f);
    }

    /**
     * e12 缚己 条件：护甲值 ≥30 时，用「夺魂索命」累计造成 10000 点伤害。
     * <p>
     * 夺魂索命是 {@code target.setHealth(...)} 直接削减，**不走伤害事件**，
     * 所以由 {@code SoulSeverHandler} 在削减处回报实际削减量。
     */
    public static void onSoulSever(ServerPlayer player, float amount) {
        if (player == null || amount <= 0.0f) return;
        if (player.getArmorValue() < 30) return;
        if (!CurseManager.on(player, D, 11)) return;
        CurseManager.tick(player, D, 11, (long) Math.ceil(amount), 10000L);
    }

    // ==================== 生命周期 / 防跨存档污染 ====================

    /** 清掉该玩家的所有瞬时计时（退出世界 / 死亡时调用） */
    public static void clear(UUID id) {
        if (id == null) return;
        NO_HEAL_TICKS.remove(id);
        STARVE_TICKS.remove(id);
        LAST_FOOD.remove(id);
        LAST_POS.remove(id);
        SLOW_WALK.remove(id);
        FEAR_UNTIL.remove(id);
        SWALLOW_FLAG.remove(id);
    }

    /**
     * 退出世界即清瞬时计时。
     * <p>
     * <b>为什么必须清</b>：这些计时器是**静态 Map 按 UUID 存**的，而单机里玩家 UUID 由名字派生
     * —— 换个存档还是同一个 UUID。不清的话「上个存档里连续 5 分钟没治疗」这类计时会**带到下个存档**
     * （跨存档污染）。
     * <p>
     * 真正的进度（{@code CurseStateData.progress}）是玩家附件 + {@code copyOnDeath}，
     * **跨死亡保留**，不受这里影响。
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) clear(event.getEntity().getUUID());
    }
}
