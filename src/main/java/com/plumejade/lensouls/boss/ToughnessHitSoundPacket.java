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
 * S2C：BOSS 韧性相关音效。
 * <p>
 * {@code fail=false} 播放削韧成功音效（toughness_change），
 * {@code fail=true} 播放无敌阻挡音效（toughness_fail）。
 * 音量和音调均有随机浮动。
 * <p>
 * 客户端处理委托给 {@link ClientPacketHandlers}（本类会被服务端加载，不能直接引用客户端类）。
 */
public class ToughnessHitSoundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToughnessHitSoundPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "toughness_hit_sound"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToughnessHitSoundPacket> STREAM_CODEC =
            StreamCodec.ofMember(ToughnessHitSoundPacket::encode, ToughnessHitSoundPacket::new);

    private final int entityId;
    private final boolean fail;  // true = 无敌阻挡音效，false = 削韧成功音效

    public ToughnessHitSoundPacket(int entityId, boolean fail) {
        this.entityId = entityId;
        this.fail = fail;
    }

    private ToughnessHitSoundPacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.fail = buf.readBoolean();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(fail);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<ToughnessHitSoundPacket> type() {
        return TYPE;
    }

    public int getEntityId() {
        return entityId;
    }

    public boolean isFail() {
        return fail;
    }

    public static void handle(ToughnessHitSoundPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandlers.handleToughnessHitSound(packet));
    }
}