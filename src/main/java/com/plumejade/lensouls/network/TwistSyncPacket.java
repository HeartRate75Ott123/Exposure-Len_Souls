package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.client.TwistClientCache;
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
 * S2C：同步佩戴者的扭曲值给客户端（供左侧 bar 渲染）。
 * <p>
 * 扭曲值仅在合成/死亡/清零等低频时机变化，变化时发送一次即可。
 */
public class TwistSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TwistSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "twist_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TwistSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TwistSyncPacket::value,
                    ByteBufCodecs.BOOL, TwistSyncPacket::barVisible,
                    TwistSyncPacket::new);

    private final int value;

    /**
     * 「左侧条该不该显示」。
     * <p>
     * 客户端**判断不了**这件事：羽毛槽附件刻意不做 sync（见 {@code FeatherAttachments}），
     * 客户端只有在开过界面、被菜单同步过之后才知道玩家戴着 ②/④ —— 这正是
     * 「扭曲值必须开一次诅咒界面才显示」的根因。所以由服务端在发包时算好一并带过来。
     */
    private final boolean barVisible;

    public TwistSyncPacket(int value) {
        this(value, true);
    }

    public TwistSyncPacket(int value, boolean barVisible) {
        this.value = value;
        this.barVisible = barVisible;
    }

    public int value() {
        return value;
    }

    public boolean barVisible() {
        return barVisible;
    }

    /** 服务端发送给指定玩家（自动带上「该不该显示」：戴着 ② 或 ④ 才显示） */
    public static void send(ServerPlayer player, int value) {
        boolean visible = com.plumejade.lensouls.handler.FeatherTwitcherHandler.hasTwitcher(player)
                || com.plumejade.lensouls.handler.FeatherAbyssHandler.hasAbyss(player);
        PacketDistributor.sendToPlayer(player, new TwistSyncPacket(value, visible));
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<TwistSyncPacket> type() {
        return TYPE;
    }

    public static void handle(TwistSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> TwistClientCache.set(packet.value(), packet.barVisible()));
    }
}
