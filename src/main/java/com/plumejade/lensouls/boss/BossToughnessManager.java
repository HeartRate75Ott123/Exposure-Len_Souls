package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BOSS 韧性管理器（服务端单例）。
 * <p>
 * 管理所有 BOSS 实体的韧性状态：记录、削韧、破防、恢复。
 * 通过 {@link LivingDamageEvent.Pre} 注入减伤，
 * 通过 {@link ServerTickEvent.Post} 驱动 tick。
 */
public class BossToughnessManager {

    private static final BossToughnessManager INSTANCE = new BossToughnessManager();
    /** entity UUID → toughness */
    private final Map<UUID, BossToughnessData> dataMap = new ConcurrentHashMap<>();

    public static BossToughnessManager getInstance() { return INSTANCE; }

    // ========== 韧性数据 API ==========

    /** 注册实体为 BOSS（由外部识别 BOSS 后调用） */
    public BossToughnessData register(LivingEntity entity) {
        BossToughnessData data = new BossToughnessData(computeRequiredHits(entity));
        data.setRecoveryFromSeconds(Config.TOUGH_RECOVERY_SECONDS.get());
        dataMap.put(entity.getUUID(), data);
        return data;
    }

    /** 获取实体的韧性数据，没有则返回 null */
    public BossToughnessData get(LivingEntity entity) {
        return dataMap.get(entity.getUUID());
    }

    /** 实体是否有韧性数据 */
    public boolean has(LivingEntity entity) {
        return dataMap.containsKey(entity.getUUID());
    }

    /** 移除实体的韧性数据（实体死亡/卸载时） */
    public void remove(LivingEntity entity) {
        BossToughnessData removed = dataMap.remove(entity.getUUID());
        if (removed != null) {
        }
    }

    /** 查询实体是否处于无敌期 */
    public boolean isInvincible(LivingEntity entity) {
        BossToughnessData data = dataMap.get(entity.getUUID());
        return data != null && data.isInvincible();
    }

    /** 设置实体的无敌剩余 tick */
    public void setInvincibleTicks(LivingEntity entity, int ticks) {
        BossToughnessData data = dataMap.get(entity.getUUID());
        if (data != null) data.setInvincibleTicks(ticks);
    }

    // ========== 削韧 ==========

    /** 拍照/要害打击时调用，削 1 次韧性，返回削韧后的数据 */
    public BossToughnessData hit(LivingEntity entity) {
        return hit(entity, null);
    }

    public BossToughnessData hit(LivingEntity entity, @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer player) {
        BossToughnessData data = dataMap.get(entity.getUUID());
        if (data == null) return null;

        if (data.isBroken()) return data;

        boolean wasInvincible = data.isInvincible();
        int invTicks = BossToughnessAttributes.getInvincibleTicks(entity);
        if (player != null) {
            invTicks = com.plumejade.lensouls.integration.TrophyModifierHandler.applyInvincibleModifier(player, invTicks);
        }
        float hitMultiplier = 1.0f;
        if (player != null) {
            hitMultiplier = com.plumejade.lensouls.integration.TrophyModifierHandler.applyHitsModifier(player, 1.0f);
        }
        boolean actuallyHit = data.hit(invTicks, hitMultiplier);
        boolean isBroken = data.isBroken();

        if (!entity.level().isClientSide) {
            if (actuallyHit) {
                if (!isBroken) {
                    PacketDistributor.sendToPlayersTrackingEntity(entity, new ToughnessParticlePacket(entity.getId(), false));
                }
                PacketDistributor.sendToPlayersTrackingEntity(entity, new ToughnessHitSoundPacket(entity.getId(), false));
            } else if (wasInvincible) {
                PacketDistributor.sendToPlayersTrackingEntity(entity, new ToughnessHitSoundPacket(entity.getId(), true));
            }
        }

        if (isBroken) {
            onToughnessBroken(entity, data, player);
        }
        broadcastToughness(entity);
        return data;
    }

    // ========== 破防处理 ==========

    private void onToughnessBroken(LivingEntity entity, BossToughnessData data) {
        onToughnessBroken(entity, data, null);
    }

    private void onToughnessBroken(LivingEntity entity, BossToughnessData data, @javax.annotation.Nullable net.minecraft.server.level.ServerPlayer player) {
        // 发射破韧粒子（十字 + 冲击波环）
        if (!entity.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, new ToughnessParticlePacket(entity.getId(), true));
        }

        int stunTicks = BossToughnessAttributes.getStunDurationTicks(entity);
        if (player != null) {
            stunTicks = com.plumejade.lensouls.integration.TrophyModifierHandler.applyStunModifier(player, stunTicks);
        }
        data.setStunTicks(stunTicks);

        // 应用定身效果（参考 FreezeTracker 的冻结逻辑）
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setNoGravity(true);
        }
        entity.setNoGravity(true);

    }

    /** 由外部（或 tick 逻辑）在定身结束时调用 */
    public void onStunEnd(LivingEntity entity) {
        BossToughnessData data = dataMap.get(entity.getUUID());
        if (data == null) return;

        // 解除定身
        if (entity instanceof Mob mob) {
            mob.setNoAi(false);
            mob.setNoGravity(false);
        }
        entity.setNoGravity(false);

        data.onStunEnd();

        // 播放韧性重置音效
        PacketDistributor.sendToPlayersTrackingEntity(entity,
                new ToughnessHitSoundPacket(entity.getId(), false));


        // 广播恢复后的状态
        broadcastToughness(entity);
    }

    // ========== 减伤计算 ==========

    /** 计算当前减伤倍率 [0..1]，返回实际减免比例 */
    public float getDamageReduction(LivingEntity entity) {
        BossToughnessData data = dataMap.get(entity.getUUID());
        if (data == null) return 0;
        float maxReduction = (float) (double) Config.TOUGH_DAMAGE_REDUCTION.get();
        return data.getDamageReduction(maxReduction);
    }

    /** 应用减伤到伤害值 */
    public float applyDamageReduction(LivingEntity entity, float damage) {
        float reduction = getDamageReduction(entity);
        if (reduction <= 0) return damage;
        float result = damage * (1.0f - reduction);
        return result;
    }

    // ========== Tick 驱动 ==========

    public void tick() {
        if (dataMap.isEmpty()) return;

        List<UUID> pendingStunEnd = new ArrayList<>();
        List<UUID> pendingResetSound = new ArrayList<>();

        for (Map.Entry<UUID, BossToughnessData> entry : dataMap.entrySet()) {
            BossToughnessData data = entry.getValue();

            // 记录恢复倒计时状态，用于检测恢复完成
            int prevRecoveryTicks = data.getRecoveryTicks();
            boolean prevBroken = data.isBroken();

            data.tick();

            // 韧性恢复完成（非破防状态下 recoveryTicks 归零 → reset 被调用）
            if (!prevBroken && prevRecoveryTicks > 0 && data.getRecoveryTicks() <= 0) {
                pendingResetSound.add(entry.getKey());
            }

            // 定身到期标记，稍后查找实体解除
            if (data.isBroken() && data.getStunRemainingTicks() <= 0) {
                pendingStunEnd.add(entry.getKey());
            }
        }

        // 韧性恢复完成音效（不在破防状态下恢复韧性）
        if (!pendingResetSound.isEmpty()) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (UUID uuid : pendingResetSound) {
                    for (ServerLevel sl : server.getAllLevels()) {
                        Entity entity = sl.getEntity(uuid);
                        if (entity instanceof LivingEntity le && le.isAlive()) {
                            PacketDistributor.sendToPlayersTrackingEntity(le,
                                    new ToughnessHitSoundPacket(le.getId(), false));
                            break;
                        }
                    }
                }
            }
        }

        // 定身到期解除（需实体上下文，在 tick 中查找）
        if (!pendingStunEnd.isEmpty()) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (UUID uuid : pendingStunEnd) {
                    for (ServerLevel sl : server.getAllLevels()) {
                        Entity entity = sl.getEntity(uuid);
                        if (entity instanceof LivingEntity le && le.isAlive()) {
                            onStunEnd(le);
                            break;
                        }
                    }
                }
            }
        }

        // 每 tick 广播客户端（含恢复进度、定身状态）
        broadcastAll();
    }

    // ========== S2C 广播 ==========

    /** 广播单实体韧性数据到追踪者 */
    public void broadcastToughness(LivingEntity entity) {
        BossToughnessData data = dataMap.get(entity.getUUID());
        if (data == null) return;
        if (entity.level().isClientSide) return;

        List<ToughnessEntry> single = List.of(
                new ToughnessEntry(entity.getId(), data.getProgress(), data.isBroken(), data.isInvincible()));
        PacketDistributor.sendToPlayersTrackingEntity(entity, new ToughnessSyncPacket(single));
    }

    /** 广播所有韧性数据到所有玩家 */
    public void broadcastAll() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // 收集所有在线实体的韧性数据
        List<ToughnessEntry> allEntries = new ArrayList<>();
        for (Map.Entry<UUID, BossToughnessData> entry : dataMap.entrySet()) {
            BossToughnessData data = entry.getValue();
            // 查找实体所在的维度
            for (ServerLevel sl : server.getAllLevels()) {
                Entity entity = sl.getEntity(entry.getKey());
                if (entity instanceof LivingEntity le && le.isAlive()) {
                    allEntries.add(new ToughnessEntry(le.getId(), data.getProgress(), data.isBroken(), data.isInvincible()));
                    break;
                }
            }
        }

        if (allEntries.isEmpty()) return;

        ToughnessSyncPacket packet = new ToughnessSyncPacket(allEntries);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    // ========== 计算所需削韧次数 ==========

    /** 计算击破韧性需要的削韧次数，优先查 per-entity 覆盖配置，否则返回默认值。 */
    public static int computeRequiredHits(LivingEntity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String idStr = id.toString();

        // 1. 配置文件覆盖：格式 "modid:entityid:count"
        for (String entry : Config.TOUGHNESS_HITS_OVERRIDES.get()) {
            String[] parts = entry.split(":");
            if (parts.length >= 3) {
                String entryId = parts[0] + ":" + parts[1];
                if (entryId.equals(idStr)) {
                    try {
                        return Integer.parseInt(parts[2]);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // 2. BossToughnessAttributes 硬编码覆盖
        int attrHits = BossToughnessAttributes.getRequiredHits(entity);
        if (attrHits != Config.TOUGHNESS_DEFAULT_HITS.get()) {
            return attrHits;
        }

        // 3. 默认值
        return Config.TOUGHNESS_DEFAULT_HITS.get();
    }

    // ========== 事件 ==========

    /** 实体死亡时清理韧性数据 */
    @SubscribeEvent
    public void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        remove(event.getEntity());
    }
}
