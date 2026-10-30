package com.plumejade.lensouls.handler.curse;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.feather.CurseDef;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import com.plumejade.lensouls.feather.CurseSideQuery;
import com.plumejade.lensouls.handler.CurioSlotBonuses;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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

    /** 单次受击的**有限**上限：超过它的（/kill、必死级）不需要倍率，直接按这个值走，避免 ×倍率后溢出成 Infinity */
    private static final float MAX_SAFE_DAMAGE = 1.0e6f;

    private static final ResourceLocation ARMOR_MOD = ResourceLocation.parse("lensouls:curse_rust_armor");
    private static final ResourceLocation WEIGHT_MOD = ResourceLocation.parse("lensouls:curse_weight");

    /**
     * e17 条件：移动速度**属性值** &lt; 0.02（原版玩家基础值 0.1 ⇒ ≈ 总减速 ≥80%）。
     * 文案与 e10 夺佑 / ④ e11 疯狂同款：直接说「速度值 < 0.02」，不写百分比。
     * <p>
     * 直接读最终属性值（含缓慢、e17 重负、减速铁板、照片等全部修饰符），<b>不读单条修饰符的 amount()</b>：
     * 原版 {@code generic.movement_speed} 下限是 0（按「-30%」这种负值判永远不成立），而 e17 自己的
     * {@code WEIGHT_MOD} 最多 {@code -0.005 × 41 格 = -20.5%}，从来摸不到 -30% —— 旧写法
     * {@code mod.amount() <= -0.30} 恒为 false，本条进度永远是 0、无法反转。
     */
    private static final double E17_SPEED_THRESHOLD = 0.02;

    /** e8 止息：连续无治疗计时（tick） */
    private static final Map<UUID, Long> NO_HEAL_TICKS = new HashMap<>();
    /** e2 饥禁：饱食度 <5 的累计 tick */
    private static final Map<UUID, Long> STARVE_TICKS = new HashMap<>();
    /** e2/e9：上一 tick 的饱食度（检测"进食"） */
    private static final Map<UUID, Integer> LAST_FOOD = new HashMap<>();
    /** e17 重负 / e18 滞步：上一 tick 的**水平**位置（只算 XZ，见位移结算处注释） */
    private static final Map<UUID, Pos> LAST_POS = new HashMap<>();
    /** e17/e18 真格累加的余数（下标 0=e17、1=e18）：慢走 0.2 格/tick 不能靠 ceil 记成 1 格 */
    private static final Map<UUID, double[]> MOVE_CARRY = new HashMap<>();
    private static final int MOVE_SLOT_E17 = 0;
    private static final int MOVE_SLOT_E18 = 1;
    /** e13 余悸：大击窗口到期 tick */
    private static final Map<UUID, Long> FEAR_UNTIL = new HashMap<>();
    /** e23 平势：本 tick 已经反击过（多段/范围伤害同一 tick 只反击一次） */
    private static final Map<UUID, Long> LAST_FIELD_TICK = new HashMap<>();

    /**
     * 「我们自己造成的伤害」重入深度（线程内）。
     * <p>
     * 本类有三处在**伤害结算内部**再造成伤害：e23 平势 诅咒态（它被你打就反击你）、
     * e11 锈甲 反转态（反弹 25% 给攻击者）、e23 反转态（20% 反制）。
     * 这些伤害会重新派发 {@code LivingIncomingDamageEvent} / {@code LivingDamageEvent.Pre}，
     * 于是形成闭环：{@code 玩家打 BOSS → levelFieldCounter 让玩家受伤 → onReflect 反弹给 BOSS
     * → BOSS 受伤（来源是玩家）→ onDealDamage → levelFieldCounter → …}，
     * 实机表现就是 {@code java.lang.StackOverflowError: Exception in server tick loop}
     * （错误报告里 63 层 {@code onDealDamage/levelFieldCounter/onReflect} 循环即此）。
     * <p>
     * 标记为「自家伤害」后，本类的三个伤害回调直接返回 —— 我们自己安排的那一份伤害
     * 只按字面数值结算，不再被自己的倍率、反弹、反制加工一次。
     */
    private static final ThreadLocal<int[]> SELF_DAMAGE_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

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

        // e9 断粮：永久饥饿 3（CurseEffects.keep：只在快过期时续期，避免每 tick 发效果包）
        if (CurseManager.on(player, D, 8)) {
            CurseEffects.keep(player, MobEffects.HUNGER, 60, 2);
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
            // 文案是「在饱食度 <5 状态下存活 10 分钟」⇒ 状态一断，进度必须清零（不是累计值）
            STARVE_TICKS.remove(player.getUUID());
            if (CurseManager.progress(player, D, 1) != 0L) {
                CurseManager.setProgress(player, D, 1, 0L);
            }
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
                    // 按**堆叠数量**累计（64 个方块算 64 件），不是按占用的槽位数——
                    // 41 个槽位 × -0.5% 最多只有 -20.5%，文案写的「上限 -70%」需要 140 件，
                    // 只有把堆叠数量算进来那条上限才有意义（140 件 = 比如 3 组铁 + 若干）。
                    // 时间口径仍是「当前背包」，丢掉物品立刻回落（属性修饰符每 tick 重算）。
                    count += player.getInventory().getItem(i).getCount();
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

        // e17 条件：移动速度值 <0.02（≈ 总减速 ≥80%）时累计移动 3000 格
        // e18 条件：缓慢状态下累计走地 10000 格（文案「走地」/设计文档「仅计地面位移」）
        // 距离口径：只算 XZ 水平位移（把 Y 算进去，跳跃/下落会白送进度），且**真格累加**——
        // 低移速下每 tick 只有 0.1~0.2 格，用 ceil 会把一个移动 tick 记成 1 格（进度放大 4~7 倍，
        // 「3000 格」实际变成 3000 tick = 150 秒）。余数留在 MOVE_CARRY 里等下一 tick 补满。
        Pos last = LAST_POS.get(player.getUUID());
        double px = player.getX();
        double pz = player.getZ();
        if (last != null && active) {
            double dx = px - last.x();
            double dz = pz - last.z();
            double dist = Math.sqrt(dx * dx + dz * dz);
            // >8 格/tick 只可能是 /tp、维度切换、重生传送，不计入
            if (dist > 0 && dist < 8.0) {
                // e17 骑乘不算：船/矿车/马的位移与**自身**移速无关（矿车约 8 格/秒，6 分钟就能刷满 3000 格），
                // 不排除会把「速度 <0.02」这道门整体架空。e18 刻意不加（用户定：它本就要求缓慢 + 走地）。
                if (CurseManager.on(player, D, 16) && !player.isPassenger()
                        && moveSpeedOf(player) < E17_SPEED_THRESHOLD) {
                    long cells = wholeCells(player.getUUID(), MOVE_SLOT_E17, dist);
                    if (cells > 0L) CurseManager.tick(player, D, 16, cells, 3000L);
                }
                if (CurseManager.on(player, D, 17) && player.onGround()
                        && player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    long cells = wholeCells(player.getUUID(), MOVE_SLOT_E18, dist);
                    if (cells > 0L) CurseManager.tick(player, D, 17, cells, 10000L);
                }
            }
        }
        LAST_POS.put(player.getUUID(), new Pos(px, pz));

        // e18 滞步 反转：免疫缓慢
        if (CurseManager.rev(player, D, 17)) {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        // e19 障目 反转：免疫失明
        if (CurseManager.rev(player, D, 18)) {
            player.removeEffect(MobEffects.BLINDNESS);
        }

        // e14 裁影：照片栏位 -2（诅咒态）/ +4（反转态）——「照片槽」就是 Curios 的 photograph 栏
        updatePhotoSlots(player);

        // e21 反转窗口内「免疫一切负面效果」
        if (CurseManager.rev(player, D, 20) && noAidGraceActive(player)) {
            purgeNegativeEffects(player);
        }
        // ⑤ 大成：23 条全部反转 → 全属性 +50%（属性修饰符）+ 常驻「免疫一切负面效果」
        boolean grand = grandActive(player);
        applyGrand(player, grand);
        if (grand) {
            purgeNegativeEffects(player);
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

        // ⑤ e8 反转的文案承诺「再生类效果正常生效」⇒ **生命恢复效果带来的治疗**不被 ⑤ 自己的
        // 恢复量拦截削减（e1 绝疗 −80% / e2 饥禁禁疗 / e8 自身都不动它）。
        // 作用域仅限 ⑤：⑥ 竭泉/血偿、④ 食之无味各自照旧按自己的诅咒态生效。
        boolean regenExempt = CurseManager.rev(player, D, 7) && HealSource.isRegenerationHeal();

        // e1 绝疗：受到治疗 -80%
        if (!regenExempt && CurseManager.on(player, D, 0)) {
            event.setAmount(amount * 0.2f);
        }
        // e2 饥禁：饱食度 <5 时禁疗（再生带来的治疗按 e8 反转承诺放行）
        if (!regenExempt && CurseManager.on(player, D, 1)
                && player.getFoodData().getFoodLevel() < 5) {
            event.setAmount(0.0f);
            return;
        }
        // e8 止息：生命自然恢复与再生类效果无效（已反转则本条本就不生效）
        if (CurseManager.on(player, D, 7)) {
            event.setAmount(0.0f);
            return;
        }

        float healed = event.getAmount();
        if (healed <= 0.0f) return;

        // e8 止息：有治疗 ⇒ 计时清零（文案是「连续 5 分钟不获得任何治疗」）
        NO_HEAL_TICKS.remove(player.getUUID());
        if (CurseManager.progress(player, D, 7) != 0L) {
            CurseManager.setProgress(player, D, 7, 0L);
        }

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

    /**
     * e3 迟凿：挖掘速度 −50%（诅咒态）/ +50%（反转态）。
     * <p>
     * <b>必须两端同口径</b>：{@code PlayerEvent.BreakSpeed} 由 {@code Player.getDigSpeed} 触发，
     * 客户端（{@code LocalPlayer}）与服务端（{@code ServerPlayer}）各算一次；而 1.21.1 的
     * {@code ServerPlayerGameMode} 会用自己的 {@code getDestroyProgress} 累积挖掘进度
     * （{@code incrementDestroyProgress} / {@code hasDelayedDestroy}）。只改服务端 ⇒
     * 客户端按原速挖、服务端按半速算，两边的进度/回执对不上 ——
     * 实机症状就是「挖完方块不消失，延迟 0.x 秒才消失并掉出掉落物」。
     * 客户端没有羽毛槽附件，改用 {@code CurseClientCache} 镜像（见 {@link CurseSideQuery}）。
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (sideOn(player, 2)) {
            event.setNewSpeed(event.getNewSpeed() * 0.5f);
        } else if (sideRev(player, 2)) {
            event.setNewSpeed(event.getNewSpeed() * 1.5f);
        }
    }

    /** 客户端镜像查询（由 {@code LenSoulsClient} 注入；专用服为 null） */
    private static CurseSideQuery CLIENT_QUERY;

    /** 仅供客户端初始化调用 */
    public static void setClientQuery(CurseSideQuery query) {
        CLIENT_QUERY = query;
    }

    /** 当前端上「该条诅咒态是否生效」：服务端读权威附件，客户端读同步镜像 */
    private static boolean sideOn(Player player, int entry) {
        if (player instanceof ServerPlayer sp) return CurseManager.on(sp, D, entry);
        return CLIENT_QUERY != null && CLIENT_QUERY.on(D, entry);
    }

    /** 当前端上「该条反转态是否生效」：服务端读权威附件，客户端读同步镜像 */
    private static boolean sideRev(Player player, int entry) {
        if (player instanceof ServerPlayer sp) return CurseManager.rev(sp, D, entry);
        return CLIENT_QUERY != null && CLIENT_QUERY.rev(D, entry);
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
                // 按**方块坐标**记账：创造模式 / 不可采掘破坏不会触发 BlockDropsEvent，
                // 用 UUID 记账会残留标志、误吞下一块掉落（见 onBlockDrops 与 onServerTick）
                SWALLOW_POS.add(event.getPos().immutable());
                CurseManager.tick(player, D, 3, 1L, 150L);
            }
        }
    }

    /**
     * 本次破坏是否要吞掉落（由 {@link #onBlockDrops} 消费）。
     * <p>
     * 记的是**方块坐标**而不是玩家 UUID：创造模式 / 不可采掘的破坏不会派发
     * {@code BlockDropsEvent}，UUID 版标志会残留到下一次真实破坏、把下一块掉落误吞。
     * 坐标版由 {@link #onServerTick} 每服务器 tick 清空一次，残留最多存活一个 tick。
     */
    private static final java.util.Set<BlockPos> SWALLOW_POS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 每服务器 tick（所有维度 tick 之后）清空一次「待吞掉落」记账，杜绝跨 tick 残留 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        try {
            if (!SWALLOW_POS.isEmpty()) SWALLOW_POS.clear();
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑤ e4 吞掉落记账清理异常", t);
        }
    }

    @SubscribeEvent
    public static void onBlockDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;
        if (SWALLOW_POS.remove(event.getPos())) {
            // **消费时必须回读当前状态**：记账写在 BreakEvent、消费写在掉落时，两者之间可能已经反转
            // （或换了佩戴者）。反转后不再吞，只把记录作废 —— 旧版这里不看状态，
            // 于是出现「徒劳已反转却还在吞掉落」。
            if (CurseManager.on(player, D, 3)) {
                event.getDrops().clear();
                return;
            }
            if (LenSouls.LOGGER.isDebugEnabled()) {
                LenSouls.LOGGER.debug("[Curse] ⑤ e4 吞掉落记录作废（消费时该条已反转）pos={}", event.getPos());
            }
        }
        // e15 厄运 诅咒态：时运 −2 ⇒ 平均少掉 0~2 件（至少保留 1 件）
        if (CurseManager.on(player, D, 14) && event.getDrops().size() > 1) {
            int cut = player.getRandom().nextInt(3);
            java.util.Iterator<net.minecraft.world.entity.item.ItemEntity> it = event.getDrops().iterator();
            while (it.hasNext() && cut > 0 && event.getDrops().size() > 1) {
                it.next();
                it.remove();
                cut--;
            }
        }

        // e4 徒劳 反转：不再吞掉落，且**时运 +2**。
        // 实现不在这里：见 CurseLootBonus / BlockFortuneDropMixin ——
        // 旧版「复制一件已有掉落物」等于对所有方块无条件加产量（石头也掉 2 个），
        // 现在改成「掉落上下文里的工具多 2 级时运」，由原版战利品表自己决定谁该多掉。
    }

    /**
     * e15 厄运：时运 −2 / 抢夺 −2（诅咒态）→ 时运 +2 / 抢夺 +2（反转态）。
     * <p>
     * <b>反转态的 +2 不在这里</b>：见 {@link CurseLootBonus}（按真正的附魔等级回话，
     * 只对原版写了抢夺的生物掉落生效）。这里只剩诅咒态的 −2 —— 原版没有「负等级附魔」，
     * 只能用「按概率删掉 0~2 件掉落（至少留 1 件）」近似，对玩家是纯惩罚，近似无害。
     * BOSS 掉魂 +50% <b>不在本类</b>：见 {@code handler/CopySoulDropHandler.copySoulMult}
     * （e15 已反转 ⇒ 掉落 ×1.5，正常死亡与守门人「挑战胜利」两条路径都取这个倍率）。
     */
    @SubscribeEvent
    public static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() instanceof Player) return;
        var drops = event.getDrops();
        if (drops.isEmpty()) return;

        if (CurseManager.on(player, D, 14)) {
            // e15 厄运 诅咒态：时运/抢夺 −2 ⇒ 平均少掉 0~2 件（至少保留 1 件；原版没有「负等级附魔」可表达）
            int cut = player.getRandom().nextInt(3);
            java.util.Iterator<net.minecraft.world.entity.item.ItemEntity> it = drops.iterator();
            while (it.hasNext() && cut > 0 && drops.size() > 1) {
                it.next();
                it.remove();
                cut--;
            }
        }
    }

    private static boolean isVanillaBlock(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "minecraft".equals(id.getNamespace());
    }

    // ==================== 受伤 ====================

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (inSelfDamage()) return;   // 我们安排的那份伤害：不再被自己的倍率/反弹加工（见 SELF_DAMAGE_DEPTH）
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float d = event.getAmount();
        // /kill 的 genericKill 是 Float.MAX_VALUE（3.4028235E38）：只要本处理器再乘一个倍率
        // （e5 盈伤 ×1.5、e13 余悸 ×1.2…）就会变成 **Infinity**，后续任何一处
        // `Infinity − Infinity` / `Infinity × 0` 都会把伤害写成 **NaN**，而 NaN 血量的
        // `getHealth() <= 0` 恒为 false ⇒ 原版永不判死、`hurt` 也被 isDeadOrDying 挡掉 ——
        // 实机表现正是「/kill 与普通攻击都杀不死 + 血量条 NaN 导致屏幕抖动/卡死」。
        // 所以入口先夹到有限值，再进任何倍率。
        if (!Float.isFinite(d) || d <= 0.0f) return;
        d = Math.min(d, MAX_SAFE_DAMAGE);

        // 累计承伤类条件（e5 满血 / e10 低移速 / e11 低护甲）排查用：
        // 一眼看出「是不是压根没进诅咒态、血量算不算满血、移速属性值多少、d 是多少」。
        AttackDiag.log("⑤受击 血={}/{} 移速={} d={} e5诅咒={} e5反转={} e10诅咒={} e11诅咒={} 护甲={}",
                player.getHealth(), player.getMaxHealth(),
                moveSpeedOf(player), d,
                CurseManager.on(player, D, 4), CurseManager.rev(player, D, 4),
                CurseManager.on(player, D, 9), CurseManager.on(player, D, 10),
                player.getArmorValue());

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
        }

        // e13 余悸：上一击是「大击」时，5 秒内受伤 +20%（乘算）/ 反转 -70%（加算）
        // 「大击」的判定（真实扣血 > 40% 最大生命）与 30 次计数都在 DamageIntake 结算后做
        if (FEAR_UNTIL.containsKey(player.getUUID())) {
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

        // e10 夺佑 / e11 锈甲 / e5 盈伤 / e6 坠渊 的**条件计数**不在这里做：
        // 一律由 {@link DamageIntake} 在 {@code LivingDamageEvent.Post} 用「真实扣血」派发到
        // {@link #onDamageTaken}。这里只负责改伤害（倍率/取消/反弹）。

        // e23 平势 反转：目标最大生命高于你 50% 时，你受伤有 20% 概率**反制**（并清掉对方无敌帧）
        if (CurseManager.rev(player, D, 22)
                && event.getSource().getDirectEntity() instanceof LivingEntity counter
                && counter != player
                && counter.getMaxHealth() >= player.getMaxHealth() * 1.5f
                && player.getRandom().nextFloat() < 0.20f) {
            counter.invulnerableTime = 0;                 // 清无敌帧（Entity 上的公开字段）
            hurtGuarded(counter, player.damageSources().playerAttack(player), Math.max(1.0f, d * 0.5f));
        }

        // ⑤ 大成：受到伤害 -25%（乘算；受伤侧一律乘算 §2）
        if (grandActive(player)) {
            d *= 0.75f;
        }

        event.setAmount(d);
    }

    // ==================== 受伤结算后（统一口径） ====================

    /**
     * 所有「累计受伤 / 受伤阈值」类条件的**唯一入口**，由 {@link DamageIntake} 在
     * {@code LivingDamageEvent.Post} 里派发 —— 此时护甲、附魔、抗性、免伤、本类的全部倍率
     * 都已算完，数值是这一击**真正落到玩家身上的伤害**。
     *
     * @param dealt   这一击的真实扣血（{@code Post.getNewDamage()}，**含**被伤害吸收吃掉的那部分）
     * @param points  已裁过的计入点数（单次不超过 10×最大生命、必死级来源不计，向上取整）
     * @param wasFull 这一击落下**之前**是否满血（{@code DamageIntake} 在 Incoming 阶段快照，1e-3 容差）
     */
    public static void onDamageTaken(ServerPlayer player, net.minecraft.world.damagesource.DamageSource src,
                                     float dealt, long points, boolean wasFull) {
        // e5 盈伤：打前满血 ⇒ 这一下的真实扣血全部计入（不看谁打来的，自伤也算）
        if (wasFull && CurseManager.on(player, D, 4)) {
            long now = CurseManager.tick(player, D, 4, points, 3000L);
            AttackDiag.log("⑤e5 满血真实扣血 +{} → 累计 {}", points, now);
        }

        // e10 夺佑：移速属性值 <0.05 时累计承受 5000 点伤害（死亡不清零，跨掉线保留）。
        // 0.05 是**属性值本身**，不是百分比：原版玩家 MOVEMENT_SPEED 基础值就是 0.1，≈ 总减速 ≥50%。
        // 取 getValue()（含全部修饰符的最终值）：缓慢效果（每级 −15%）、e17 重负、减速铁板都体现在这里。
        if (CurseManager.on(player, D, 9) && moveSpeedOf(player) < 0.05) {
            CurseManager.tick(player, D, 9, points, 5000L);
        }

        // e11 锈甲：护甲值 ≤5 时累计承受 2000 点物理伤害（近战口径：非弹射物且直接实体是生物）
        if (CurseManager.on(player, D, 10)
                && player.getArmorValue() <= 5
                && src != null
                && !src.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                && src.getDirectEntity() instanceof LivingEntity) {
            CurseManager.tick(player, D, 10, points, 2000L);
        }

        // e6 坠渊：单次摔落真实扣血 ≥15 点**并存活**。
        // 注意：`LivingDamageEvent.Post` 在 `LivingEntity.actuallyHurt` 末尾派发，而 `die()` 在 `hurt()`
        // 里更后面（javap：actuallyHurt 431/470、die 872）⇒ **致死一击同样会走到这里**，
        // 「并存活」不能靠"Post 就代表活着扛下来了"，必须显式看血量（Post 时 setHealth 已跑完）。
        if (CurseManager.on(player, D, 5) && src != null
                && src.is(net.minecraft.tags.DamageTypeTags.IS_FALL) && dealt >= 15.0f
                && player.getHealth() > 0.0f) {
            CurseManager.tick(player, D, 5, 1L, 1L);
        }

        // e13 余悸：这一下真实扣血 > 40% 最大生命 ⇒ 开 5 秒窗口，并计「大击 30 次」（死亡即重置）
        if (dealt > player.getMaxHealth() * 0.40f) {
            FEAR_UNTIL.put(player.getUUID(), player.level().getGameTime() + 100L);
            if (CurseManager.on(player, D, 12)) {
                CurseManager.tick(player, D, 12, 1L, 30L);
            }
        }
    }

    // ==================== 造成伤害 ====================

    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        if (inSelfDamage()) return;   // 自己的反弹/反伤：按字面数值结算，不再叠一遍己方倍率
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;
        float d = event.getNewDamage();
        if (d <= 0.0f) return;

        // e7 钝锋：造成所有伤害 -10%（乘算）
        if (CurseManager.on(player, D, 6)) {
            d *= 0.9f;
            CurseManager.tick(player, D, 6, (long) Math.ceil(d), 10000L);
        } else if (CurseManager.rev(player, D, 6)) {
            // 反转：**无视目标 10% 护甲**。按 ArmorPenHandler 的口径，把被护甲削掉的那部分加回 10%
            // （NeoForge 1.21.1 护甲在 LivingDamageEvent.Pre 之前就已结算，这是唯一可行点）
            float armorCut = event.getContainer()
                    .getReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ARMOR);
            if (armorCut > 0.0f) d += armorCut * 0.10f;
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

        // e16 释刃：造成伤害时 2% 概率把主手武器丢出去（统一走 CurseHandDrop：
        // 先清槽、回读校验、只生成一份掉落物，且 1 秒内最多一次）
        if (CurseManager.on(player, D, 15) && player.getRandom().nextFloat() < 0.02f
                && CurseHandDrop.dropHeld(player, net.minecraft.world.InteractionHand.MAIN_HAND, true, "timecore-e16")) {
            CurseManager.tick(player, D, 15, 1L, 200L);
        }

        // e20 垂翅：飞行/腾空时造成伤害 -50%（乘算）
        if (CurseManager.on(player, D, 19) && !player.onGround()) {
            d *= 0.5f;
            CurseManager.tick(player, D, 19, (long) Math.ceil(d), 1000L);
        }

        // ── 输出侧收口（§2「叠加口径（定稿）」：反转态一律**加算**，汇总 Σ 后一次 d × (1 + Σ)） ──
        float additive = 0.0f;

        // e10 夺佑 反转：每有一级抗性提升，伤害 +5%
        if (CurseManager.rev(player, D, 9)) {
            MobEffectInstance res = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
            int level = res == null ? 0 : res.getAmplifier() + 1;
            if (level > 0) additive += 0.05f * level;
        }
        // e20 垂翅 反转的 +25% 是本条唯一的例外：**不并入 Σ**，见下面 Σ 收口之后的独立乘算
        // e21 无援 反转：保命触发后 10 秒内造成伤害 +50%
        if (noAidGraceActive(player)) {
            additive += 0.50f;
        }
        // e22 封镜 反转：Y≥100 时照片弹幕伤害 +50%（同样按加算并入 Σ）
        if (CurseManager.rev(player, D, 21) && player.getY() >= 100.0
                && com.plumejade.lensouls.util.PhotoProjMarker.isBarrageDamage(event.getSource())) {
            additive += 0.50f;
        }

        if (additive > 0.0f) {
            d *= (1.0f + additive);
        }

        // e20 垂翅 反转：**只要处于创造飞行**就追加 +25%，且**不并入上面的 Σ 加算**，
        // 而是独立乘算 ×1.25（放在 Σ 收口之后）。与照片「创造飞行」的 −90% 纯惩罚相乘：
        // 0.1 × 1.25 = 0.125 = 净 −87.5%（设计文档 §2 实例）。
        if (CurseManager.rev(player, D, 19) && player.getAbilities().flying) {
            d *= 1.25f;
        }

        // e17 重负 反转：每 1% 移速损失 → +1 点伤害（**点数加算**，§2③，与上面的百分比乘区分）
        if (CurseManager.rev(player, D, 16)) {
            d += slowPercent(player);
        }

        // e23 平势（诅咒态）：目标最大生命比你高一倍时，它被你打就反击你
        levelFieldCounter(player, target);

        event.setNewDamage(d);
    }

    // ==================== 死亡 / 击杀 ====================

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // e13 余悸 / e8 止息：文案都写了「并存活」⇒ 死亡即重置计数
        // e5 盈伤：文案已写明「死亡清零」⇒ 同样重置（**跨掉线保留**：CurseStateData 走玩家附件，见类尾注释）
        CurseManager.resetProgress(player, D, 12);
        CurseManager.resetProgress(player, D, 4);
        CurseManager.resetProgress(player, D, 7);
        FEAR_UNTIL.remove(player.getUUID());
        NO_HEAL_TICKS.remove(player.getUUID());
        STARVE_TICKS.remove(player.getUUID());
        LAST_POS.remove(player.getUUID());
        MOVE_CARRY.remove(player.getUUID());
        LAST_FOOD.remove(player.getUUID());

        // e11 锈甲 反转：受近战伤害时反弹 25%（放在死亡前结算里不合适，见 onDealDamage 外的反弹逻辑）
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity() == player) return;

        // e9 断粮 条件：在饥饿 V（amplifier ≥4，需外部来源）状态下击杀 200 只生物
        if (CurseManager.on(player, D, 8)) {
            MobEffectInstance hunger = player.getEffect(MobEffects.HUNGER);
            if (hunger != null && hunger.getAmplifier() >= 4) {
                CurseManager.tick(player, D, 8, 1L, 200L);
            }
        }

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
        if (inSelfDamage()) return;   // 我们自己的自伤/反伤，不再反弹（否则 onReflect ⇄ onDealDamage 无限递归）
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CurseManager.rev(player, D, 10)) return;
        if (!(event.getSource().getDirectEntity() instanceof LivingEntity attacker)) return;
        if (event.getSource().getDirectEntity() instanceof Projectile) return;
        hurtGuarded(attacker, player.damageSources().thorns(player), event.getAmount() * 0.25f);
    }

    // ==================== e21 无援 ====================

    /**
     * ⑤ e21 无援（诅咒态）是否**封掉一切不死图腾**。
     * <p>
     * 供两条「原版事件拦不到」的路径使用：
     * <ul>
     *   <li>{@code mixin/TotemAnnulledMixin} —— 原版 {@code LivingEntity.checkTotemDeathProtection}
     *       入口直接返回 false（连物品都不会被 {@code shrink}）；</li>
     *   <li>{@code mixin/compat/InventoryTotemCurseMixin} —— 「更好的图腾 / inventorytotem」在
     *       {@code LivingDeathEvent} 里从**背包**取图腾自行复活，完全不经过 {@code LivingUseTotemEvent}。</li>
     * </ul>
     */
    public static boolean blocksTotem(net.minecraft.server.level.ServerPlayer player) {
        return CurseManager.on(player, D, 20);
    }

    /** 图腾拦截取证日志的节流表（UUID → 下次允许打印刻） */
    private static final java.util.Map<java.util.UUID, Long> TOTEM_LOG_AT =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 图腾被拦下时打一条 INFO（每玩家 30 秒最多一次）。
     * <p>
     * 用途：确认 mixin 真的命中了 —— 原版路径打印 {@code vanilla}，
     * 《更好的图腾》背包路径打印 {@code inventorytotem}。没有这条日志就说明那条路径没被拦到。
     */
    public static void logTotemBlock(ServerPlayer player, String path) {
        long now = player.level().getGameTime();
        Long until = TOTEM_LOG_AT.get(player.getUUID());
        if (until != null && now < until) return;
        TOTEM_LOG_AT.put(player.getUUID(), now + 600L);
        LenSouls.LOGGER.info("[Curse] ⑤ e21 图腾已拦下（路径={}，玩家={}）",
                path, player.getGameProfile().getName());
    }

    /** e21 无援：不死图腾失效（诅咒态）；反转后照常触发并给 10 秒强化 */
    @SubscribeEvent
    public static void onUseTotem(net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (CurseManager.on(player, D, 20)) {
            event.setCanceled(true);
            return;
        }
        if (CurseManager.rev(player, D, 20)) {
            // 反转：保命手段恢复，并开 10 秒窗口 —— 窗口内造成伤害 +50%、免疫一切负面效果
            // （免疫在 tick 里持续清除负面；加伤在输出侧 Σ 里）
            grantNoAidGrace(player);
        }
    }

    /** e21 无援 条件：生命 ≤2 时用拍立得拍 10 张瞬间照片（回调，唯一入口见 {@link #onFrameAdded}） */
    public static void onInstantPhotoAtLowHealth(ServerPlayer player) {
        if (!CurseManager.on(player, D, 20)) return;
        if (player.getHealth() > 2.0f) return;
        CurseManager.tick(player, D, 20, 1L, 10L);
    }

    /** 拍立得（{@code exposure_polaroid:instant_camera}）——e21 条件里的「拍立得」 */
    private static final ResourceLocation INSTANT_CAMERA =
            ResourceLocation.parse("exposure_polaroid:instant_camera");

    /**
     * e21 无援 反转条件的**唯一入口**：用拍立得拍出照片（生命 ≤2 时计数，目标 10 张）。
     * <p>
     * <b>为什么补在这里</b>：此前只有 {@link #onInstantPhotoAtLowHealth} 这个「回调方法」，
     * 而全仓库**没有任何调用点** ⇒ 条件进度恒为 0、e21 永远无法反转
     * （与 ⑤ e5 / e9 当年「完全没有 cond 分支 ⇒ 永远无法反转」是同一类缺陷）。
     * <p>
     * 拍立得出片必然先经过 Exposure 的 {@code FrameAddedEvent}（与 {@code PhotoInjectionHandler}
     * 同源），这里按「相机是拍立得」筛选后回报；普通相机 / 暗房不计数（文案写明「用拍立得」）。
     */
    @SubscribeEvent
    public static void onFrameAdded(io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent event) {
        try {
            if (!(event.getCameraHolderEntity() instanceof ServerPlayer player)) return;
            ItemStack camera = event.getCamera();
            if (camera == null || camera.isEmpty()) return;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(camera.getItem());
            if (id == null || !INSTANT_CAMERA.equals(id)) return;
            onInstantPhotoAtLowHealth(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑤ e21 拍立得拍照计数异常", t);
        }
    }

    /**
     * e23 平势：目标最大生命比你高一倍时，它被你打就反击你（诅咒态）。
     * <p>
     * 同 tick 只反击一次：范围/多段伤害（横扫、弹幕、连续命中）会在同一刻派发多次
     * {@code LivingDamageEvent.Pre}，逐次反击等于把「挨一下」放大成十几下。
     * 造成伤害走 {@link #hurtGuarded}，绝不允许这条链再被 {@link #onDealDamage} 处理回来。
     */
    public static void levelFieldCounter(ServerPlayer player, LivingEntity target) {
        if (inSelfDamage()) return;
        if (!CurseManager.on(player, D, 22)) return;
        if (target.getMaxHealth() < player.getMaxHealth() * 2.0f) return;
        long now = player.level().getGameTime();
        Long last = LAST_FIELD_TICK.put(player.getUUID(), now);
        if (last != null && last.longValue() == now) return;
        hurtGuarded(player, player.damageSources().mobAttack(target), 2.0f);
    }

    /** 自己在伤害结算内部再造成的伤害：打上重入标记，期间本类的伤害回调全部跳过 */
    private static boolean inSelfDamage() {
        return SELF_DAMAGE_DEPTH.get()[0] > 0;
    }

    /**
     * 我们主动造成的一次伤害（自伤 / 反弹 / 反制）。
     * <p>
     * 嵌套调用直接放弃：外层已经在结算我们自己安排的伤害了，再套一层只会回到递归里。
     * 数值按字面结算，不再被诅咒的倍率加工 —— 这也是「反击 2 点」「反弹 25%」的字面语义。
     */
    private static void hurtGuarded(LivingEntity target, net.minecraft.world.damagesource.DamageSource source,
                                    float amount) {
        if (target == null || amount <= 0.0f) return;
        if (target.level().isClientSide) return;
        int[] depth = SELF_DAMAGE_DEPTH.get();
        if (depth[0] > 0) return;
        depth[0]++;
        try {
            target.hurt(source, amount);
        } finally {
            depth[0]--;
        }
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

    // ==================== e14 裁影 / e22 封镜 ====================

    private static final String PHOTO_SLOT_TAG = "lensouls:curse_photo_slot";
    private static final ResourceLocation PHOTO_SLOT_MOD = ResourceLocation.parse("lensouls:curse_trimmed_frame");

    /**
     * e14 裁影：照片栏位 **-2**（诅咒态）/ **+4**（反转态）。
     * <p>
     * 「照片槽」就是 Curios 的 {@code photograph} 栏位，加减走 Curios 官方的
     * {@code addTransientSlotModifier}（口径与 {@code PhotoSpecialEffects.updatePhotoSlots} 一致：
     * 加完必须 {@code stacks.update()} 把惰性 resize 落实，否则尺寸要等下次被读取才生效；
     * 再把 {@code getUpdatingInventories()} 发一次同步包，客户端才会 applySyncTag）。
     */
    /**
     * 强制重算 e14 的照片栏位加成（绕过 {@code PHOTO_SLOT_TAG} 的「值没变就不动」短路）。
     * <p>
     * 供 {@code CurioSlotBonuses.refresh} 调用（虚影残像归还时必须先把槽位数还原）。
     */
    public static void refreshPhotoSlots(ServerPlayer player) {
        if (player == null) return;
        player.getPersistentData().remove(PHOTO_SLOT_TAG);
        updatePhotoSlots(player);
    }

    private static void updatePhotoSlots(ServerPlayer player) {
        int want = 0;
        if (CurseManager.on(player, D, 13)) want = -2;
        else if (CurseManager.rev(player, D, 13)) want = 4;

        // 自愈：只比 persistentData 里的「上次写入多少」是不够的 —— 死亡/重生会重建 Curios 容器，
        // 标记位还在、修饰符却已经没了，于是永远不再重写（照片栏位就永久少/多一截）。
        // 必须同时核对玩家身上该修饰符的现值。
        if (player.getPersistentData().getInt(PHOTO_SLOT_TAG) == want
                && CurioSlotBonuses.slotModifierAmount(player, "photograph", PHOTO_SLOT_MOD) == want) {
            return;
        }
        player.getPersistentData().putInt(PHOTO_SLOT_TAG, want);
        final int target = want;

        try {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                handler.removeSlotModifier("photograph", PHOTO_SLOT_MOD);
                if (target != 0) {
                    handler.addTransientSlotModifier("photograph", PHOTO_SLOT_MOD, target,
                            AttributeModifier.Operation.ADD_VALUE);
                }
                var stacks = handler.getCurios().get("photograph");
                if (stacks != null) stacks.update();
                var updates = handler.getUpdatingInventories();
                if (!updates.isEmpty()) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            new top.theillusivec4.curios.common.network.server.sync.SPacketSyncModifiers(
                                    player.getId(), updates));
                    updates.clear();
                }
            });
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ⑤ e14 照片栏位调整失败", t);
        }
    }

    /** e22 封镜 的反转条件：在 Y≥200 处拍照（普通照片也算）——由 {@code PhotoInjectionHandler} 回调 */
    public static void onPhotoTaken(ServerPlayer player) {
        if (player == null) return;
        if (!CurseManager.on(player, D, 21)) return;
        if (player.getY() < 200.0) return;
        CurseManager.tick(player, D, 21, 1L, 100L);
    }

    // ==================== 反转态 / 大成 的公共支撑 ====================

    /** e21 无援 反转：保命触发后的 10 秒强化窗口（持久化，跨维度保留） */
    private static final String KEY_NO_AID_UNTIL = "lensouls:tc_no_aid_until";
    /** e14 裁影 条件：能力窃取照片的**生物种类**去重集合（持久化，跨死亡保留） */
    private static final String KEY_STOLEN_KINDS = "lensouls:tc_stolen_kinds";
    /** e14 条件目标：50 种 */
    private static final int STOLEN_KIND_GOAL = 50;
    /** ⑤ 大成：全属性 +50% 的属性修饰符 id */
    private static final ResourceLocation GRAND_MOD = ResourceLocation.parse("lensouls:curse_grand");

    /** e17 重负 反转：当前移速**损失**百分比（0..100），1% = 1 点附加伤害 */
    private static float slowPercent(ServerPlayer player) {
        var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return 0f;
        double base = attr.getBaseValue();
        if (base <= 0.0) return 0f;
        double lost = (1.0 - attr.getValue() / base) * 100.0;
        return (float) Math.max(0.0, Math.min(100.0, lost));
    }

    /** e21 无援 反转：强化窗口是否还在 */
    private static boolean noAidGraceActive(ServerPlayer player) {
        long until = player.getPersistentData().getLong(KEY_NO_AID_UNTIL);
        return until > 0L && player.level().getGameTime() < until;
    }

    /** e21 无援 反转：保命手段（不死图腾 / 时空回溯）触发时开 10 秒窗口 */
    public static void grantNoAidGrace(ServerPlayer player) {
        if (player == null) return;
        if (!CurseManager.rev(player, D, 20)) return;
        player.getPersistentData().putLong(KEY_NO_AID_UNTIL, player.level().getGameTime() + 200L);
    }

    /** 清掉身上所有**负面**效果（e21 反转窗口内 / 大成常驻 = 「免疫一切负面效果」） */
    private static void purgeNegativeEffects(ServerPlayer player) {
        java.util.List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> bad =
                new java.util.ArrayList<>();
        for (MobEffectInstance inst : player.getActiveEffects()) {
            if (!inst.getEffect().value().isBeneficial()) bad.add(inst.getEffect());
        }
        for (var holder : bad) {
            player.removeEffect(holder);
        }
    }

    /** ⑤ 大成：该款 23 条**全部**反转 */
    private static boolean grandActive(ServerPlayer player) {
        if (player == null || !CurseManager.isActive(player, D)) return false;
        return CurseManager.state(player).reversedCount(D) >= 23;
    }

    /** ⑤ 大成「全属性 +50%」覆盖的属性（主要战斗属性） */
    @SuppressWarnings("unchecked")
    private static final net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>[] GRAND_ATTRS =
            new net.minecraft.core.Holder[]{
                    Attributes.MAX_HEALTH,
                    Attributes.ATTACK_DAMAGE,
                    Attributes.ARMOR,
                    Attributes.ARMOR_TOUGHNESS,
                    Attributes.MOVEMENT_SPEED,
                    Attributes.ATTACK_SPEED,
                    Attributes.KNOCKBACK_RESISTANCE,
                    Attributes.LUCK,
            };

    /** ⑤ 大成：全属性 +50%（ADD_MULTIPLIED_TOTAL，收口在属性修饰符上） */
    private static void applyGrand(ServerPlayer player, boolean grand) {
        for (var attr : GRAND_ATTRS) {
            var inst = player.getAttribute(attr);
            if (inst == null) continue;
            if (grand) {
                if (inst.getModifier(GRAND_MOD) == null) {
                    inst.addOrUpdateTransientModifier(new AttributeModifier(
                            GRAND_MOD, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            } else if (inst.getModifier(GRAND_MOD) != null) {
                inst.removeModifier(GRAND_MOD);
            }
        }
    }

    /**
     * e14 裁影 的反转条件：**累计拍过 50 种不同的能力窃取照片**（同种生物不重复计）。
     * <p>
     * 由 {@code ability/PhotoInjector} 在写入 {@code lensouls:stolen_entity} 时回调——
     * 能力窃取照片本来就有这条数据，这里只做**去重统计**（写玩家 persisted 子键，跨死亡保留）。
     */
    public static void onStolenPhoto(ServerPlayer player, String entityId) {
        if (player == null || entityId == null || entityId.isEmpty()) return;
        if (!CurseManager.on(player, D, 13)) return;

        var tag = player.getPersistentData()
                .getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        var list = tag.getList(KEY_STOLEN_KINDS, net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (entityId.equals(list.getString(i))) return;      // 同种生物只计一次
        }
        list.add(net.minecraft.nbt.StringTag.valueOf(entityId));
        tag.put(KEY_STOLEN_KINDS, list);
        player.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, tag);

        int n = list.size();
        CurseManager.setProgress(player, D, 13, n);
        if (n >= STOLEN_KIND_GOAL) {
            CurseManager.reach(player, D, 13);
        }
    }

    /**
     * e10 条件用：移速**属性值**（含全部修饰符的最终值，原版玩家基础值 0.1）。
     * 属性缺失（理论上不会）时回退到 0.1，保证不会误判成「已减速」。
     */
    private static double moveSpeedOf(ServerPlayer player) {
        var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return attr == null ? 0.1 : attr.getValue();
    }

    /**
     * e17/e18 的**真格累加**：把本 tick 的位移并入该条件的整格计数，不足 1 格的余数留到下一 tick。
     * <p>
     * 不能写成 {@code ceil(dist)} 或 {@code round(dist)}：慢走每 tick 只有 0.1~0.2 格，
     * ceil 会把每个移动 tick 都记成 1 格（进度放大 4~7 倍），round 则永远记 0。
     *
     * @return 本次应当计入进度的整格数（通常为 0，每 5~7 tick 出一次 1）
     */
    private static long wholeCells(UUID id, int slot, double dist) {
        double[] carry = MOVE_CARRY.computeIfAbsent(id, k -> new double[2]);
        carry[slot] += dist;
        long whole = (long) carry[slot];
        carry[slot] -= whole;
        return whole;
    }

    /** 上一 tick 的水平位置（XZ）——Y 不计入移动距离，见位移结算处注释 */
    private record Pos(double x, double z) {
    }

    /** 清掉该玩家的所有瞬时计时（退出世界 / 死亡时调用） */
    public static void clear(UUID id) {
        if (id == null) return;
        NO_HEAL_TICKS.remove(id);
        STARVE_TICKS.remove(id);
        LAST_FOOD.remove(id);
        LAST_POS.remove(id);
        MOVE_CARRY.remove(id);
        FEAR_UNTIL.remove(id);
        LAST_FIELD_TICK.remove(id);
        CurseHandDrop.clear(id);
        // e4「待吞掉落」按**方块坐标**记账（见 SWALLOW_POS），且每服务器 tick 清空一次，
        // 与玩家 UUID 无关，这里不再需要清理。
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
