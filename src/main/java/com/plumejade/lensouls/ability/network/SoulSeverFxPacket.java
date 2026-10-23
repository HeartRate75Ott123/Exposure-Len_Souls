package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 夺魂索命的视觉同步包 (S2C)。
 * <p>
 * 携带目标位置/身高与成败标记：客户端据此播"魂飞魄散"（绿色魂火 + 上升环 + 屏幕绿雾）。
 * 用坐标而不是实体 id——目标可能在判定后立刻死亡/被移除。
 */
public class SoulSeverFxPacket implements CustomPacketPayload {

    public static final Type<SoulSeverFxPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "soul_sever_fx"));

    public static final StreamCodec<FriendlyByteBuf, SoulSeverFxPacket> STREAM_CODEC =
            StreamCodec.ofMember(SoulSeverFxPacket::encode, SoulSeverFxPacket::new);

    private final double x;
    private final double y;
    private final double z;
    private final float height;
    private final boolean success;

    public SoulSeverFxPacket(double x, double y, double z, float height, boolean success) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.height = height;
        this.success = success;
    }

    private SoulSeverFxPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.height = buf.readFloat();
        this.success = buf.readBoolean();
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(height);
        buf.writeBoolean(success);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SoulSeverFxPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                var mc = net.minecraft.client.Minecraft.getInstance();
                var level = mc.level;
                if (level == null) return;
                var pos = new net.minecraft.world.phys.Vec3(packet.x, packet.y, packet.z);

                // 世界空间：魂火环 + 魂丝
                com.plumejade.lensouls.client.fx.WorldFxBursts.soulSever(level, pos, packet.height, packet.success);
                // 屏幕空间：绿色魂雾 + 从目标扩散的绿环（环心 = 身体中心，见 SoulSeverScreenFx）
                com.plumejade.lensouls.client.fx.ScreenFxManager.push(
                        new com.plumejade.lensouls.client.fx.SoulSeverScreenFx(pos, packet.height, packet.success));
            } catch (Throwable ignored) {
            }
        });
    }
}
