package com.plumejade.lensouls.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record GunAmmoData(int currentAmmo, int maxAmmo) {
    public static final Codec<GunAmmoData> CODEC = RecordCodecBuilder.create(
        inst -> inst.group(
            Codec.INT.fieldOf("ammo").forGetter(GunAmmoData::currentAmmo),
            Codec.INT.fieldOf("max").forGetter(GunAmmoData::maxAmmo)
        ).apply(inst, GunAmmoData::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, GunAmmoData> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, GunAmmoData::currentAmmo,
            ByteBufCodecs.INT, GunAmmoData::maxAmmo,
            GunAmmoData::new);
}
