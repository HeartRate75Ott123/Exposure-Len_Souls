package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.gui.FeatherSlotMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 请求打开羽毛装配界面（C2S，空载荷）。
 * <p>
 * l2tabs 的分页图标点击是<b>纯客户端</b>事件，而菜单必须由服务端打开
 * （{@code player.openMenu}），客户端才会收到 {@code ClientboundOpenScreenPacket} 并显示界面——
 * 这也是「槽内容 + 锁定位」能正确同步的前提。
 */
public record FeatherOpenPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FeatherOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "feather_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FeatherOpenPacket> STREAM_CODEC =
            StreamCodec.unit(new FeatherOpenPacket());

    @Override
    @NotNull
    public CustomPacketPayload.Type<FeatherOpenPacket> type() {
        return TYPE;
    }

    public static void handle(FeatherOpenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || player.level().isClientSide) return;
            try {
                player.openMenu(new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("gui.lensouls.feather.title");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                        return new FeatherSlotMenu(id, inventory);
                    }
                });
            } catch (Throwable t) {
                LenSouls.LOGGER.error("[Feather] 打开羽毛装配界面失败", t);
            }
        });
    }
}
