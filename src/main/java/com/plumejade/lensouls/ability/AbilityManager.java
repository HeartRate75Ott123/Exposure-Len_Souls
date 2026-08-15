package com.plumejade.lensouls.ability;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.network.AbilitySyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
// PlayerRespawnEvent is used as PlayerEvent.PlayerRespawnEvent (inner class of PlayerEvent)
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 能力系统服务端单例管理器。
 * <p>
 * 职责：能力切换、解锁/锁定、NBT 持久化、S2C 同步、首次解锁播报。
 */
public class AbilityManager {

    private static final AbilityManager INSTANCE = new AbilityManager();
    private static final String NBT_KEY = "lensouls_abilities";

    private final Map<UUID, PlayerAbilityData> cache = new ConcurrentHashMap<>();

    public static AbilityManager getInstance() {
        return INSTANCE;
    }

    // ========== 公开 API ==========

    public AbilityType getEnabled(Player player) {
        return get(player).getEnabled();
    }

    /**
     * 循环切换到下一个已解锁能力。
     *
     * @return 是否切换成功（false 表示仅有一个能力，未切换）
     */
    public boolean cycleToNextEnabled(ServerPlayer player) {
        PlayerAbilityData data = get(player);
        AbilityType next = data.cycleToNext();
        save(player, data);
        syncToClient(player);
        return next != null;
    }

    public boolean isUnlocked(Player player, AbilityType type) {
        return get(player).isUnlocked(type);
    }

    /**
     * 直接设置当前启用能力（GUI 卡片「选择」按钮用）。
     * 仅在目标能力已解锁时生效。
     *
     * @return 是否设置成功
     */
    public boolean setEnabled(ServerPlayer player, AbilityType type) {
        PlayerAbilityData data = get(player);
        if (!data.isUnlocked(type)) return false;
        data.setEnabled(type);
        save(player, data);
        syncToClient(player);
        return true;
    }

    /**
     * 设置能力的解锁状态。
     * true→false 且该能力当前启用时，自动回退到弱点透镜。
     */
    public void setUnlocked(ServerPlayer player, AbilityType type, boolean value) {
        PlayerAbilityData data = get(player);
        boolean wasUnlocked = data.isUnlocked(type);

        if (wasUnlocked == value) return;

        data.setUnlocked(type, value);
        save(player, data);
        syncToClient(player);

        if (value && !wasUnlocked) {
            // 解锁成功提示（消息栏）
            player.sendSystemMessage(
                    Component.translatable("message.lensouls.ability.unlocked",
                            Component.translatable(type.getNameKey()))
                            .copy().withStyle(net.minecraft.ChatFormatting.GREEN));
        }
    }

    // ========== 空间扭曲（以照片坐标为圆心） ==========

    public boolean isSpatialWarpActive(Player player) {
        return get(player).isSpatialWarpActive();
    }

    /** 获取空间扭曲圈中心坐标 */
    public Vec3 getWarpCenter(Player player) {
        return get(player).getWarpCenter();
    }

    /** 获取空间扭曲所在维度 */
    public String getWarpDimension(Player player) {
        return get(player).getWarpDimension();
    }

    /**
     * 以照片拍摄坐标激活空间扭曲圈。
     *
     * @param center 照片拍摄时的玩家坐标
     * @param dimId  维度 ResourceLocation 字符串
     */
    public void activateSpatialWarp(ServerPlayer player, Vec3 center, String dimId) {
        get(player).setWarpCenter(center, dimId);
        save(player, get(player));
        syncToClient(player);

        player.displayClientMessage(
                Component.translatable("ability.lensouls.spatial_warp.enabled")
                        .copy().withStyle(net.minecraft.ChatFormatting.GREEN), true);

    }

    /** 关闭空间扭曲 */
    public void deactivateSpatialWarp(ServerPlayer player) {
        PlayerAbilityData data = get(player);
        if (data.isSpatialWarpActive()) {
            data.resetSpatialWarp();
            save(player, data);
            syncToClient(player);
            player.displayClientMessage(
                    Component.translatable("ability.lensouls.spatial_warp.disabled")
                            .copy().withStyle(net.minecraft.ChatFormatting.YELLOW), true);
        }
    }

    /** 强制重置空间扭曲（区块卸载/维度切换/切能力时调用） */
    public void resetSpatialWarp(ServerPlayer player) {
        PlayerAbilityData data = get(player);
        if (data.isSpatialWarpActive()) {
            Vec3 c = data.getWarpCenter();
            data.resetSpatialWarp();
            save(player, data);
            syncToClient(player);
            player.displayClientMessage(
                    Component.translatable("ability.lensouls.spatial_warp.expired")
                            .copy().withStyle(net.minecraft.ChatFormatting.YELLOW), true);
        }
    }

    // ========== 描述标记查询 ==========

    public boolean hasSentDescription(Player player, AbilityType type) {
        return get(player).hasSentDescription(type);
    }

    /** 解锁顺序（旧→新，最近解锁在尾部），GUI「新解锁排最前」用 */
    public java.util.List<AbilityType> getUnlockOrder(Player player) {
        return get(player).getUnlockOrder();
    }

    // ========== S2C 同步 ==========

    public void syncToClient(ServerPlayer player) {
        PlayerAbilityData data = get(player);
        AbilityType enabled = data.getEnabled();
        long unlockedMask = 0L;
        AbilityType[] values = AbilityType.values();
        for (int i = 0; i < values.length; i++) {
            if (data.isUnlocked(values[i])) unlockedMask |= (1L << i);
        }
        // 解锁顺序（GUI 排序用）
        java.util.List<AbilityType> order = data.getUnlockOrder();
        int[] unlockOrder = new int[order.size()];
        for (int i = 0; i < order.size(); i++) {
            unlockOrder[i] = order.get(i).ordinal();
        }
        boolean swActive = data.isSpatialWarpActive();
        double wx = 0, wy = 0, wz = 0;
        String wDim = "";
        if (swActive) {
            Vec3 c = data.getWarpCenter();
            wx = c.x; wy = c.y; wz = c.z;
            wDim = data.getWarpDimension();
        }
        PacketDistributor.sendToPlayer(player,
                new AbilitySyncPacket(enabled != null ? enabled.ordinal() : -1,
                        unlockedMask, unlockOrder, swActive, wx, wy, wz, wDim));
    }

    // ========== 持久化 ==========

    private PlayerAbilityData get(Player player) {
        PlayerAbilityData data = cache.computeIfAbsent(player.getUUID(), uuid -> load(player));
        LenSouls.LOGGER.trace("[AbilityManager] get: player={}, cacheHit={}", player.getName().getString(),
                cache.containsKey(player.getUUID()));
        return data;
    }

    private PlayerAbilityData load(Player player) {
        CompoundTag tag = player.getPersistentData().getCompound(NBT_KEY);
        if (tag.isEmpty()) {
            PlayerAbilityData data = new PlayerAbilityData();
            save(player, data);
            return data;
        }
        PlayerAbilityData data = PlayerAbilityData.deserialize(tag);
        return data;
    }

    private void save(Player player, PlayerAbilityData data) {
        player.getPersistentData().put(NBT_KEY, data.serialize());
        LenSouls.LOGGER.trace("[AbilityManager] save: player={}", player.getName().getString());
    }

    public void unload(Player player) {
        cache.remove(player.getUUID());
    }

    // ========== 事件监听 ==========

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            getInstance().get(sp); // 触发加载
            getInstance().syncToClient(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            getInstance().unload(event.getEntity());
        }
    }

    /**
     * 服务端停止时清空缓存，防止跨存档污染。
     * <p>
     * 单机集成服务器切换存档时，若玩家登出事件未触发，同 UUID 玩家会命中旧存档
     * 残留缓存（离线模式 UUID 由用户名固定生成），导致新档凭空出现已解锁能力。
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        getInstance().cache.clear();
    }

    /**
     * 玩家死亡后实体克隆时保留能力数据。
     * <p>
     * 将旧实体的能力数据复制到新实体，避免因磁盘存档滞后丢失已解锁的能力。
     * 新实体复活后通过 {@code onPlayerRespawn} 同步到客户端。
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)) return;
        if (!event.isWasDeath()) return; // 仅死亡需要复制，返回主世界不需要

        // 通过 get() 从缓存或旧玩家 NBT 加载能力数据，再写入新玩家 NBT
        // 新玩家 load() 时会从 NBT 读到刚写入的数据
        var mgr = getInstance();
        PlayerAbilityData data = mgr.get(oldPlayer);
        newPlayer.getPersistentData().put(NBT_KEY, data.serialize());

    }

    /**
     * 玩家复活后同步能力状态到客户端。
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            getInstance().syncToClient(sp);
        }
    }
}
