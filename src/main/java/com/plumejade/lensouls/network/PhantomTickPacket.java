package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.entity.BossPhantomType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C 虚影阶段包——通知客户端幻灵进入蓄力/余辉阶段。
 * <p>
 * 爆发阶段仍由 {@link PhantomSkillPacket} 触发。
 * phase: 0=蓄力(CHARGE), 2=余辉(DECAY)
 */
public class PhantomTickPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PhantomTickPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "phantom_tick"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhantomTickPacket> STREAM_CODEC =
            StreamCodec.ofMember(PhantomTickPacket::encode, PhantomTickPacket::new);

    private final int bossTypeOrdinal;
    /** 0=蓄力, 1=爆发(暂未使用, 由 PhantomSkillPacket 处理), 2=余辉 */
    private final int phase;

    public PhantomTickPacket(BossPhantomType bossType, int phase) {
        this.bossTypeOrdinal = bossType.ordinal();
        this.phase = phase;
    }

    private PhantomTickPacket(RegistryFriendlyByteBuf buf) {
        this.bossTypeOrdinal = buf.readInt();
        this.phase = buf.readInt();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(bossTypeOrdinal);
        buf.writeInt(phase);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<PhantomTickPacket> type() { return TYPE; }

    public int getBossTypeOrdinal() { return bossTypeOrdinal; }
    public int getPhase() { return phase; }
}
