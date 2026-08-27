package com.plumejade.lensouls.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 药水玻璃板携带的数据组件：可记录多种药水效果，每条含效果 id、等级(amplifier)、时长(刻)。
 * 装配到相机滤镜槽后，拍照时 {@link FilterPhotoHandler} 读取并施加这些效果。
 */
public record PotionFilterData(List<Entry> effects) {

    public record Entry(ResourceLocation effect, int amplifier, int duration) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("effect").forGetter(Entry::effect),
                Codec.INT.fieldOf("amplifier").forGetter(Entry::amplifier),
                Codec.INT.fieldOf("duration").forGetter(Entry::duration)
        ).apply(instance, Entry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        ResourceLocation.STREAM_CODEC, Entry::effect,
                        ByteBufCodecs.VAR_INT, Entry::amplifier,
                        ByteBufCodecs.VAR_INT, Entry::duration,
                        Entry::new);
    }

    public static final Codec<PotionFilterData> CODEC = Entry.CODEC.listOf()
            .xmap(PotionFilterData::new, PotionFilterData::effects);

    public static final StreamCodec<RegistryFriendlyByteBuf, PotionFilterData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public PotionFilterData decode(RegistryFriendlyByteBuf buf) {
                    int n = buf.readVarInt();
                    List<Entry> list = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        list.add(Entry.STREAM_CODEC.decode(buf));
                    }
                    return new PotionFilterData(list);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, PotionFilterData value) {
                    buf.writeVarInt(value.effects().size());
                    for (Entry e : value.effects()) {
                        Entry.STREAM_CODEC.encode(buf, e);
                    }
                }
            };
}
