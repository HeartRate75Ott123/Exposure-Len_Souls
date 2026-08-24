package com.plumejade.lensouls.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * 瞄准判定工具：判断是否「瞄准」某个实体。
 * <p>
 * 对多部件实体（九头蛇/娜迦等），父实体中心可能远离准星；只要其任一存活子部件落在
 * 玩家视锥（锥角内）与射程内，即视为瞄准该父实体——实现「拍子实体局部也能追溯到本体」。
 */
public final class AimTargetUtil {

    private AimTargetUtil() {
    }

    /**
     * 玩家是否瞄准了给定实体（或其任一子部件）。
     *
     * @param player        玩家
     * @param entity        待判定的实体（父实体）
     * @param range         最大射程
     * @param halfAngleDeg  锥半角（度），如 30 表示总锥角 60°
     */
    public static boolean isAimedAt(Player player, LivingEntity entity, double range, double halfAngleDeg) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double maxDistSqr = range * range;
        double cos = Math.cos(Math.toRadians(halfAngleDeg));

        if (entity.distanceToSqr(player) <= maxDistSqr) {
            Vec3 dir = entity.position().subtract(eye).normalize();
            if (look.dot(dir) >= cos) return true;
        }

        PartEntity<?>[] parts = entity.getParts();
        if (parts != null) {
            for (PartEntity<?> p : parts) {
                if (p == null || !p.isAlive()) continue;
                if (p.distanceToSqr(player) > maxDistSqr) continue;
                Vec3 pdir = p.getEyePosition().subtract(eye).normalize();
                if (look.dot(pdir) >= cos) return true;
            }
        }
        return false;
    }
}
