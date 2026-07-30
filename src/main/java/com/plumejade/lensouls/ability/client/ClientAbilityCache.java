package com.plumejade.lensouls.ability.client;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 客户端能力缓存。
 * <p>
 * 由 S2C AbilitySyncPacket 更新，供输入处理器、HUD 和客户端 Mixin 使用。
 * 同时缓存空间扭曲球心坐标，用于客户端侧提前判断交互是否有效。
 */
public class ClientAbilityCache {

    private static AbilityType currentEnabled;
    private static boolean spatialWarpActive = false;
    private static Vec3 warpCenter = Vec3.ZERO;
    private static String warpDimension = "";

    public static void set(int enabledOrdinal, boolean swActive,
                           double warpX, double warpY, double warpZ, String warpDimension) {
        AbilityType[] values = AbilityType.values();
        if (enabledOrdinal >= 0 && enabledOrdinal < values.length) {
            AbilityType previous = currentEnabled;
            currentEnabled = values[enabledOrdinal];
            if (previous != currentEnabled) {
            }
        } else {
            LenSouls.LOGGER.warn("[ClientAbilityCache] 无效 ordinal: {}", enabledOrdinal);
        }
        if (spatialWarpActive != swActive) {
        }
        spatialWarpActive = swActive;
        warpCenter = new Vec3(warpX, warpY, warpZ);
        ClientAbilityCache.warpDimension = warpDimension == null ? "" : warpDimension;
    }

    // ========== 状态查询 ==========

    public static AbilityType getEnabled() { return currentEnabled; }
    public static boolean isSpatialWarpActive() { return spatialWarpActive; }
    public static Vec3 getWarpCenter() { return warpCenter; }
    public static String getWarpDimension() { return warpDimension; }

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
