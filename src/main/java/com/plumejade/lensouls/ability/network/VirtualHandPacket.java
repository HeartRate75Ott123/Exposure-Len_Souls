package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：告诉客户端「某玩家此刻看起来手里拿着这台相机」（空栈 = 取消）。
 * <p>
 * 纯显示态：服务端不动任何物品，客户端只在渲染读手持物时替换。
 */
public record VirtualHandPacket(int entityId, ItemStack stack) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VirtualHandPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "virtual_hand"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VirtualHandPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, VirtualHandPacket::entityId,
                    ItemStack.OPTIONAL_STREAM_CODEC, VirtualHandPacket::stack,
                    VirtualHandPacket::new
            );

    @Override
    @NotNull
    public CustomPacketPayload.Type<VirtualHandPacket> type() {
        return TYPE;
    }

    public static void handle(VirtualHandPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                com.plumejade.lensouls.client.VirtualHandCache.set(packet.entityId(), packet.stack()));
    }
}
