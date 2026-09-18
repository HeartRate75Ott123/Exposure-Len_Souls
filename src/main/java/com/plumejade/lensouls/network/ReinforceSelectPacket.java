package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.gui.ReinforceMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 强化界面「选中物品栏槽位」请求（C2S）。
 * <p>
 * 选择界面展示的就是玩家物品栏，客户端把点中的槽位下标上报，
 * 服务端校验（非空、非黑名单）后写入菜单的选中值并原版同步回来。
 * 传 -1 表示取消选中。
 */
public record ReinforceSelectPacket(int slot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ReinforceSelectPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "reinforce_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReinforceSelectPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeVarInt(packet.slot()),
                    buf -> new ReinforceSelectPacket(buf.readVarInt()));

    @Override
    @NotNull
    public CustomPacketPayload.Type<ReinforceSelectPacket> type() {
        return TYPE;
    }

    public static void handle(ReinforceSelectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || player.level().isClientSide) return;
            // 兜底：本处理器里的任何异常都不能冒泡到服务端主循环（那会直接踢掉玩家）
            try {
                if (player.containerMenu instanceof ReinforceMenu menu) {
                    menu.onSelectRequest(packet.slot());
                }
            } catch (Throwable t) {
                com.plumejade.lensouls.LenSouls.LOGGER.error("[Reinforce] 选择槽位失败 slot={}", packet.slot(), t);
            }
        });
    }
}
