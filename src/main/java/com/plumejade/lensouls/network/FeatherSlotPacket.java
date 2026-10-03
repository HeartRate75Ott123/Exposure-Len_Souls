package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.gui.FeatherSlotMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 羽毛槽操作 C2S：装入（含更换）/ 卸下。
 * <p>
 * 与 {@code ReinforceSelectPacket} 同款写法：客户端只负责"点了哪个槽、哪一格背包"，
 * 真正的校验与结算全在 {@link FeatherSlotMenu} 的服务端方法里
 * （生存锁定、创造权限、物品是否在支持列表内）。
 */
public record FeatherSlotPacket(int action, int slot, int inventoryIndex) implements CustomPacketPayload {

    public static final int ACTION_INSTALL = 0;
    public static final int ACTION_UNINSTALL = 1;

    public static final CustomPacketPayload.Type<FeatherSlotPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "feather_slot_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FeatherSlotPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.action());
                        buf.writeVarInt(packet.slot());
                        buf.writeVarInt(packet.inventoryIndex());
                    },
                    buf -> new FeatherSlotPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    public static FeatherSlotPacket install(int slot, int inventoryIndex) {
        return new FeatherSlotPacket(ACTION_INSTALL, slot, inventoryIndex);
    }

    public static FeatherSlotPacket uninstall(int slot) {
        return new FeatherSlotPacket(ACTION_UNINSTALL, slot, -1);
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<FeatherSlotPacket> type() {
        return TYPE;
    }

    public static void handle(FeatherSlotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || player.level().isClientSide) return;
            try {
                if (!(player.containerMenu instanceof FeatherSlotMenu menu)) return;
                if (packet.action() == ACTION_INSTALL) {
                    menu.onInstall(packet.slot(), packet.inventoryIndex());
                } else if (packet.action() == ACTION_UNINSTALL) {
                    menu.onUninstall(packet.slot());
                }
            } catch (Throwable t) {
                // 任何异常都不能冒泡到服务端主循环（那会直接踢掉玩家）
                LenSouls.LOGGER.error("[Feather] 羽毛槽操作失败 action={} slot={}",
                        packet.action(), packet.slot(), t);
            }
        });
    }
}
