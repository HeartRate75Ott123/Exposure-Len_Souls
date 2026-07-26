package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.gui.PhotoGuiMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 打开照片 GUI 请求包（C2S）。
 */
public class PhotoOpenPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PhotoOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "photo_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhotoOpenPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {},
                    buf -> new PhotoOpenPacket()
            );

    @Override
    @NotNull
    public CustomPacketPayload.Type<PhotoOpenPacket> type() {
        return TYPE;
    }

    public static void handle(PhotoOpenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;

            // 检查玩家是否持有摄魂附魔武器
            if (PhotoGuiMenu.findWeapon(player).isEmpty()) return;

            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new PhotoGuiMenu(id, inv),
                    Component.translatable("container.lensouls.photo_gui")
            ));
        });
    }
}
