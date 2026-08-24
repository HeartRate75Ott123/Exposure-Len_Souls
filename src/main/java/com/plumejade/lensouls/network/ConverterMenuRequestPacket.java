package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.gui.ModMenus;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 请求转换器内容（C2S）。
 * <p>
 * 客户端长按激活键时发送，服务端读取转换器容器真实内容（3×3 镜魂 + 冷却），
 * 通过 {@link ConverterMenuSyncPacket} 返回。
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

    /** 服务端处理：读转换器内容 → S2C 返回 */
    public static void handle(ConverterMenuRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ItemStack converter = ModMenus.findConverter(player);
            if (converter.isEmpty()) return;

            CustomData customData = converter.get(DataComponents.CUSTOM_DATA);
            if (customData == null) return;
            CompoundTag tag = customData.copyTag();
            if (!tag.contains("ConverterItems", Tag.TAG_LIST)) return;

            ListTag itemsList = tag.getList("ConverterItems", Tag.TAG_COMPOUND);
            CompoundTag slotUuids = tag.getCompound("SoulItemIds");
            CompoundTag cooldowns = tag.getCompound("SoulCooldowns");
            var access = player.registryAccess();
            long now = player.level().getGameTime();

            List<ConverterMenuSyncPacket.SoulEntry> entries = new ArrayList<>();
            for (int i = 0; i < itemsList.size(); i++) {
                CompoundTag slotTag = itemsList.getCompound(i);
                int slot = slotTag.getByte("Slot") & 0xFF;
                ItemStack stack = ItemStack.parseOptional(access, slotTag);
                if (stack.isEmpty()) continue;

                String itemUuid = slotUuids.getString("slot_" + slot);
                int remaining = 0;
                if (!itemUuid.isEmpty() && cooldowns.contains(itemUuid, Tag.TAG_COMPOUND)) {
                    CompoundTag cd = cooldowns.getCompound(itemUuid);
                    long end = cd.getLong("end");
                    if (end > now) remaining = (int) (end - now);
                }
                entries.add(new ConverterMenuSyncPacket.SoulEntry(slot, stack, remaining));
            }

            PacketDistributor.sendToPlayer(player, new ConverterMenuSyncPacket(entries));
        });
    }
}
