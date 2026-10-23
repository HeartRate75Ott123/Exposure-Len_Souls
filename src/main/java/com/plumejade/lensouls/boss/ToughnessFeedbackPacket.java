package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：削韧命中的**屏幕反馈**（只发给造成这次韧性伤害的玩家）。
 * <p>
 * 与广播给所有追踪者的 {@link ToughnessHitSoundPacket} 刻意分开：
 * 音效/粒子是"谁都看得见的世界事件"，而屏幕抖动 + 镜面炸碎是"出手那个人的手感反馈"，
 * 旁观者不该跟着抖屏。
 * <p>
 * {@code stun=true} 表示这一下把韧性打空、触发了定身 —— 客户端据此加大幅度。
 */
public class ToughnessFeedbackPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToughnessFeedbackPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "toughness_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToughnessFeedbackPacket> STREAM_CODEC =
            StreamCodec.ofMember(ToughnessFeedbackPacket::encode, ToughnessFeedbackPacket::new);

    private final int entityId;
    private final boolean stun;

    public ToughnessFeedbackPacket(int entityId, boolean stun) {
        this.entityId = entityId;
        this.stun = stun;
    }

    private ToughnessFeedbackPacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.stun = buf.readBoolean();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(stun);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int getEntityId() {
        return entityId;
    }

    /** 这一下是否触发了定身（韧性刚好被打空） */
    public boolean isStun() {
        return stun;
    }

    public static void handle(ToughnessFeedbackPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandlers.handleToughnessFeedback(packet));
    }
}
