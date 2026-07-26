package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：激活空间扭曲 — 消耗一张带坐标的照片，以照片拍摄位置为圆心展开范围圈。
 */
public class SpatialWarpActivatePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpatialWarpActivatePacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "spatial_warp_activate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpatialWarpActivatePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {},
                    buf -> new SpatialWarpActivatePacket()
            );

    @Override
    @NotNull
    public CustomPacketPayload.Type<SpatialWarpActivatePacket> type() {
        return TYPE;
    }

    public static void handle(SpatialWarpActivatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;

            AbilityManager am = AbilityManager.getInstance();

            // 已激活 → 关闭
            if (am.isSpatialWarpActive(sp)) {
                am.deactivateSpatialWarp(sp);
                return;
            }

            // 未激活 → 从手持照片读取坐标激活，不消耗照片
            ItemStack held = sp.getMainHandItem();
            CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!tag.contains("lensouls:spatial_warp_pos")) {
                // 主手没有则扫背包找第一张
                for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                    ItemStack stack = sp.getInventory().getItem(i);
                    CompoundTag t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    if (t.contains("lensouls:spatial_warp_pos")) {
                        tag = t;
                        break;
                    }
                }
                if (!tag.contains("lensouls:spatial_warp_pos")) return;
            }

            CompoundTag posTag = tag.getCompound("lensouls:spatial_warp_pos");
            Vec3 center = new Vec3(posTag.getDouble("x"), posTag.getDouble("y"), posTag.getDouble("z"));
            String dimId = sp.level().dimension().location().toString();

            am.activateSpatialWarp(sp, center, dimId);
        });
    }
}
