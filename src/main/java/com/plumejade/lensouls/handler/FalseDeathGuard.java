package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.feather.CurseManager;
import com.plumejade.lensouls.mixin.LivingEntityDeadAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「假死」兜底：血量 ≤ 0 但人没死（{@code dead == false}）的**修复器**。
 * <p>
 * <b>这个状态为什么致命</b>：原版 {@code LivingEntity.hurt} 的头几行就是
 * {@code if (this.isDeadOrDying()) return false;}，而 {@code isDeadOrDying()} 的定义是
 * {@code isRemoved() || dead || getHealth() <= 0}。所以一旦出现「血量 0 但 dead 还是 false」，
 * 之后**任何**伤害都进不来（{@code /kill}、999 真伤、摔落、岩浆都不行），
 * 唯一的出口是直接 {@code setHealth(...)}（这就是 {@code /heal} 能救回来的原因）。
 * <p>
 * 产生这种状态只有两条路：<br>
 * ① 某个 {@code LivingDeathEvent} 监听器 {@code setCanceled(true)} 却没有把血量填回 &gt; 0
 * （原版契约：取消死亡 ⇒ 你必须自己回血，图腾就是 {@code setHealth(1)}）；<br>
 * ② 某段代码直接 {@code setHealth(0)}（不经过伤害流程，自然也不会触发死亡）。
 * <p>
 * 本类做两件事（都不改任何玩法数值，只在已经坏掉的状态上止损）：
 * <ul>
 *   <li><b>死亡当刻</b>：{@link LivingDeathEvent} 在 {@link EventPriority#LOWEST} 收尾时，
 *       若事件已被取消且血量仍 ≤ 0 ⇒ 按图腾惯例回填 1 点并 WARN 记名。
 *       本模组自己的取消点（② 阻死、照片套装复活）都会先回血，所以这条只会命中第三方。</li>
 *   <li><b>事后兜底</b>：每 20 tick 扫在线玩家，连续 2 次发现 {@code 血量 ≤ 0 && !dead}
 *       ⇒ 回填 1 点并 WARN（含诊断信息：游戏模式、上限、扭曲值、诅咒掩码、最后伤害来源）。
 *       登录时也立即查一次。</li>
 * </ul>
 */
public final class FalseDeathGuard {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("lensouls.falsedeath");

    /** 连续观测到「0 血未死」多少次（20 tick/次）才动手：2 次 = 2 秒，避开同一 tick 内的正常过渡 */
    private static final int REPAIR_AFTER_OBSERVATIONS = 2;

    /** UUID → 已连续观测次数 */
    private static final Map<UUID, Integer> STUCK_STREAK = new ConcurrentHashMap<>();

    private FalseDeathGuard() {
    }

    // ==================== ① 死亡被取消的当刻 ====================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeathFinalCheck(LivingDeathEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!event.isCanceled()) {
                STUCK_STREAK.remove(player.getUUID());
                return;
            }
            if (Float.isFinite(player.getHealth()) && player.getHealth() > 0.0f) return;   // 取消者已经正确回血，无事

            player.setHealth(1.0f);
            logRepair(player, "死亡事件被取消但血量仍为 0", event.getSource());
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[FalseDeath] 死亡收尾检查异常", t);
        }
    }

    // ==================== ② 事后兜底（每 20 tick / 登录） ====================

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        try {
            if (event.getServer().getTickCount() % 20 != 0) return;
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                check(player, false);
            }
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[FalseDeath] 周期检查异常", t);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer player) check(player, true);
        } catch (Throwable t) {
            LenSouls.LOGGER.error("[FalseDeath] 登录检查异常", t);
        }
    }

    /** 血量是否「坏掉」：NaN / ±Infinity 会让 {@code getHealth() <= 0} 恒 false ⇒ 永不判死 */
    private static boolean isBrokenHealth(ServerPlayer player) {
        float h = player.getHealth();
        return !Float.isFinite(h);
    }

    private static void check(ServerPlayer player, boolean immediate) {
        UUID id = player.getUUID();
        if (isBrokenHealth(player)) {
            player.setHealth(Math.max(1.0f, Math.min(player.getMaxHealth(), 1.0f)));
            logRepair(player, "血量非有限值（NaN/Infinity）", player.getLastDamageSource());
            STUCK_STREAK.remove(id);
            return;
        }
        if (player.isRemoved() || player.getHealth() > 0.0f) {
            STUCK_STREAK.remove(id);
            return;
        }
        if (isDeadFlag(player)) {
            // 正常死亡流程里（死亡界面）：这是合法状态，不碰
            STUCK_STREAK.remove(id);
            return;
        }
        int streak = STUCK_STREAK.merge(id, 1, Integer::sum);
        if (!immediate && streak < REPAIR_AFTER_OBSERVATIONS) return;

        player.setHealth(1.0f);
        logRepair(player, immediate ? "登录时发现 0 血未死" : "持续 " + streak + " 次观测到 0 血未死",
                player.getLastDamageSource());
        STUCK_STREAK.remove(id);
    }

    /** 是否走了死亡流程（{@code dead} 字段）；{@code isDeadOrDying()} 把「0 血未死」也算进去了 */
    private static boolean isDeadFlag(ServerPlayer player) {
        try {
            return ((LivingEntityDeadAccessor) (Object) player).lensouls$isDeadFlag();
        } catch (Throwable t) {
            // 理论上不会发生；退化成「不识别」，宁可不动
            return true;
        }
    }

    private static void logRepair(ServerPlayer player, String why, DamageSource source) {
        String src = "?";
        try {
            if (source != null && source.typeHolder() != null) {
                src = source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("?");
            }
        } catch (Throwable ignored) {
        }
        int curseMask = CurseManager.activeMask(player);
        int twist = FeatherTwitcherHandler.getTwist(player);
        boolean defying = FeatherTwitcherHandler.isDefying(player);
        LOGGER.warn("[FalseDeath] 已修复假死：玩家={} 原因={} 血量={} 上限={} deadFlag={} 模式={} 扭曲={} 诅咒掩码={} 阻死={} 最后伤害={}",
                player.getGameProfile().getName(),
                why,
                player.getHealth(),
                player.getMaxHealth(),
                isDeadFlag(player),
                player.gameMode.getGameModeForPlayer(),
                twist,
                Integer.toBinaryString(curseMask),
                defying,
                src);
    }
}
