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
 * S2C：时间定格被 BOSS 韧性拒绝时，向客户端发送天青色粒子。
 * <p>
 * 服务端 {@link com.plumejade.lensouls.ability.handler.TimeFreezeHandler} 发出，
 * 客户端从实体中心向外扩散天青色平行四边形粒子（同削韧粒子发射器风格）。
 */
public class FreezeRejectParticlePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FreezeRejectParticlePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "freeze_reject_particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FreezeRejectParticlePacket> STREAM_CODEC =
            StreamCodec.ofMember(FreezeRejectParticlePacket::encode, FreezeRejectParticlePacket::new);

    private final int entityId;

    public FreezeRejectParticlePacket(int entityId) {
        this.entityId = entityId;
    }

    private FreezeRejectParticlePacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FreezeRejectParticlePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level == null) return;

            Entity entity = level.getEntity(packet.entityId);
            if (!(entity instanceof LivingEntity le) || !le.isAlive()) return;

            var player = mc.player;
            if (player == null) return;

            Vec3 center = le.position().add(0, le.getBbHeight() * 0.5, 0);
            Vec3 toPlayer = player.position().add(0, player.getEyeHeight() * 0.5, 0)
                    .subtract(center).normalize();

            // 破韧样式：朝玩家方向锥形散射（120°锥角，5个，低速）
            spawnConeParticles(level, center, toPlayer, 120.0,
                    5, ModParticleTypes.FREEZE_REJECT.get(), 0.15, 0.45);
        });
    }

    /**
     * 朝目标方向锥角范围内散射粒子（破韧样式）。
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
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = random.nextDouble() * halfRad;

            double speed = minSpeed + random.nextDouble() * (maxSpeed - minSpeed);

            double sinPhi = Math.sin(phi);
            double lx = sinPhi * Math.cos(theta);
            double ly = sinPhi * Math.sin(theta);
            double lz = Math.cos(phi);

            double vx = (lx * xAxis.x + ly * yAxis.x + lz * zAxis.x) * speed;
            double vy = (lx * xAxis.y + ly * yAxis.y + lz * zAxis.y) * speed;
            double vz = (lx * xAxis.z + ly * yAxis.z + lz * zAxis.z) * speed;

            level.addParticle(type, origin.x, origin.y, origin.z, vx, vy, vz);
        }
    }
}
