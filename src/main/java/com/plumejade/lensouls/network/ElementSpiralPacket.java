package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.client.particle.ClientElementSpiralHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：元素弱点增伤螺旋粒子触发包。
 * <p>
 * 服务端 {@link com.plumejade.lensouls.damage.DamageHandler} / {@link com.plumejade.lensouls.damage.PhotoDamageHandler} 发出，
 * 客户端在受击实体位置启动 25 tick 圆锥螺旋粒子上升动画。
 */
public class ElementSpiralPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ElementSpiralPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "element_spiral"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ElementSpiralPacket> STREAM_CODEC =
            StreamCodec.ofMember(ElementSpiralPacket::encode, ElementSpiralPacket::new);

    private final int entityId;
    private final int elementOrdinal;
    private final boolean weaknessLens;   // true → 弱点透镜色 #2d495c，忽略 elementOrdinal

    public ElementSpiralPacket(int entityId, int elementOrdinal) {
        this(entityId, elementOrdinal, false);
    }

    public ElementSpiralPacket(int entityId, int elementOrdinal, boolean weaknessLens) {
        this.entityId = entityId;
        this.elementOrdinal = elementOrdinal;
        this.weaknessLens = weaknessLens;
    }

    private ElementSpiralPacket(RegistryFriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.elementOrdinal = buf.readVarInt();
        this.weaknessLens = buf.readBoolean();
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeVarInt(elementOrdinal);
        buf.writeBoolean(weaknessLens);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ElementSpiralPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientElementSpiralHandler.getInstance().startSpiral(packet.entityId, packet.elementOrdinal, packet.weaknessLens);
        });
    }
}
