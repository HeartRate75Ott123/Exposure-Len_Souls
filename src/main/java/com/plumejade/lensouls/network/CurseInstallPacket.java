package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.gui.FeatherSlotMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：从诅咒列表（§4.1）把某一款诅咒装进指定槽位。
 * <p>
 * 与旧的 {@link FeatherSlotPacket#install(int, int)}（按背包下标装）不同：列表**固定包含全部 7 款**，
 * 所以这里按 <b>诅咒 id</b> 传递——服务端解析成物品：背包里有就搬一个，没有就发放一个。
 */
public record CurseInstallPacket(int slot, String curseId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CurseInstallPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "curse_install"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CurseInstallPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CurseInstallPacket::slot,
                    ByteBufCodecs.STRING_UTF8, CurseInstallPacket::curseId,
                    CurseInstallPacket::new);

    @Override
    @NotNull
    public CustomPacketPayload.Type<CurseInstallPacket> type() {
        return TYPE;
    }

    public static void handle(CurseInstallPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null || player.level().isClientSide) return;
            try {
                if (!(player.containerMenu instanceof FeatherSlotMenu menu)) return;
                menu.onInstallCurse(packet.slot(), packet.curseId());
            } catch (Throwable t) {
                // 任何异常都不能冒泡到服务端主循环
                LenSouls.LOGGER.error("[Curse] 列表装入失败 slot={} id={}",
                        packet.slot(), packet.curseId(), t);
            }
        });
    }
}
