package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.util.BarrageToggle;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：把服务端的弹幕开关权威值回发给客户端（客户端只用于显示那一行状态提示）。
 * <p>
 * 发送时机：玩家登录时补发一次、每次 {@link BarrageTogglePacket} 翻转后立刻回发。
 * 客户端按下去时会先乐观翻转再发 C2S，本包负责把「实际生效的值」纠正回来
 * （丢包/被拒时客户端不会一直显示错的状态）。
 */
public class BarrageStatePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BarrageStatePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "barrage_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BarrageStatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, BarrageStatePacket::enabled,
                    BarrageStatePacket::new);

    private final boolean enabled;

    public BarrageStatePacket(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /** 服务端 → 客户端 */
    public static void send(ServerPlayer player, boolean enabled) {
        PacketDistributor.sendToPlayer(player, new BarrageStatePacket(enabled));
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<BarrageStatePacket> type() {
        return TYPE;
    }

    public static void handle(BarrageStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                com.plumejade.lensouls.client.ClientBarrageState.setEnabled(packet.enabled()));
    }
}
