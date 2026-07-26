package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.item.DimensionalGunItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public class DimensionalGunCyclePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DimensionalGunCyclePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "dimensional_gun_cycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DimensionalGunCyclePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new DimensionalGunCyclePacket());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DimensionalGunCyclePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer sp)) return;
            ItemStack stack = sp.getMainHandItem();
            if (stack.getItem() instanceof DimensionalGunItem gun) {
                gun.cycleAmmoType(stack, sp);
            }
        });
    }
}
