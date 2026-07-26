package com.plumejade.lensouls.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record GunKillData(int kills) {
    public static final Codec<GunKillData> CODEC = RecordCodecBuilder.create(
        inst -> inst.group(
            Codec.INT.fieldOf("kills").forGetter(GunKillData::kills)
        ).apply(inst, GunKillData::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, GunKillData> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, GunKillData::kills,
            GunKillData::new);
}
