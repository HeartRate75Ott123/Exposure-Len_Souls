package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：滤镜<b>成功施加到某个生物身上</b>时的世界特效（"对焦收束"）。
 * <p>
 * 只发 {@code entityId + 颜色}，位置由客户端每帧自己向实体取——这样被施加者移动/被击退时，
 * 特效会跟着它平滑滑动，而不是钉在发包那一瞬间的坐标上。
 * <p>
 * 广播范围 {@code sendToPlayersTrackingEntityAndSelf}：世界事件，旁观者与本人（自拍）都该看见。
 */
public class FilterAppliedFxPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FilterAppliedFxPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "filter_applied_fx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterAppliedFxPacket> STREAM_CODEC =
            StreamCodec.ofMember(FilterAppliedFxPacket::encode, FilterAppliedFxPacket::new);

    private final int entityId;
    private final float r;
    private final float g;
    private final float b;

    public FilterAppliedFxPacket(int entityId, float r, float g, float b) {
        this.entityId = entityId;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    private FilterAppliedFxPacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.r = buf.readFloat();
        this.g = buf.readFloat();
        this.b = buf.readFloat();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeFloat(r);
        buf.writeFloat(g);
        buf.writeFloat(b);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getEntityId() {
        return entityId;
    }

    public float getR() {
        return r;
    }

    public float getG() {
        return g;
    }

    public float getB() {
        return b;
    }

    public static void handle(FilterAppliedFxPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                com.plumejade.lensouls.client.ClientPacketHandlers.handleFilterAppliedFx(packet);
            } catch (Throwable ignored) {
            }
        });
    }
}
