package com.plumejade.lensouls.client;

import com.plumejade.lensouls.boss.FreezeRejectParticlePacket;
import com.plumejade.lensouls.boss.ToughnessHitSoundPacket;
import com.plumejade.lensouls.boss.ToughnessParticlePacket;
import com.plumejade.lensouls.particle.ModParticleTypes;
import com.plumejade.lensouls.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * S2C 包客户端处理（音效/粒子）。
 * <p>
 * 与 Exposure 的 ClientPacketsHandler 同模式：S2C packet 类会被服务端加载（通道双端注册），
 * 其 handle 只能委托到这里——本类虽引用 Minecraft 等 {@code @OnlyIn(CLIENT)} 类，
 * 但自身无 {@code @OnlyIn} 注解，服务端加载 packet 类时不会触发本类加载；
 * 仅在客户端收到包执行 handle 时才加载本类。
 */
public class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    public static void handleToughnessHitSound(ToughnessHitSoundPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        Entity entity = level.getEntity(packet.getEntityId());
        if (!(entity instanceof LivingEntity le) || !le.isAlive()) return;

        SoundEvent sound = packet.isFail() ? ModSounds.TOUGHNESS_FAIL.get() : ModSounds.TOUGHNESS_CHANGE.get();
        Random rng = new Random();
        float volume = 0.7f + rng.nextFloat() * 0.5f;
        float pitch = 0.8f + rng.nextFloat() * 0.4f;

        level.playLocalSound(
                le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(),
                sound,
                SoundSource.PLAYERS,
                volume, pitch,
                false
        );
    }

    public static void handleToughnessParticle(ToughnessParticlePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        Entity entity = level.getEntity(packet.getEntityId());
        if (!(entity instanceof LivingEntity le) || !le.isAlive()) return;

        var player = mc.player;
        if (player == null) return;

        // 从实体中心指向玩家本地坐标
        Vec3 entityCenter = le.position().add(0, le.getBbHeight() * 0.5, 0);
        Vec3 toPlayer = player.position().add(0, player.getEyeHeight() * 0.5, 0)
                .subtract(entityCenter).normalize();

        if (packet.isBreak()) {
            // 破碎：十字架（5 条 120 度锥角，加速）+ 冲击波（1 个环形扩散）
            spawnConeParticles(level, entityCenter, toPlayer, 120.0,
                    5, ModParticleTypes.TOUGHNESS_BREAK.get(), 0.8, 1.2);
            // 韧性圆环扩散（固定时间实体中心，非锥形散射）
            level.addParticle(ModParticleTypes.TOUGHNESS_SHOCKWAVE.get(),
                    entityCenter.x, entityCenter.y, entityCenter.z,
                    0, 0, 0);
        } else {
            // 击中：平面四边形粒子（向玩家扩散）
            spawnBurstParticles(level, entityCenter,
                    10, ModParticleTypes.TOUGHNESS_HIT.get(), 0.3, 0.9);
        }
    }

    public static void handleFreezeRejectParticle(FreezeRejectParticlePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        Entity entity = level.getEntity(packet.getEntityId());
        if (!(entity instanceof LivingEntity le) || !le.isAlive()) return;

        var player = mc.player;
        if (player == null) return;

        Vec3 center = le.position().add(0, le.getBbHeight() * 0.5, 0);
        Vec3 toPlayer = player.position().add(0, player.getEyeHeight() * 0.5, 0)
                .subtract(center).normalize();

        // 平面四边形方式向玩家方向锥形散射（120 度锥角，5 条加速）
        spawnConeParticles(level, center, toPlayer, 120.0,
                5, ModParticleTypes.FREEZE_REJECT.get(), 0.15, 0.45);
    }

    /**
     * 锥角范围内朝目标方向散射粒子。
     *
     * @param level     客户端世界
     * @param origin    粒子原点（实体中心）
     * @param direction 朝向玩家方向（单位向量）
     * @param coneDeg   锥角（度）
     * @param count     粒子数量
     * @param type      粒子类型
     * @param minSpeed  最小速度
     * @param maxSpeed  最大速度
     */
    private static void spawnConeParticles(Level level, Vec3 origin, Vec3 direction,
                                           double coneDeg, int count, SimpleParticleType type,
                                           double minSpeed, double maxSpeed) {
        var random = level.random;
        double halfRad = Math.toRadians(coneDeg * 0.5);

        // 局部坐标系（direction = Z 轴）
        Vec3 zAxis = direction;
        Vec3 xAxis;
        if (Math.abs(zAxis.y) < 0.99) {
            xAxis = new Vec3(1, 0, 0).cross(zAxis).normalize();
        } else {
            xAxis = new Vec3(0, 0, 1).cross(zAxis).normalize();
        }
        Vec3 yAxis = zAxis.cross(xAxis).normalize();

        for (int i = 0; i < count; i++) {
            // 锥体球坐标均匀分布
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = random.nextDouble() * halfRad;

            double speed = minSpeed + random.nextDouble() * (maxSpeed - minSpeed);

            double sinPhi = Math.sin(phi);
            // 局部方向
            double lx = sinPhi * Math.cos(theta);
            double ly = sinPhi * Math.sin(theta);
            double lz = Math.cos(phi);

            // 转换到世界空间
            double vx = (lx * xAxis.x + ly * yAxis.x + lz * zAxis.x) * speed;
            double vy = (lx * xAxis.y + ly * yAxis.y + lz * zAxis.y) * speed;
            double vz = (lx * xAxis.z + ly * yAxis.z + lz * zAxis.z) * speed;

            level.addParticle(type, origin.x, origin.y, origin.z, vx, vy, vz);
        }
    }

    /**
     * 从实体中心向玩家方向散射粒子（全方向均匀分布）。
     *
     * @param level     客户端世界
     * @param origin    粒子原点（实体中心）
     * @param count     粒子数量
     * @param type      粒子类型
     * @param minSpeed  最小速度
     * @param maxSpeed  最大速度
     */
    private static void spawnBurstParticles(Level level, Vec3 origin,
                                            int count, SimpleParticleType type,
                                            double minSpeed, double maxSpeed) {
        var random = level.random;
        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;          // 水平全角度
            double phi = (random.nextDouble() - 0.5) * Math.PI * 0.6;  // -54°~+54°
            double speed = minSpeed + random.nextDouble() * (maxSpeed - minSpeed);

            double vx = Math.cos(phi) * Math.cos(theta) * speed;
            double vy = Math.sin(phi) * speed;
            double vz = Math.cos(phi) * Math.sin(theta) * speed;

            level.addParticle(type, origin.x, origin.y, origin.z, vx, vy, vz);
        }
    }
}