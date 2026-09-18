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
 * 强化界面「用某材料强化一次」请求（C2S）。
 * <p>
 * 客户端只上报材料 ID，服务端负责：校验选中物品、校验重复、从三容器消耗 1 个材料、
 * 写入属性修饰符与已强化列表。客户端收到的只是同步结果（槽位与数量）。
 */
public record ReinforceApplyPacket(ResourceLocation materialId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ReinforceApplyPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "reinforce_apply"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReinforceApplyPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeResourceLocation(packet.materialId()),
                    buf -> new ReinforceApplyPacket(buf.readResourceLocation()));

    @Override
    @NotNull
    public CustomPacketPayload.Type<ReinforceApplyPacket> type() {
        return TYPE;
    }

    public static void handle(ReinforceApplyPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || player.level().isClientSide) return;
            // 兜底：本处理器里的任何异常都不能冒泡到服务端主循环（那会直接踢掉玩家）
            try {
                if (player.containerMenu instanceof ReinforceMenu menu) {
                    menu.onApplyRequest(packet.materialId());
                }
            } catch (Throwable t) {
                com.plumejade.lensouls.LenSouls.LOGGER.error("[Reinforce] 强化失败 material={}", packet.materialId(), t);
            }
        });
    }
}
