package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.util.BarrageToggle;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：请求切换照片弹幕开关（默认 B 键）。
 * <p>
 * 不传目标值而是让服务端自己翻转：弹幕状态<b>只</b>由服务端持有并裁决
 * （{@code BossPhotoProjHelper.trigger} 读的是服务端那份），客户端那份只用于画状态提示，
 * 不参与任何判定，所以不存在「客户端说关了但服务端还在弹」的执行偏差。
 * <p>
 * 翻转后立刻回发 {@link BarrageStatePacket} 让客户端把乐观值对齐到权威值。
 */
public class BarrageTogglePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BarrageTogglePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "barrage_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BarrageTogglePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {},
                    buf -> new BarrageTogglePacket());

    @Override
    @NotNull
    public CustomPacketPayload.Type<BarrageTogglePacket> type() {
        return TYPE;
    }

    /** 服务端处理器：翻转持久化状态并回发权威值 */
    public static void handle(BarrageTogglePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.level().isClientSide) return;
            BarrageStatePacket.send(player, BarrageToggle.flip(player));
        });
    }
}
