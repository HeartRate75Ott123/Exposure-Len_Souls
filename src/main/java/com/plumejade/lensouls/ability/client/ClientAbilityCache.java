package com.plumejade.lensouls.ability.client;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 客户端能力缓存。
 * <p>
 * 由 S2C AbilitySyncPacket 更新，供输入处理器、HUD 和客户端 Mixin 使用。
 * 同时缓存空间扭曲球心坐标，用于客户端侧提前判断交互是否有效。
 */
public class ClientAbilityCache {

    private static AbilityType currentEnabled;
    private static long unlockedMask = 0L;
    /** 解锁顺序（旧→新，最近解锁在尾部），GUI 排序用 */
    private static java.util.List<AbilityType> unlockOrder = new java.util.ArrayList<>();
    private static boolean spatialWarpActive = false;
    private static Vec3 warpCenter = Vec3.ZERO;
    private static String warpDimension = "";

    public static void set(long unlockedMask, int[] unlockOrder, boolean swActive,
                            double warpX, double warpY, double warpZ, String warpDimension) {
        AbilityType[] values = AbilityType.values();
        spatialWarpActive = swActive;
        ClientAbilityCache.unlockedMask = unlockedMask;
        ClientAbilityCache.unlockOrder = new java.util.ArrayList<>();
        for (int ordinal : unlockOrder) {
            if (ordinal >= 0 && ordinal < values.length) {
                ClientAbilityCache.unlockOrder.add(values[ordinal]);
            }
        }
        warpCenter = new Vec3(warpX, warpY, warpZ);
        ClientAbilityCache.warpDimension = warpDimension == null ? "" : warpDimension;
    }

    /**
     * 设置「当前手持相机的选中能力」镜像（S2C {@link com.plumejade.lensouls.ability.network.CameraAbilitySyncPacket}
     * 或客户端换机时本地播种）。-1 表示未选中。
     */
    public static void setHeldCameraSelected(int ordinal) {
        AbilityType[] values = AbilityType.values();
        if (ordinal == -1) {
            currentEnabled = null;
        } else if (ordinal >= 0 && ordinal < values.length) {
            currentEnabled = values[ordinal];
        } else {
            LenSouls.LOGGER.warn("[ClientAbilityCache] 无效 ordinal: {}", ordinal);
        }
    }

    // ========== 状态查询 ==========

    /** 退出世界时清空全部缓存，防止同 JVM 内切换存档导致跨存档污染 */
    public static void reset() {
        currentEnabled = null;
        unlockedMask = 0L;
        unlockOrder = new java.util.ArrayList<>();
        spatialWarpActive = false;
        warpCenter = Vec3.ZERO;
        warpDimension = "";
    }

    /** 客户端登出事件（切档/退出到标题时触发） */
    public static void onClientLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        reset();
    }

    public static AbilityType getEnabled() { return currentEnabled; }
    public static boolean isSpatialWarpActive() { return spatialWarpActive; }
    public static Vec3 getWarpCenter() { return warpCenter; }
    public static String getWarpDimension() { return warpDimension; }

    /** 查询能力是否已解锁（服务端解锁位图）。GUI 卡片置灰用。 */
    public static boolean isUnlocked(AbilityType type) {
        int ordinal = type.ordinal();
        if (ordinal < 0 || ordinal >= 64) return false;
        return (unlockedMask & (1L << ordinal)) != 0;
    }

    /** 解锁顺序（旧→新，最近解锁在尾部），GUI「新解锁排最前」用 */
    public static java.util.List<AbilityType> getUnlockOrder() {
        return unlockOrder;
    }

    /**
     * 判断目标位置是否在扭曲球内。
     * 使用玩家的 {@link Attributes#BLOCK_INTERACTION_RANGE} 作为球半径（不受 Mixin 膨胀影响）。
     */
    public static boolean isInWarpSphere(Vec3 target) {
        if (!spatialWarpActive || warpDimension.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (!mc.player.level().dimension().location().toString().equals(warpDimension)) return false;
        double range = mc.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        return target.distanceToSqr(warpCenter) <= range * range;
    }

    /**
     * 计算客户端射线/交互需要的最小触及距离，确保能到达扭曲球最远处。
     */
    public static double getWarpReachDistance() {
        if (!spatialWarpActive || warpDimension.isEmpty()) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        if (!mc.player.level().dimension().location().toString().equals(warpDimension)) return 0;
        double playerDist = mc.player.position().distanceTo(warpCenter);
        double range = mc.player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        return playerDist + range;
    }

    /** 计算实体攻击需要的最小触及距离（使用 ENTITY_INTERACTION_RANGE 作为球半径） */
    public static double getWarpEntityReachDistance() {
        if (!spatialWarpActive || warpDimension.isEmpty()) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        if (!mc.player.level().dimension().location().toString().equals(warpDimension)) return 0;
        double playerDist = mc.player.position().distanceTo(warpCenter);
        double range = mc.player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        return playerDist + range;
    }
}
