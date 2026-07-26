package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C：同步所有 BOSS 的韧性数据。
 * <p>
 * 服务端 {@link BossToughnessManager} 在状态变更或每 tick 广播，
 * 客户端 {@link BossToughnessClientCache} 更新供 {@link ToughnessBarRenderer} 和 {@link StunGlintRenderTypes} 使用。
 */
public class ToughnessSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToughnessSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "toughness_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToughnessSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(ToughnessSyncPacket::encode, ToughnessSyncPacket::new);

    private final List<ToughnessEntry> entries;

    public ToughnessSyncPacket(List<ToughnessEntry> entries) {
        this.entries = entries;
    }

    public List<ToughnessEntry> getEntries() {
        return entries;
    }

    private ToughnessSyncPacket(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ToughnessEntry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int entityId = buf.readInt();
            float progress = buf.readFloat();
            boolean broken = buf.readBoolean();
            boolean invincible = buf.readBoolean();
            list.add(new ToughnessEntry(entityId, progress, broken, invincible));
        }
        this.entries = list;
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (ToughnessEntry entry : entries) {
            buf.writeInt(entry.entityId());
            buf.writeFloat(entry.progress());
            buf.writeBoolean(entry.broken());
            buf.writeBoolean(entry.invincible());
        }
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<ToughnessSyncPacket> type() {
        return TYPE;
    }

    public static void handle(ToughnessSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            BossToughnessClientCache.update(packet.entries);
        });
    }
}
