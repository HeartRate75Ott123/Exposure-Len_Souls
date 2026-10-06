package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：「本模组召唤的幻翼还剩多少刻」。
 *
 * <p>用途见 {@code SwarmPhantomFade}：幻翼的标记与时效<b>不能</b>塞进原版 {@code Phantom} 的同步数据槽
 * （{@code defineId} 的 id 按初始化时机分配，两端会错位并把客户端踢下线）。
 * 现在服务端只在<b>玩家开始追踪该幻翼时</b>（{@code PlayerEvent.StartTracking}）发一次余命，
 * 客户端拿它算渐隐曲线——不需要逐 tick 同步。
 */
public class SwarmPhantomPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SwarmPhantomPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "swarm_phantom"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwarmPhantomPacket> STREAM_CODEC =
            StreamCodec.ofMember(SwarmPhantomPacket::encode, SwarmPhantomPacket::new);

    private final int entityId;
    private final int remainingTicks;

    public SwarmPhantomPacket(int entityId, int remainingTicks) {
        this.entityId = entityId;
        this.remainingTicks = remainingTicks;
    }

    private SwarmPhantomPacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.remainingTicks = buf.readVarInt();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeVarInt(this.remainingTicks);
    }

    public int getEntityId() {
        return this.entityId;
    }

    public int getRemainingTicks() {
        return this.remainingTicks;
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<SwarmPhantomPacket> type() {
        return TYPE;
    }

    public static void handle(SwarmPhantomPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                com.plumejade.lensouls.client.ClientPacketHandlers.handleSwarmPhantom(packet));
    }
}
