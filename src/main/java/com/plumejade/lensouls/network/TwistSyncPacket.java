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

    /**
     * 服务端发送给指定玩家。
     * <p>
     * <b>数值以"实际佩戴的那一款"的真实值为准</b>，不再信任调用点传入的 value：
     * 不同调用点各自算值（例如装入 ④ 折翼沉渊时它自己的扭曲值刚初始化 = 0），
     * 会把已经存在的 ② 扭曲之人的真实值短暂覆盖成 0（实测症状：已装 ② 再装 ④，
     * 左侧条停在 0 直到下次更新）。这里统一收口：
     * ② 优先（0–100）→ 否则 ④（其内部 0–200，除以 2 映射到同一条）→ 都没有才用传入值。
     */
    public static void send(ServerPlayer player, int value) {
        boolean twitcher = com.plumejade.lensouls.handler.FeatherTwitcherHandler.hasTwitcher(player);
        boolean abyss = com.plumejade.lensouls.handler.FeatherAbyssHandler.hasAbyss(player);
        // ② 与 ④ 各自独立存值，而左侧条只有一根 ⇒ 显示**两者中较大者**（④ 的值 ÷2 归一到 0–100）。
        // 否则同时佩戴时，④ 的"每合成 1 个物品 +1"永远看不见（实测：②+④ 一起戴，
        // 疯狂合成一堆物品，条仍停在 ② 的旧值）。
        int tw = twitcher
                ? com.plumejade.lensouls.handler.FeatherTwitcherHandler.getTwist(player) : -1;
        int ab = abyss
                ? Math.round(com.plumejade.lensouls.handler.FeatherAbyssHandler.getTwist(player) / 2.0f) : -1;
        int real = (tw < 0 && ab < 0) ? value : Math.max(0, Math.max(tw, ab));
        PacketDistributor.sendToPlayer(player, new TwistSyncPacket(real, twitcher || abyss));
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
