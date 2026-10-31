package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：**特殊滤镜施加成功 → 屏幕中央弹出该滤镜的物品图标**。
 * <p>
 * 走原版不死图腾那套物品激活动画（{@code GameRenderer.displayItemActivation}：40 刻的
 * 缩放进场 + 悬浮 + 淡出，贴图就是物品自己的模型）。图腾是「图腾救了你」的反馈，
 * 这里是「这个滤镜生效了」的反馈。
 * <p>
 * 只发一份 {@link ItemStack}（数量恒为 1），物品取相机上实际装着的那片滤镜，
 * 所以滤镜来自哪个模组、换成哪一款都自动对——不做「效果 → 物品 id」的硬编码映射。
 * <p>
 * 收件人由服务端决定（见 {@code FilterPhotoHandler}）：<b>拍照的玩家</b>必发
 * （蜘蛛滤镜打的是怪物，只有施加者屏幕能弹）；被上了滤镜的玩家如果不是施加者本人，也发一份。
 * 旁观者不发——屏幕中央弹窗是个人反馈，不该满场闪。
 */
public class FilterItemPopupPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FilterItemPopupPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "filter_item_popup"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterItemPopupPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.STREAM_CODEC, FilterItemPopupPacket::icon,
                    FilterItemPopupPacket::new);

    private final ItemStack icon;

    public FilterItemPopupPacket(ItemStack icon) {
        this.icon = icon;
    }

    /** 服务端唯一发送口：只给这一位玩家弹一次 */
    public static void send(ServerPlayer viewer, ItemStack icon) {
        if (viewer == null || icon == null || icon.isEmpty()) return;
        // 数量归一：照片堆叠/耐久之类的组件都不该带进动画
        ItemStack one = icon.copyWithCount(1);
        // 取证行（进 latest.log）：测完直接 grep "[FilterFx]" 就能确认弹窗有没有发出去、发的是哪款滤镜
        LenSouls.LOGGER.info("[FilterFx] popup -> viewer={} item={}",
                viewer.getGameProfile().getName(),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(one.getItem()));
        PacketDistributor.sendToPlayer(viewer, new FilterItemPopupPacket(one));
    }

    public ItemStack icon() {
        return icon;
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FilterItemPopupPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                com.plumejade.lensouls.client.ClientPacketHandlers.handleFilterItemPopup(packet);
            } catch (Throwable ignored) {
            }
        });
    }
}
