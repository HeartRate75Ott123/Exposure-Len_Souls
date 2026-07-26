package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * S2C：BOSS 韧性相关音效。
 * <p>
 * {@code fail=false} 播放削韧成功音效（toughness_change），
 * {@code fail=true} 播放无敌阻挡音效（toughness_fail）。
 * 音量和音调均有随机浮动。
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

    public static void handle(ToughnessHitSoundPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level == null) return;

            Entity entity = level.getEntity(packet.entityId);
            if (!(entity instanceof LivingEntity le) || !le.isAlive()) return;

            SoundEvent sound = packet.fail ? ModSounds.TOUGHNESS_FAIL.get() : ModSounds.TOUGHNESS_CHANGE.get();
            Random rng = new Random();
            float volume = 0.7f + rng.nextFloat() * 0.5f;
            float pitch  = 0.8f + rng.nextFloat() * 0.4f;

            level.playLocalSound(
                    le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(),
                    sound,
                    SoundSource.PLAYERS,
                    volume, pitch,
                    false
            );
        });
    }
}
