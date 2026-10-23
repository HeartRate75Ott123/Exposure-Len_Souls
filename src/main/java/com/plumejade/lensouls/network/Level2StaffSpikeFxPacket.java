package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * N公司2级员工「spike」远程投掷的视觉同步包 (S2C)。
 * <p>
 * 原来这条射线是一路 {@code ParticleTypes.SMOKE} 撒黑烟（每 tick 6 颗），既糊又不像"魔法"。
 * 现在改成<b>纯客户端着色器特效</b>：服务端只在<b>释放瞬间</b>发一条包，携带起点 / 方向 / 射程，
 * 客户端用 {@link com.plumejade.lensouls.client.fx.Level2StaffFx#spike} 复现整条轨迹——
 * 员工身上先爆一圈银白「十字环」（聚焦收缩 → 向外扩散消散），随后一条深紫色、粗而清晰的
 * 魔法轨迹以与服务端相同的速度（0.7 格/tick）飞向目标。
 * <p>
 * 用坐标 + 方向而不是实体 id：射线是纯服务端状态，客户端没有任何同步载体。
 */
public class Level2StaffSpikeFxPacket implements CustomPacketPayload {

    public static final Type<Level2StaffSpikeFxPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "level2_staff_spike_fx"));

    public static final StreamCodec<FriendlyByteBuf, Level2StaffSpikeFxPacket> STREAM_CODEC =
            StreamCodec.ofMember(Level2StaffSpikeFxPacket::encode, Level2StaffSpikeFxPacket::new);

    private final double ox;
    private final double oy;
    private final double oz;
    private final float dx;
    private final float dy;
    private final float dz;
    private final float range;

    public Level2StaffSpikeFxPacket(double ox, double oy, double oz,
                                    double dx, double dy, double dz, float range) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.dx = (float) dx;
        this.dy = (float) dy;
        this.dz = (float) dz;
        this.range = range;
    }

    private Level2StaffSpikeFxPacket(FriendlyByteBuf buf) {
        this.ox = buf.readDouble();
        this.oy = buf.readDouble();
        this.oz = buf.readDouble();
        this.dx = buf.readFloat();
        this.dy = buf.readFloat();
        this.dz = buf.readFloat();
        this.range = buf.readFloat();
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeDouble(ox);
        buf.writeDouble(oy);
        buf.writeDouble(oz);
        buf.writeFloat(dx);
        buf.writeFloat(dy);
        buf.writeFloat(dz);
        buf.writeFloat(range);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Level2StaffSpikeFxPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.level == null) return;
                com.plumejade.lensouls.client.fx.Level2StaffFx.spike(
                        new net.minecraft.world.phys.Vec3(packet.ox, packet.oy, packet.oz),
                        new net.minecraft.world.phys.Vec3(packet.dx, packet.dy, packet.dz),
                        packet.range);
            } catch (Throwable ignored) {
            }
        });
    }
}
