package com.plumejade.lensouls.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 镜魂冷却数据组件。
 * <p>
 * 记录冷却结束的绝对游戏刻和总冷却刻数，
 * 直接作为 DataComponent 附着在物品上，不依赖 CustomData NBT 序列化。
 */
public record SoulCooldownData(long endTime, int duration) {

    public static final Codec<SoulCooldownData> CODEC = RecordCodecBuilder.create(
            inst -> inst.group(
                    Codec.LONG.fieldOf("end").forGetter(SoulCooldownData::endTime),
                    Codec.INT.fieldOf("dur").forGetter(SoulCooldownData::duration)
            ).apply(inst, SoulCooldownData::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SoulCooldownData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, SoulCooldownData::endTime,
                    ByteBufCodecs.INT, SoulCooldownData::duration,
                    SoulCooldownData::new
            );

    /** 返回剩余刻数，≤ 0 表示已过期 */
    public long remainingTicks(long gameTime) {
        return endTime - gameTime;
    }
}
