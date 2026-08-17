package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：时间定格 BOSS 技能拒绝时向客户端发送紫色粒子。
 * <p>
 * 由 {@link com.plumejade.lensouls.ability.handler.TimeFreezeHandler} 触发，
 * 客户端从实体位置向玩家方向散射紫色平面四边形粒子。
 * <p>
 * 客户端处理委托给 {@link ClientPacketHandlers}（本类会被服务端加载，不能直接引用客户端类）。
 */
public class FreezeRejectParticlePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FreezeRejectParticlePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "freeze_reject_particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FreezeRejectParticlePacket> STREAM_CODEC =
            StreamCodec.ofMember(FreezeRejectParticlePacket::encode, FreezeRejectParticlePacket::new);

    private final int entityId;

    public FreezeRejectParticlePacket(int entityId) {
        this.entityId = entityId;
    }

    private FreezeRejectParticlePacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getEntityId() {
        return entityId;
    }

    public static void handle(FreezeRejectParticlePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandlers.handleFreezeRejectParticle(packet));
    }
}