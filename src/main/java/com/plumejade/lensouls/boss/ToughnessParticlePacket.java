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
 * S2C：BOSS 韧性削减粒子。
 * <p>
 * 由 {@link BossToughnessManager#hit(LivingEntity)} 触发，
 * 客户端根据 {@code isBreak} 生成粒子（韧性破碎/击中粒子）。
 * <p>
 * 客户端处理委托给 {@link ClientPacketHandlers}（本类会被服务端加载，不能直接引用客户端类）。
 */
public class ToughnessParticlePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToughnessParticlePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "toughness_particle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToughnessParticlePacket> STREAM_CODEC =
            StreamCodec.ofMember(ToughnessParticlePacket::encode, ToughnessParticlePacket::new);

    private final int entityId;
    private final boolean isBreak;

    public ToughnessParticlePacket(int entityId, boolean isBreak) {
        this.entityId = entityId;
        this.isBreak = isBreak;
    }

    private ToughnessParticlePacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.isBreak = buf.readBoolean();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(isBreak);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getEntityId() {
        return entityId;
    }

    public boolean isBreak() {
        return isBreak;
    }

    public static void handle(ToughnessParticlePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandlers.handleToughnessParticle(packet));
    }
}