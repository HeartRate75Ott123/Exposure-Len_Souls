package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C：照片套装面板排版调试开关。
 * <p>
 * 只发给执行调试指令的那名玩家（{@code /lensouls gui photo_set testopen|testfalse}）。
 * 客户端收到后设置 {@code PhotoEffectsDebug} 的静态开关，面板随即塞入人造长文本，用来检验分页 / 裁切。
 * 纯调试用途，正式玩法不会发送。
 */
public record PhotoSetDebugPacket(boolean enable) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PhotoSetDebugPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "photo_set_debug"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoSetDebugPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeBoolean(packet.enable()),
                    buf -> new PhotoSetDebugPacket(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<PhotoSetDebugPacket> type() {
        return TYPE;
    }

    /** 客户端处理器：写入调试开关 */
    public static void handle(PhotoSetDebugPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                com.plumejade.lensouls.client.tabs.PhotoEffectsDebug.setEnabled(packet.enable());
            } catch (Throwable t) {
                LenSouls.LOGGER.error("[PhotoSet] 排版调试开关同步失败", t);
            }
        });
    }
}
