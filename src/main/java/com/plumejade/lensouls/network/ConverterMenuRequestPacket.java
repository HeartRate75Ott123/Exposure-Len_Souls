package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.gui.ConverterSelectMenu;
import com.plumejade.lensouls.gui.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 请求打开精准选择容器（C2S）。
 * <p>
 * 客户端长按激活键时发送，服务端打开 {@link ConverterSelectMenu} 真实容器——
 * 容器内容由原版容器同步机制传回，与右键打开的转换器 GUI 完全一致。
 */
public class ConverterMenuRequestPacket implements CustomPacketPayload {

    public static final Type<ConverterMenuRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "converter_menu_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConverterMenuRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new ConverterMenuRequestPacket());

    @Override
    @NotNull
    public Type<ConverterMenuRequestPacket> type() {
        return TYPE;
    }

    /** 服务端处理：打开精准选择容器 */
    public static void handle(ConverterMenuRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack converter = ModMenus.findConverter(player);
            if (converter.isEmpty()) return;

            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new ConverterSelectMenu(id, inv, converter),
                    Component.translatable("container.lensouls.converter")));
        });
    }
}
