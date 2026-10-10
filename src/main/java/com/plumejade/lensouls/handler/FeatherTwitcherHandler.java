package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.entity.ModEntities;
import com.plumejade.lensouls.entity.TwitcherEntity;
import com.plumejade.lensouls.feather.CurseDef;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.feather.CurseManager;
import com.plumejade.lensouls.feather.FeatherEquip;
import com.plumejade.lensouls.item.ModItems;
import com.plumejade.lensouls.mixin.EntityInvulnerableTimeAccessor;
import com.plumejade.lensouls.network.TwistSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ② 羽·扭曲之人（新改案 · 3 条 · 整款共用反转条件）。
 *
 * <pre>
 * e1 外伤(0)：诅咒 = 受到伤害 +100%；反转 = 受到伤害 -25%（受伤侧乘算）
 * e2 苦楚(1)：诅咒 = 每点扭曲值：造成伤害 +1%、受到伤害 +1%；扭曲值上限 100；反转 = 扭曲值获取 ×1.2
 * e3 狂喜(2)：诅咒 = 受击时每 1 点伤害 +0.1 扭曲值；扭曲值满 100 时死亡 → 强制掉落（无视 keepInventory，
 *                  有虚影核心则由它托管）+ 生成扭曲者
 *             反转 = 〔保留〕原机制照旧（阻死结束后若仍死亡，原满值死亡效果照常触发）；
 *                    另有：死亡时若扭曲值 ≥100 → 消耗 100 阻死 3 秒（仅阻止死亡；时间到受 999 点真伤）；
 *                    期间每刻消耗 1 + 3% 当前值，每消耗 1 点额外延长 0.1 秒（÷（状态时间+1）递减），可突破上限获取
 * 反转条件（整款共用）：扭曲值 100、护甲值 0 时，击杀 6 万血以上的生物
 * </pre>
 *
 * 扭曲值来源：受击驱动（狂喜）+ 复制之魂合成消耗（{@code ResultSlotMixin}）；
 * 清零：击杀扭曲者（{@link #onTwitcherDeath}）。相对旧版<b>只移除</b>「每次死亡 +10」。
 */
public class FeatherTwitcherHandler {

    private static final CurseDef D = CurseDefs.TWITCHER;

    public static final String KEY_TWIST = "lensouls:twist_value";
    /** 死亡强制掉落的单次标记（扭曲值满 100 死亡时写入，掉落流程消费后清除） */
    public static final String KEY_FORCE_DROP = "lensouls:force_drop";
    /**
     * 满值死亡的「托管」标记（设计文档 §7）：本次死亡即便开着 keepInventory，
     * 也允许 {@code PhantomRemnantHandler} 用虚影核心托管背包。
     */
    public static final String KEY_FORCE_HOST = "lensouls:force_host";

    /** 扭曲值上限 */
    public static final int MAX_TWIST = 100;
    /** 存活扭曲者判定半径 */
    public static final int SPAWN_RANGE = 32;
    /** BOSS 判定半径 */
    public static final int BOSS_RANGE = 64;

    // ── e1 外伤 ──
    /** 诅咒态受到伤害 +100%（乘算） */
    public static final float E1_TAKEN_MULT_CURSE = 2.0f;
    /** 反转态受到伤害 -25%（受伤侧乘算） */
    public static final float E1_TAKEN_MULT_REVERSED = 0.75f;

    // ── e2 苦楚 ──
    /** 每点扭曲值的造成伤害增幅（+1%） */
    public static final float E2_DEALT_PER_TWIST = 0.01f;
    /** 每点扭曲值的受到伤害增幅（+1%） */
    public static final float E2_TAKEN_PER_TWIST = 0.01f;
    /** 反转态扭曲值获取倍率（×1.2） */
    public static final float E2_REVERSED_GAIN_MULT = 1.2f;

    // ── e3 狂喜 ──
    /** 受击驱动：每 1 点伤害提升 0.1 扭曲值 */
    public static final float E3_TWIST_PER_DAMAGE = 0.1f;
    /** 反转：阻死基础时长 3 秒（60 tick） */
    public static final int DEFY_BASE_TICKS = 60;
    /** 反转：每刻消耗 1 + 3% 当前扭曲值 */
    public static final float DEFY_CONSUME_RATIO = 0.03f;
    /** 反转：每消耗 1 点扭曲值额外延长 0.1 秒（= 2 tick） */
    public static final float DEFY_EXTEND_TICKS_PER_POINT = 2.0f;
    /** 反转：阻死结束时受到的 999 点真伤 */
    public static final float DEFY_DEATH_DAMAGE = 999.0f;

    // ── 整款共用反转条件 ──
    /** 反转条件：目标最大生命 ≥ 6 万 */
    public static final float REVERSE_TARGET_MIN_HEALTH = 60000f;

    /** 进行中的「阻死」状态 */
    private static final Map<UUID, DefyState> DEFY = new HashMap<>();
    /** 阻死计时到点、正由我们施加 999 点真伤（死亡后要触发原满值死亡机制） */
    private static final Map<UUID, Boolean> DEFY_ENDING = new HashMap<>();
    /** 扭曲值的小数部分（受击驱动是 0.1/点，整数 NBT 存不下） */
    private static final Map<UUID, Float> TWIST_FRAC = new HashMap<>();
    /** 本次伤害是我们自己施加的阻死 999 点（不参与受击驱动的扭曲值增长） */
    private static boolean applyingDefyDeath = false;

    private static final class DefyState {
        final long startTick;
        long untilTick;
        float extension;

        DefyState(long now) {
            this.startTick = now;
            this.untilTick = now + DEFY_BASE_TICKS;
        }
    }

    // ==================== 登录 / 重生 / 跨维度 补发同步 ====================
    // 左侧扭曲条读的是客户端缓存，而这些时机客户端会重建玩家实例（缓存丢失）⇒ 必须补发，
    // 否则表现为「重进游戏 / 跨维度 / 死亡重生后条子消失，得再操作一次才出现」。

    @SubscribeEvent
    public static void onLoginSync(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            TwistSyncPacket.send(sp, getTwist(sp));
        }
    }

    @SubscribeEvent
    public static void onRespawnSync(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            TwistSyncPacket.send(sp, getTwist(sp));
        }
    }

    @SubscribeEvent
    public static void onDimensionSync(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            TwistSyncPacket.send(sp, getTwist(sp));
        }
    }

    /**
     * 佩戴检测：{@link FeatherEquip#has}（只认本模组羽毛栏的 7 个槽位）。
     */
    public static boolean hasTwitcher(Player player) {
        return FeatherEquip.has(player, ModItems.FEATHER_TWITCHER.get());
    }

    /**
     * 跨死亡持久化子键：NeoForge 复活（restoreFrom）只复制 persistentData 的
     * PlayerPersisted 子键，直接放根上的键死亡复活后会丢失（实测 twist 变 0）。
     */
    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void writeBack(Player player, CompoundTag tag) {
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    // ==================== 扭曲值读写 ====================

    /** 原始扭曲值（不封顶：阻死期间可突破上限） */
    public static int rawTwist(Player player) {
        if (player == null) return 0;
        return Math.max(0, persisted(player).getInt(KEY_TWIST));
    }

    /** 读取扭曲值（阻死期间不封顶，其余时间 0..100） */
    public static int getTwist(Player player) {
        if (player == null) return 0;
        int raw = rawTwist(player);
        return DEFY.containsKey(player.getUUID()) ? raw : Math.min(raw, MAX_TWIST);
    }

    /** 设置扭曲值：常规封顶 100，阻死期间不封顶；同步客户端（客户端 bar 只认 0..100） */
    public static void setTwist(ServerPlayer player, int value) {
        boolean defy = DEFY.containsKey(player.getUUID());
        int clamped = defy ? Math.max(0, value) : Math.max(0, Math.min(MAX_TWIST, value));
        CompoundTag tag = persisted(player);
        tag.putInt(KEY_TWIST, clamped);
        writeBack(player, tag);
        TwistSyncPacket.send(player, Math.min(clamped, MAX_TWIST));
    }

    /** 增加扭曲值（含苦楚反转的获取 ×1.2；封顶逻辑见 {@link #setTwist}） */
    public static void addTwist(ServerPlayer player, int delta) {
        if (player == null || delta == 0) return;
        if (delta > 0 && CurseManager.rev(player, D, 1)) {
            delta = Math.max(1, Math.round(delta * E2_REVERSED_GAIN_MULT));
        }
        setTwist(player, getTwist(player) + delta);
    }

    /** 受击驱动的小数累加（每 1 点伤害 +0.1 扭曲值，攒够 1 点才写入） */
    private static void addTwistFraction(ServerPlayer player, float amount) {
        if (player == null || amount <= 0f) return;
        if (CurseManager.rev(player, D, 1)) amount *= E2_REVERSED_GAIN_MULT;
        float total = TWIST_FRAC.merge(player.getUUID(), amount, Float::sum);
        int whole = (int) Math.floor(total);
        if (whole <= 0) return;
        TWIST_FRAC.put(player.getUUID(), total - whole);
        setTwist(player, rawTwist(player) + whole);
    }

    /** 玩家登录时同步扭曲值到客户端槽（大退重进后立即恢复显示，避免客户端缓存停留在 0） */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TwistSyncPacket.send(player, getTwist(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        DEFY.remove(id);
        DEFY_ENDING.remove(id);
        TWIST_FRAC.remove(id);
    }

    // ==================== 受到伤害 ====================

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Pre event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            float d = event.getNewDamage();
            if (d <= 0f) return;

            // e1 外伤：+100%（诅咒，乘算）/ -25%（反转，受伤侧乘算）
            if (CurseManager.on(player, D, 0)) {
                d *= E1_TAKEN_MULT_CURSE;
            } else if (CurseManager.rev(player, D, 0)) {
                d *= E1_TAKEN_MULT_REVERSED;
            }

            // e2 苦楚：每点扭曲值受到伤害 +1%（乘算）
            if (CurseManager.on(player, D, 1)) {
                d *= 1.0f + E2_TAKEN_PER_TWIST * getTwist(player);
            }

            event.setNewDamage(d);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ② 受伤结算异常", t);
        }
    }

    /** 阻死期间：本次致命伤害直接取消（只阻止死亡，不改数值） */
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!DEFY.containsKey(player.getUUID())) return;
            if (event.getAmount() >= player.getHealth()) {
                event.setCanceled(true);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ② 阻死免死异常", t);
        }
    }

    /** e3 狂喜：受击驱动涨扭曲值（诅咒态与反转态都生效 —— 反转〔保留〕原机制） */
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        try {
            if (applyingDefyDeath) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!CurseManager.on(player, D, 2) && !CurseManager.rev(player, D, 2)) return;
            float dealt = event.getNewDamage();
            if (dealt <= 0f) return;
            addTwistFraction(player, dealt * E3_TWIST_PER_DAMAGE);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ② 受击涨扭曲值异常", t);
        }
    }

    // ==================== 造成伤害 ====================

    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        try {
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
            if (event.getEntity() == player) return;
            if (!CurseManager.on(player, D, 1)) return;
            float d = event.getNewDamage();
            if (d <= 0f) return;
            // e2 苦楚：每点扭曲值造成伤害 +1%（乘算）
            event.setNewDamage(d * (1.0f + E2_DEALT_PER_TWIST * getTwist(player)));
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ② 造成伤害结算异常", t);
        }
    }

    // ==================== 每 tick：阻死状态 ====================

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            tickDefy(player);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ② 阻死结算异常", t);
        }
    }

    private static void tickDefy(ServerPlayer player) {
        DefyState st = DEFY.get(player.getUUID());
        if (st == null) return;
        long now = player.level().getGameTime();

        // 每刻消耗 1 + 3% 当前值；每消耗 1 点延长 0.1 秒（递减：÷（状态时间 + 1）秒）
        int twist = rawTwist(player);
        if (twist > 0) {
            int consume = Math.max(1, Math.round(1.0f + DEFY_CONSUME_RATIO * twist));
            consume = Math.min(consume, twist);
            setTwist(player, twist - consume);
            float seconds = Math.max(0f, (now - st.startTick) / 20.0f);
            st.extension += consume * DEFY_EXTEND_TICKS_PER_POINT / (seconds + 1.0f);
            if (st.extension >= 1.0f) {
                long add = (long) st.extension;
                st.untilTick += add;
                st.extension -= add;
            }
        }

        if (now >= st.untilTick) {
            DEFY.remove(player.getUUID());
            DEFY_ENDING.put(player.getUUID(), Boolean.TRUE);
            ((EntityInvulnerableTimeAccessor) (Object) player).lensouls$setInvulnerableTime(0);
            applyingDefyDeath = true;
            try {
                player.hurt(player.damageSources().magic(), DEFY_DEATH_DAMAGE);
            } finally {
                applyingDefyDeath = false;
            }
        }
    }

    // ==================== 死亡 ====================

    /**
     * 死亡结算（{@link EventPriority#HIGHEST}：必须早于 {@code PhantomRemnantHandler} 的默认优先级，
     * 后者要读到本次死亡是否被标记为「满值死亡」才决定在 keepInventory 下是否托管 —— 见设计文档 §7）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            if (!hasTwitcher(player)) return;

            // ① 阻死进行中：继续阻止死亡（计时到点那一击由 tickDefy 自己施加）
            if (DEFY.containsKey(player.getUUID())) {
                player.setHealth(1.0f);
                event.setCanceled(true);
                return;
            }

            int twist = rawTwist(player);
            boolean full = twist >= MAX_TWIST;

            // ② 反转态：死亡时扭曲值 ≥100 → 消耗 100，阻止死亡 3 秒
            if (full && CurseManager.rev(player, D, 2)) {
                setTwist(player, twist - MAX_TWIST);
                DEFY.put(player.getUUID(), new DefyState(player.level().getGameTime()));
                player.setHealth(1.0f);
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable("message.lensouls.twitcher.defy"), true);
                return;
            }

            // ③ 原「满值死亡」机制：诅咒态直接触发；反转态在阻死结束后（999 真伤致死）触发
            boolean ending = DEFY_ENDING.remove(player.getUUID()) != null;
            if ((full && CurseManager.on(player, D, 2)) || ending) {
                triggerFullTwistDeath(player);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ② 死亡结算异常", t);
        }
    }

    /** 满值死亡三件套：生成扭曲者 + 写强制掉落标记 + 写托管标记 */
    private static void triggerFullTwistDeath(ServerPlayer player) {
        if (canSpawnTwitcher(player)) {
            spawnTwitcher(player);
        }
        player.getPersistentData().putBoolean(KEY_FORCE_DROP, true);
        player.getPersistentData().putBoolean(KEY_FORCE_HOST, true);
    }

    /** 生成条件：死亡点 32 格内无存活归属扭曲者，且 64 格内无 BOSS */
    private static boolean canSpawnTwitcher(ServerPlayer player) {
        List<TwitcherEntity> near = player.serverLevel().getEntitiesOfClass(TwitcherEntity.class,
                player.getBoundingBox().inflate(SPAWN_RANGE),
                e -> e.isAlive() && e.isOwnedBy(player.getUUID()));
        if (!near.isEmpty()) return false;
        return !hasBossNearby(player, BOSS_RANGE);
    }

    /** 反射检测死亡点半径内是否存在持有可见 BOSS 血条的实体（复用 CopySoulDropHandler 判定） */
    private static boolean hasBossNearby(ServerPlayer player, int range) {
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range))) {
            if (entity == player) continue;
            if (CopySoulDropHandler.hasBossBar(entity)) return true;
        }
        return false;
    }

    /** 生成扭曲者：死亡点 3~8 格随机位置，属性按玩家状态配置 */
    private static void spawnTwitcher(ServerPlayer player) {
        var level = player.serverLevel();
        double angle = level.random.nextDouble() * Math.PI * 2;
        double dist = 3 + level.random.nextInt(6);
        Vec3 pos = player.position().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

        TwitcherEntity twitcher = ModEntities.TWITCHER.get().create(level);
        if (twitcher == null) return;
        twitcher.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0.0F);
        twitcher.setOwner(player.getUUID());
        twitcher.initFromPlayer(player);
        level.addFreshEntity(twitcher);
    }

    /**
     * 扭曲者死亡 → 清零<b>击杀者</b>的扭曲值（沿用旧机制）。
     */
    @SubscribeEvent
    public static void onTwitcherDeath(LivingDeathEvent event) {
        try {
            if (!(event.getEntity() instanceof TwitcherEntity)) return;
            if (event.getEntity().level().isClientSide) return;
            if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
            if (!hasTwitcher(killer)) return;
            if (getTwist(killer) > 0) {
                setTwist(killer, 0);
                TWIST_FRAC.remove(killer.getUUID());
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ② 扭曲者死亡清零异常", t);
        }
    }

    // ==================== 反转条件：满值 + 0 护甲时击杀 6 万血以上生物 ====================

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        try {
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
            LivingEntity dead = event.getEntity();
            if (dead == player) return;
            if (!CurseManager.isActive(player, D)) return;
            if (rawTwist(player) < MAX_TWIST) return;
            if (player.getArmorValue() != 0) return;
            if (dead.getMaxHealth() < REVERSE_TARGET_MIN_HEALTH) return;

            for (int i = 0; i < D.entries(); i++) {
                CurseManager.tick(player, D, i, 1L, 1L);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[Curse] ② 反转条件结算异常", t);
        }
    }
}
