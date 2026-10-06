package com.plumejade.lensouls.ability.util;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.network.FreezeSyncPacket;
import com.plumejade.lensouls.boss.BossToughnessData;
import com.plumejade.lensouls.boss.BossToughnessManager;
import com.plumejade.lensouls.boss.FreezeRejectParticlePacket;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.UUID;

/**
 * 时间定格管理器（服务端单例）。
 * <p>
 * 时间定格 = 实体定身（学习破刹）：只定身拍摄瞬间画面内的生物
 * （Exposure {@code FrameAddedEvent.getEntitiesInFrame()}），玩家完全正常
 * tick——攻速/伤害不受影响。韧性目标（BOSS 韧性）有 30% 概率成功定身
 * （首次掷骰锁定），普通生物必定身。
 * <p>
 * 定身实体由 {@link com.plumejade.lensouls.mixin.BossStunTickMixin} 跳过
 * {@code LivingEntity.tick}（复用破刹定身管线），客户端由
 * {@link com.plumejade.lensouls.ability.client.ClientFreezeCache} 同步定身集
 * 驱动渲染冻结（partialTicks / 蓝描边 / 蓝 glint）。
 */
public class TimeFreezeManager {

    private static final TimeFreezeManager INSTANCE = new TimeFreezeManager();

    public static TimeFreezeManager getInstance() {
        return INSTANCE;
    }

    private MinecraftServer server;
    private UUID sourcePlayerId;
    private int remainingTicks;
    private final IntOpenHashSet frozenEntities = new IntOpenHashSet();
    /** 定身期间每个实体实际受到的伤害累计（键 = 实体 id，供「伤害达标提前解冻」用） */
    private final it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap accumulatedDamage =
            new it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap();
    private final java.util.Random random = new java.util.Random();

    private TimeFreezeManager() {
    }

    /** 触发时间定格：定身给定实体集（拍摄瞬间画面内生物）。 */
    public void freeze(MinecraftServer srv, ServerPlayer source, Collection<LivingEntity> entitiesInFrame) {
        if (isFrozen()) return;
        this.server = srv;
        this.sourcePlayerId = source.getUUID();
        this.remainingTicks = 100;
        frozenEntities.clear();
        accumulatedDamage.clear();
        // 目标选取口径统一在 PhotoTargets.freezable（不要玩家 / 不要拍摄者本人 /
        // 不要玩家驯服的宠物 / 不要本模组自己的召唤物——幻灵与幻影幻翼）
        for (LivingEntity e : com.plumejade.lensouls.util.PhotoTargets.freezable(source, entitiesInFrame)) {
            if (e.isRemoved()) continue;
            // 韧性目标：
            // 1) 破刹期间无法定格——100% miss（弹 miss 粒子）
            // 2) 未破刹时 30% 概率成功定身（首次掷骰锁定，失败弹 miss 粒子）
            // 破刹与定格定身互不冲突，各自独立到期（同时作用时定身持续到两者更晚者结束）
            BossToughnessData data = BossToughnessManager.getInstance().get(e);
            if (data != null) {
                if (data.isBroken()) {
                    PacketDistributor.sendToAllPlayers(new FreezeRejectParticlePacket(e.getId()));
                    continue;
                }
                if (random.nextFloat() >= 0.3f) {
                    PacketDistributor.sendToAllPlayers(new FreezeRejectParticlePacket(e.getId()));
                    continue;
                }
            }
            frozenEntities.add(e.getId());
        }
        LenSouls.LOGGER.debug("[TimeFreeze] 时间定格开始，定身 {} 个实体", frozenEntities.size());
        broadcast(true);
    }

    /** 服务端每 tick 递减，到期解除定身。 */
    public void tick() {
        if (server == null) return;
        remainingTicks--;
        if (remainingTicks <= 0) {
            LenSouls.LOGGER.debug("[TimeFreeze] 时间定格到期，解除定身");
            unfreeze();
        }
    }

    /**
     * 定身期间统计实体实际受到的伤害；累计达到「最大生命 × {@code toughStunBreakDamagePercent}（默认 16%）」
     * 时<b>提前解冻这一个实体</b>——完全模仿韧性定身的同款阈值
     * （见 {@link BossToughnessManager#addStunDamage}，共用同一配置项）。
     * <p>
     * 由 {@link com.plumejade.lensouls.boss.ToughnessDamageHandler#onLivingDamagePost} 在
     * {@code LivingDamageEvent.Post}（伤害已生效）调用，且只统计玩家造成的伤害。
     * <b>注意口径：时间定格定身期间韧性减伤照常生效</b>（用户明确要求：定格 ≠ 破定，
     * 对面还剩韧性就照常免伤），所以这里累计的是减伤后实际打出来的伤害。
     */
    public void addFreezeDamage(LivingEntity entity, float amount) {
        if (server == null || amount <= 0f) return;
        int id = entity.getId();
        if (!frozenEntities.contains(id)) return;
        double percent = Config.TOUGH_STUN_BREAK_DAMAGE_PERCENT.get();
        if (percent <= 0) return;
        float total = accumulatedDamage.addTo(id, amount) + amount;
        float threshold = (float) (entity.getMaxHealth() * percent);
        if (total >= threshold) {
            unfreezeEntity(id);
        }
    }

    /** 提前解冻单个实体（其余照旧），并广播剩余定身集给客户端。 */
    private void unfreezeEntity(int id) {
        if (!frozenEntities.remove(id)) return;
        accumulatedDamage.remove(id);
        LenSouls.LOGGER.debug("[TimeFreeze] 实体 {} 定身期间伤害达标，提前解冻", id);
        broadcast(true);
    }

    /** 当前是否处于时间定格。 */
    public boolean isFrozen() {
        return server != null;
    }

    /** 实体是否被时停定身（服务端判定）。 */
    public boolean isEntityFrozen(Entity entity) {
        return isFrozen() && frozenEntities.contains(entity.getId());
    }

    /** 玩家退出时清理（仅当其为冻结来源时解除定身）。 */
    public void unfreezePlayerSource(ServerPlayer player) {
        if (isFrozen() && sourcePlayerId != null && sourcePlayerId.equals(player.getUUID())) {
            unfreeze();
        }
    }

    private void unfreeze() {
        if (server == null) return;
        frozenEntities.clear();
        accumulatedDamage.clear();
        server = null;
        sourcePlayerId = null;
        remainingTicks = 0;
        broadcast(false);
    }

    private void broadcast(boolean frozen) {
        PacketDistributor.sendToAllPlayers(new FreezeSyncPacket(frozen,
                frozen ? frozenEntities.toIntArray() : new int[0]));
    }
}
