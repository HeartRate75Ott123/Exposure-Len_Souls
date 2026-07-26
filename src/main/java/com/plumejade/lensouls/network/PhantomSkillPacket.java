package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.entity.BossPhantomType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C 虚影技能特效包——通知客户端播放 BOSS 技能特效（粒子+音效）。
 */
public class PhantomSkillPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PhantomSkillPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "phantom_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhantomSkillPacket> STREAM_CODEC =
            StreamCodec.ofMember(PhantomSkillPacket::encode, PhantomSkillPacket::new);

    private final int bossTypeOrdinal;

    public PhantomSkillPacket(BossPhantomType bossType) {
        this.bossTypeOrdinal = bossType.ordinal();
    }

    private PhantomSkillPacket(RegistryFriendlyByteBuf buf) {
        this.bossTypeOrdinal = buf.readInt();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(bossTypeOrdinal);
    }

    // ========== Payload 接口 ==========

    @Override
    @NotNull
    public CustomPacketPayload.Type<PhantomSkillPacket> type() { return TYPE; }

    public int getBossTypeOrdinal() { return bossTypeOrdinal; }
}
