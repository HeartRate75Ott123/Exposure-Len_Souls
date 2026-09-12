package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.gui.ModMenus;
import com.plumejade.lensouls.item.ConverterItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 转换器触发模式切换包（C2S，手持转换器左键空气时由客户端发送）。
 * <p>
 * 模式是<b>物品组件</b>（{@link ModDataComponents#CONVERTER_MODE}）上的数据，
 * 因此由服务端作权威写入方：客户端只上报目标值，服务端写到转换器上后再同步回来，
 * 避免客户端本地改组件被下一次背包同步覆盖。
 * <p>
 * 传的是<b>目标值</b>而非「翻转」指令，重复到达也不会来回跳。
 */
public record ConverterModeSwitchPacket(int mode) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ConverterModeSwitchPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "converter_mode_switch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConverterModeSwitchPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeVarInt(packet.mode()),
                    buf -> new ConverterModeSwitchPacket(buf.readVarInt()));

    @Override
    @NotNull
    public CustomPacketPayload.Type<ConverterModeSwitchPacket> type() {
        return TYPE;
    }

    /** 服务端处理器：把模式写到玩家身上的转换器物品组件里 */
    public static void handle(ConverterModeSwitchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null) return;
            if (player.level().isClientSide) return;

            // 手持优先（主手 → 副手），否则退到背包内第一个转换器
            ItemStack converter = player.getMainHandItem();
            if (!(converter.getItem() instanceof ConverterItem)) {
                converter = player.getOffhandItem();
            }
            if (!(converter.getItem() instanceof ConverterItem)) {
                converter = ModMenus.findConverter(player);
            }
            if (converter.isEmpty() || !(converter.getItem() instanceof ConverterItem)) return;

            ModDataComponents.setConverterMode(converter, packet.mode());
        });
    }
}
