package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * N公司2级员工「camera_shoot」的视觉同步包 (S2C)。
 * <p>
 * 同一条包同时承担两件事，用 {@link #screenFx} 区分：
 * <ul>
 *   <li><b>所有收到的客户端</b>：在员工位置爆出银白色发光魂尘（与 {@code SOUL_WISP} 同一套粒子形态，
 *       换成银白配色）；</li>
 *   <li><b>只有被拍到（吃到迟缓/挖掘疲劳/虚弱）的玩家</b>：屏幕里弹出「取景框 + 横向失焦 + 闪白」
 *       的纯客户端后处理（{@link com.plumejade.lensouls.client.fx.PhotoShotScreenFx}）。</li>
 * </ul>
 * 服务端按"是否在 32 格 debuff 圈里"逐个玩家决定 {@code screenFx}，所以一条包就够，
 * 不必拆成广播包 + 单发包两条。
 */
public class Level2StaffCameraFxPacket implements CustomPacketPayload {

    public static final Type<Level2StaffCameraFxPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "level2_staff_camera_fx"));

    public static final StreamCodec<FriendlyByteBuf, Level2StaffCameraFxPacket> STREAM_CODEC =
            StreamCodec.ofMember(Level2StaffCameraFxPacket::encode, Level2StaffCameraFxPacket::new);

    private final double x;
    private final double y;
    private final double z;
    private final float height;
    private final boolean screenFx;

    public Level2StaffCameraFxPacket(double x, double y, double z, float height, boolean screenFx) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.height = height;
        this.screenFx = screenFx;
    }

    private Level2StaffCameraFxPacket(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.height = buf.readFloat();
        this.screenFx = buf.readBoolean();
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(height);
        buf.writeBoolean(screenFx);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Level2StaffCameraFxPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                var mc = net.minecraft.client.Minecraft.getInstance();
                var level = mc.level;
                if (level == null) return;

                // 世界空间：员工身上爆出银白发光魂尘（谁收到谁看得到）
                com.plumejade.lensouls.client.fx.Level2StaffFx.cameraBurst(level,
                        new net.minecraft.world.phys.Vec3(packet.x, packet.y, packet.z), packet.height);

                // 屏幕空间：只有"被拍到"的那位玩家看到取景框
                if (packet.screenFx) {
                    com.plumejade.lensouls.client.fx.ScreenFxManager.push(
                            new com.plumejade.lensouls.client.fx.PhotoShotScreenFx());
                }
            } catch (Throwable ignored) {
            }
        });
    }
}
