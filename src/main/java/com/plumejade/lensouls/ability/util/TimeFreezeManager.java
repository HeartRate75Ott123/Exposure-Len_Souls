package com.plumejade.lensouls.ability.util;

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
        for (LivingEntity e : entitiesInFrame) {
            if (e == null || e.isRemoved()) continue;
            if (e instanceof Player) continue;
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
        // 定身成功集含 block_factorys_bosses 受保护 BOSS 时：给拍照者抗5 + 满抗击退（持续整个定格时长）
        for (LivingEntity e : entitiesInFrame) {
            if (e == null || e.isRemoved()) continue;
            if (!frozenEntities.contains(e.getId())) continue;
            if (com.plumejade.lensouls.boss.BossGuardHelper.isProtectedBoss(e)) {
                com.plumejade.lensouls.boss.BossGuardHelper.apply(source, remainingTicks);
                break;
            }
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
