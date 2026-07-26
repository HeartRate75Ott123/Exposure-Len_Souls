package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.particle.ModParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：BOSS 韧性粒子包。
 * <p>
 * 服务端 {@link BossToughnessManager#hit(LivingEntity)} 发出，
 * 客户端根据 {@code isBreak} 决定粒子类型和数量，从实体中心朝本地玩家方向散射。
 */
public class ToughnessParticlePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToughnessParticlePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "toughness_particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToughnessParticlePacket> STREAM_CODEC =
            StreamCodec.ofMember(ToughnessParticlePacket::encode, ToughnessParticlePacket::new);

    private final int entityId;
    private final boolean isBreak;

    public ToughnessParticlePacket(int entityId, boolean isBreak) {
        this.entityId = entityId;
        this.isBreak = isBreak;
    }

    private ToughnessParticlePacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.isBreak = buf.readBoolean();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(isBreak);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToughnessParticlePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level == null) return;

            Entity entity = level.getEntity(packet.entityId);
            if (!(entity instanceof LivingEntity le) || !le.isAlive()) return;

            var player = mc.player;
            if (player == null) return;

            // 从实体中心指向本地玩家
            Vec3 entityCenter = le.position().add(0, le.getBbHeight() * 0.5, 0);
            Vec3 toPlayer = player.position().add(0, player.getEyeHeight() * 0.5, 0)
                    .subtract(entityCenter).normalize();

            if (packet.isBreak) {
                // 破韧：十字架（5个，120°锥角，高速）+ 冲击波（1个，固定）
                spawnConeParticles(level, entityCenter, toPlayer, 120.0,
                        5, ModParticleTypes.TOUGHNESS_BREAK.get(), 0.8, 1.2);
                // 冲击波圆环（固定在实体中心，不动不散射）
                level.addParticle(ModParticleTypes.TOUGHNESS_SHOCKWAVE.get(),
                        entityCenter.x, entityCenter.y, entityCenter.z,
                        0, 0, 0);
            } else {
                // 削韧：平行四边形环绕实体向外扩散
                spawnBurstParticles(level, entityCenter,
                        10, ModParticleTypes.TOUGHNESS_HIT.get(), 0.3, 0.9);
            }
        });
    }

    /**
     * 在锥角范围内朝目标方向散射粒子。
     *
     * @param level      客户端世界
     * @param origin     发射原点（实体中心）
     * @param direction  朝向玩家方向（单位向量）
     * @param coneDeg    锥角（度）
     * @param count      粒子数
     * @param type       粒子类型
     * @param minSpeed   最小速度
     * @param maxSpeed   最大速度
     */
    private static void spawnConeParticles(Level level, Vec3 origin, Vec3 direction,
                                           double coneDeg, int count, SimpleParticleType type,
                                           double minSpeed, double maxSpeed) {
        var random = level.random;
        double halfRad = Math.toRadians(coneDeg * 0.5);

        // 构建局部坐标系：direction = Z 轴
        Vec3 zAxis = direction;
        Vec3 xAxis;
        if (Math.abs(zAxis.y) < 0.99) {
            xAxis = new Vec3(1, 0, 0).cross(zAxis).normalize();
        } else {
            xAxis = new Vec3(0, 0, 1).cross(zAxis).normalize();
        }
        Vec3 yAxis = zAxis.cross(xAxis).normalize();

        for (int i = 0; i < count; i++) {
            // 锥体内随机方向：球坐标
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = random.nextDouble() * halfRad;

            double speed = minSpeed + random.nextDouble() * (maxSpeed - minSpeed);

            double sinPhi = Math.sin(phi);
            // 局部向量
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
     * 环绕实体中心向外扩散粒子（全方向球面分布）。
     *
     * @param level      客户端世界
     * @param origin     发射原点（实体中心）
     * @param count      粒子数
     * @param type       粒子类型
     * @param minSpeed   最小速度
     * @param maxSpeed   最大速度
     */
    private static void spawnBurstParticles(Level level, Vec3 origin,
                                            int count, SimpleParticleType type,
                                            double minSpeed, double maxSpeed) {
        var random = level.random;
        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;          // 水平全方向
            double phi = (random.nextDouble() - 0.5) * Math.PI * 0.6;  // -54°~+54°
            double speed = minSpeed + random.nextDouble() * (maxSpeed - minSpeed);

            double vx = Math.cos(phi) * Math.cos(theta) * speed;
            double vy = Math.sin(phi) * speed;
            double vz = Math.cos(phi) * Math.sin(theta) * speed;

            level.addParticle(type, origin.x, origin.y, origin.z, vx, vy, vz);
        }
    }
}
