package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityOrderData;
import com.plumejade.lensouls.ability.AbilityType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S：上报能力展示顺序（菜单里拖拽排序后发送）。
 * <p>
 * 服务端只做「归一化 + 落盘 + 回同步」，不校验解锁状态——
 * 顺序是纯排版偏好，未解锁的能力也参与排序（见 {@link AbilityOrderData}）。
 */
public class AbilityOrderSetPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AbilityOrderSetPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "ability_order_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityOrderSetPacket> STREAM_CODEC =
            StreamCodec.ofMember(AbilityOrderSetPacket::encode, AbilityOrderSetPacket::new);

    private final int[] order;

    public AbilityOrderSetPacket(int[] order) {
        this.order = order;
    }

    private AbilityOrderSetPacket(RegistryFriendlyByteBuf buf) {
        int len = Math.max(0, Math.min(buf.readVarInt(), AbilityType.values().length * 2));
        this.order = new int[len];
        for (int i = 0; i < len; i++) {
            this.order[i] = buf.readVarInt();
        }
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(order.length);
        for (int ordinal : order) buf.writeVarInt(ordinal);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<AbilityOrderSetPacket> type() {
        return TYPE;
    }

    public static void handle(AbilityOrderSetPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            AbilityType[] values = AbilityType.values();
            List<AbilityType> list = new ArrayList<>();
            for (int ordinal : packet.order) {
                if (ordinal >= 0 && ordinal < values.length) list.add(values[ordinal]);
            }
            AbilityOrderData.get(sp.getServer()).setOrder(list);
            AbilityManager.getInstance().syncToClient(sp);
        });
    }
}
