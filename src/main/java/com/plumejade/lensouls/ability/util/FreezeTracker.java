package com.plumejade.lensouls.ability.util;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.network.FreezeSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 时间定格追踪器（服务端单例）。
 * <p>
 * 管理所有活跃的时间定格：冻结/解冻实体、tick 递减、到期清理。
 */
public class FreezeTracker {

    private static final FreezeTracker INSTANCE = new FreezeTracker();

    private final Map<UUID, FreezeEntry> activeFreezes = new ConcurrentHashMap<>();

    public static FreezeTracker getInstance() { return INSTANCE; }

    /** 冻结一组实体 */
    public void freeze(Player source, Set<Entity> targets, int durationTicks) {
        MinecraftServer server = source.getServer();
        FreezeEntry entry = new FreezeEntry(new HashSet<>(targets), server, durationTicks);
        activeFreezes.put(source.getUUID(), entry);
        applyFreeze(targets, true);
        // 同步冻结 ID 到客户端
        List<Integer> ids = targets.stream().map(Entity::getId).toList();
        PacketDistributor.sendToPlayer((ServerPlayer) source, new FreezeSyncPacket(true, ids));
    }

    /** 每 tick 递减，到期解冻 */
    public void tick() {
        if (activeFreezes.isEmpty()) return;

        Iterator<Map.Entry<UUID, FreezeEntry>> it = activeFreezes.entrySet().iterator();
        int expired = 0;
        while (it.hasNext()) {
            Map.Entry<UUID, FreezeEntry> entry = it.next();
            FreezeEntry fe = entry.getValue();
            fe.remainingTicks--;

            if (fe.remainingTicks <= 0) {
                LenSouls.LOGGER.trace("[FreezeTracker] tick: 到期解冻, sourceIdx={}, targets={}",
                        entry.getKey(), fe.targets.size());
                // 同步解冻到客户端
                ServerPlayer sourcePlayer = fe.server.getPlayerList().getPlayer(entry.getKey());
                if (sourcePlayer != null) {
                    List<Integer> ids = fe.targets.stream().map(Entity::getId).toList();
                    PacketDistributor.sendToPlayer(sourcePlayer, new FreezeSyncPacket(false, ids));
                }
                unfreezeAll(fe.targets);
                it.remove();
                expired++;
            } else {
                applyFreeze(fe.targets, true);
            }
        }
        if (expired > 0) {
        }
    }

    public boolean isPlayerSource(Player player) {
        return activeFreezes.containsKey(player.getUUID());
    }

    /** 玩家当前的冻结是否还有活着的实体 */
    public boolean hasLiveFrozenEntities(Player player) {
        FreezeEntry entry = activeFreezes.get(player.getUUID());
        if (entry == null) return false;
        return entry.targets.stream().anyMatch(e -> e.isAlive());
    }

    /** 强制解冻并清理玩家的冻结条目 */
    public void forceUnfreeze(Player player) {
        FreezeEntry entry = activeFreezes.remove(player.getUUID());
        if (entry != null) {
            unfreezeAll(entry.targets);
        }
    }

    public boolean isEntityFrozen(Entity entity) {
        return activeFreezes.values().stream().anyMatch(e -> e.targets.contains(entity));
    }

    /**
     * 解除单个实体的时间冻结（状态互斥用：破定接管时调用）。
     * 从所有冻结条目中移除该实体并同步解冻到各来源玩家；恢复其 AI。
     */
    public void unfreezeEntity(Entity entity) {
        if (activeFreezes.isEmpty()) return;
        List<UUID> sources = new ArrayList<>();
        for (Map.Entry<UUID, FreezeEntry> entry : activeFreezes.entrySet()) {
            if (entry.getValue().targets.remove(entity)) {
                sources.add(entry.getKey());
            }
        }
        if (sources.isEmpty()) return;
        for (UUID source : sources) {
            ServerPlayer sp = null;
            MinecraftServer server = activeFreezes.get(source) != null
                    ? activeFreezes.get(source).server : null;
            if (server != null) {
                sp = server.getPlayerList().getPlayer(source);
            }
            if (sp != null) {
                PacketDistributor.sendToPlayer(sp, new FreezeSyncPacket(false, List.of(entity.getId())));
            }
        }
        if (!isEntityFrozen(entity)) {
            applyFreeze(Set.of(entity), false);
        }
    }

    /** 玩家退出时清理其冻结的所有实体 */
    public void unfreezePlayerSource(Player source) {
        FreezeEntry entry = activeFreezes.remove(source.getUUID());
        if (entry != null) {
            unfreezeAll(entry.targets);
        }
    }

    // ========== 冻结/解冻效果 ==========

    /** 实体是否仍处于破定定身（韧性打爆状态）——此时解冻不得恢复 AI */
    private static boolean isStunLocked(Entity e) {
        if (!(e instanceof LivingEntity living)) return false;
        com.plumejade.lensouls.boss.BossToughnessData data =
                com.plumejade.lensouls.boss.BossToughnessManager.getInstance().get(living);
        return data != null && data.isBroken();
    }

    private void applyFreeze(Set<Entity> targets, boolean frozen) {
        for (Entity e : targets) {
            if (e instanceof Mob mob) {
                boolean stunLocked = isStunLocked(mob);
                mob.setNoAi(frozen || stunLocked);
                mob.setNoGravity(frozen || stunLocked);
                if (frozen) {
                    mob.setDeltaMovement(Vec3.ZERO);
                    mob.hurtMarked = true;
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 254, false, false, false));
                } else {
                    mob.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                }
            } else if (e instanceof Player player) {
                player.setNoGravity(frozen);
                if (frozen) {
                    player.setDeltaMovement(Vec3.ZERO);
                    player.hurtMarked = true;
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 254, false, false, false));
                } else {
                    player.setNoGravity(false);
                    player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                }
            }
        }
    }

    private void unfreezeAll(Set<Entity> targets) {
        applyFreeze(targets, false);
    }

    // ========== 内部数据结构 ==========

    private static class FreezeEntry {
        final Set<Entity> targets;
        final MinecraftServer server;
        int remainingTicks;

        FreezeEntry(Set<Entity> targets, MinecraftServer server, int remainingTicks) {
            this.targets = targets;
            this.server = server;
            this.remainingTicks = remainingTicks;
        }
    }
}
