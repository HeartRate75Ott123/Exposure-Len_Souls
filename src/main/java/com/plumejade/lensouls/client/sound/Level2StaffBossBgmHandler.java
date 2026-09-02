package com.plumejade.lensouls.client.sound;

import com.plumejade.lensouls.entity.Level2StaffBossEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * N公司2级员工 BGM 控制器（客户端）—— 仿 Legendary-Monsters {@code BossMusicPlayer}。
 * <p>
 * 每客户端 tick 检测玩家 32 格内最近的 {@link Level2StaffBossEntity}：
 * <ul>
 *   <li>在范围内 → 确保 BGM 实例在播放（单例复用），目标音量按距离 0~32 线性映射；</li>
 *   <li>离开范围 / 无 Boss → 目标音量归零，实例淡出后自动停止。</li>
 * </ul>
 * 音量平滑由 {@link Level2StaffBgmSound#tick()} 实现。
 */
public class Level2StaffBossBgmHandler {

    private static final double RANGE = 32.0D;
    private static final double FULL_VOLUME_RANGE = 8.0D;

    private static Level2StaffBgmSound active;

    private Level2StaffBossBgmHandler() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Player player = mc.player;
        List<Level2StaffBossEntity> bosses = mc.level.getEntitiesOfClass(Level2StaffBossEntity.class,
                AABB.ofSize(player.position(), RANGE * 2, RANGE * 2, RANGE * 2));

        // 最近的 Boss
        Level2StaffBossEntity nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (Level2StaffBossEntity e : bosses) {
            if (!e.isAlive()) continue;
            double d = player.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                nearest = e;
            }
        }

        SoundManager sm = mc.getSoundManager();

        if (nearest == null) {
            // 无 Boss 在范围：目标音量归零（实例内淡出后自动 stop）
            if (active != null) {
                active.setTargetVolume(0f);
            }
            return;
        }

        // 目标音量：0~FULL 满音量，FULL~RANGE 线性降为 0
        double dist = Math.sqrt(bestDist);
        float target = (float) net.minecraft.util.Mth.clamp(
                1.0D - (dist - FULL_VOLUME_RANGE) / (RANGE - FULL_VOLUME_RANGE), 0.0D, 1.0D);

        if (active == null) {
            active = new Level2StaffBgmSound(nearest);
            sm.play(active);
        } else if (active.getBoss() != nearest) {
            // 目标 Boss 变化：复用同一实例（同 BGM），仅重绑目标，不重建避免打断
            active.setBoss(nearest);
        }
        // 仿 Legendary-Monsters BossMusicPlayer：声道缺失（如大退重进后 SoundManager 重建清空）时，
        // 每 tick 用 SoundManager.isActive 检测并重新 play 同一实例，避免 BGM 永久静音
        if (!sm.isActive(active)) {
            sm.play(active);
        }
        active.setTargetVolume(target);
    }

    /** 当前播放实例是否 == 传入实例（单例互斥，供 SoundBossMusic.canPlaySound 判断） */
    public static boolean isCurrent(Level2StaffBgmSound sound) {
        return active == sound;
    }

    /** 实例内部淡出完成后清除引用 */
    public static void clearInstance() {
        active = null;
    }

    /** 断线/重进清理 */
    public static void reset(PlayerEvent.PlayerLoggedOutEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (active != null) {
            if (mc.getSoundManager() != null) mc.getSoundManager().stop(active);
            active = null;
        }
    }
}
