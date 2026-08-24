package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.integration.BossPhotoProjHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家左键挥击信号（C2S）。
 * <p>
 * 客户端 {@code Minecraft.startAttack()} 每次左键按下（含空手/对空气）发送，
 * 服务端触发 Boss 照片弹幕判定。与 {@code Player#attack} / BetterCombat
 * {@code handleAttackRequest} 信号并存，由 {@code BossPhotoProjHelper.onSwing} 内 3 tick 去重。
 */
public class PhotoSwingPacket implements CustomPacketPayload {

    public static final Type<PhotoSwingPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "photo_swing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoSwingPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {},
                    buf -> new PhotoSwingPacket()
            );

    @Override
    @NotNull
    public Type<PhotoSwingPacket> type() {
        return TYPE;
    }

    /** 服务端处理器 */
    public static void handle(PhotoSwingPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                BossPhotoProjHelper.onSwing(sp);
            }
        });
    }
}
