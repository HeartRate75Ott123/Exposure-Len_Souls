package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityBehavior;
import com.plumejade.lensouls.ability.AbilityManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
 * C2S：开/关空间扭曲 — 以照片记录的拍摄位置（坐标 + 维度）为圆心展开范围圈，不消耗照片；
 * 照片维度与当前维度不一致时拒绝并提示。
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

            // 未激活 → 从手持照片读取坐标 + 维度激活，不消耗照片
            ItemStack held = sp.getMainHandItem();
            CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!tag.contains(AbilityBehavior.TAG_WARP_POS)) {
                // 主手没有则扫背包找第一张
                for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                    ItemStack stack = sp.getInventory().getItem(i);
                    CompoundTag t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    if (t.contains(AbilityBehavior.TAG_WARP_POS)) {
                        tag = t;
                        break;
                    }
                }
                if (!tag.contains(AbilityBehavior.TAG_WARP_POS)) return;
            }

            CompoundTag posTag = tag.getCompound(AbilityBehavior.TAG_WARP_POS);
            Vec3 center = new Vec3(posTag.getDouble("x"), posTag.getDouble("y"), posTag.getDouble("z"));
            String currentDim = sp.level().dimension().location().toString();
            // 旧照片（没有维度键）按当前维度兜底，保持旧存档可用
            String photoDim = tag.contains(AbilityBehavior.TAG_WARP_DIM)
                    ? tag.getString(AbilityBehavior.TAG_WARP_DIM) : currentDim;
            if (!photoDim.equals(currentDim)) {
                sp.displayClientMessage(Component.translatable(
                                "ability.lensouls.spatial_warp.wrong_dimension",
                                com.plumejade.lensouls.ability.util.DimensionNames.of(photoDim))
                        .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                return;
            }

            am.activateSpatialWarp(sp, center, photoDim);
        });
    }
}
