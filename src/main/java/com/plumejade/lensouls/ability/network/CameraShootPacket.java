package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import io.github.mortuusars.exposure.world.camera.Camera;
import io.github.mortuusars.exposure.world.entity.CameraOperator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：饰品栏相机按下快门。
 * <p>
 * 原版 Exposure 的右键释放只认两种相机：手上的（走 {@code CameraItem.use}）与相机架上的
 * （{@code MinecraftMixin.onStartUseItem} 里只判 {@code CameraOnStand}）。饰品栏相机两者都不是，
 * 客户端按右键不会发任何东西 ⇒ 永远拍不了。这里补一个专用包，让服务端对当前活跃相机调
 * {@link Camera#release()}，与手持完全同一条链路（冷却、消耗、出片都在里面）。
 */
public class CameraShootPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CameraShootPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "camera_shoot"));

    public static final CameraShootPacket INSTANCE = new CameraShootPacket();

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraShootPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    @NotNull
    public CustomPacketPayload.Type<CameraShootPacket> type() {
        return TYPE;
    }

    public static void handle(CameraShootPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            Camera active = ((CameraOperator) sp).getActiveExposureCamera();
            if (active == null) return;
            if (active.isEmpty()) return;
            active.release();
        });
    }
}
