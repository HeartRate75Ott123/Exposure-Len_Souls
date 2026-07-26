package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * S2C：BOSS 韧性被削减时播放松弛音效。
 * <p>
 * 由服务端 {@link BossToughnessManager#hit(LivingEntity)} 发出，
 * 客户端收到后随机播放 4 个韧性变化音效之一，并浮动音量和音调。
 */
public class ToughnessHitSoundPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToughnessHitSoundPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "toughness_hit_sound"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToughnessHitSoundPacket> STREAM_CODEC =
            StreamCodec.ofMember(ToughnessHitSoundPacket::encode, ToughnessHitSoundPacket::new);

    private final int entityId;

    public ToughnessHitSoundPacket(int entityId) {
        this.entityId = entityId;
    }

    private ToughnessHitSoundPacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<ToughnessHitSoundPacket> type() {
        return TYPE;
    }

    public static void handle(ToughnessHitSoundPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level == null) return;

            Entity entity = level.getEntity(packet.entityId);
            if (!(entity instanceof LivingEntity le) || !le.isAlive()) return;

            // 随机浮动音量 [0.7 ~ 1.2] + 音调 [0.8 ~ 1.2]
            Random rng = new Random();
            float volume = 0.7f + rng.nextFloat() * 0.5f;
            float pitch  = 0.8f + rng.nextFloat() * 0.4f;

            level.playLocalSound(
                    le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(),
                    ModSounds.TOUGHNESS_CHANGE.get(),
                    SoundSource.PLAYERS,
                    volume, pitch,
                    false
            );
        });
    }
}
