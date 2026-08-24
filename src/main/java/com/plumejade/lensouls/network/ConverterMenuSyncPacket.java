package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.gui.SoulSelectOverlay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 转换器镜魂选择菜单数据（S2C）。
 * <p>
 * 服务端读取转换器容器真实内容（3×3 镜魂 + 冷却），返回给客户端 Overlay 菜单显示。
 */
public class ConverterMenuSyncPacket implements CustomPacketPayload {

    public record SoulEntry(int slot, ItemStack stack, int remainingTicks) {}

    public static final Type<ConverterMenuSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "converter_menu_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoulEntry> ENTRY_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SoulEntry::slot,
                    ItemStack.OPTIONAL_STREAM_CODEC, SoulEntry::stack,
                    ByteBufCodecs.VAR_INT, SoulEntry::remainingTicks,
                    SoulEntry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, ConverterMenuSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ENTRY_CODEC.apply(ByteBufCodecs.list()), p -> p.entries,
                    ConverterMenuSyncPacket::new);

    private final List<SoulEntry> entries;

    public ConverterMenuSyncPacket(List<SoulEntry> entries) {
        this.entries = entries;
    }

    @Override
    @NotNull
    public Type<ConverterMenuSyncPacket> type() {
        return TYPE;
    }

    /** 客户端处理：更新镜魂选择菜单数据 */
    public static void handle(ConverterMenuSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> SoulSelectOverlay.setSouls(packet.entries));
    }
}
