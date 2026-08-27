package com.plumejade.lensouls.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 药水滤镜数据：记录所携带的原版药水效果与等级。
 * 附着在 potion_filter 物品上，拍照时由 FilterPhotoHandler 读回并施加。
 */
public record PotionFilterData(ResourceLocation effect, int amplifier) {
    public static final Codec<PotionFilterData> CODEC = RecordCodecBuilder.create(
            inst -> inst.group(
                            ResourceLocation.CODEC.fieldOf("effect").forGetter(PotionFilterData::effect),
                            Codec.INT.fieldOf("amplifier").forGetter(PotionFilterData::amplifier)
                    )
                    .apply(inst, PotionFilterData::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PotionFilterData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.map(ResourceLocation::parse, ResourceLocation::toString),
                    PotionFilterData::effect,
                    ByteBufCodecs.INT, PotionFilterData::amplifier,
                    PotionFilterData::new);
}
