package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：**最上层提示弹窗**（{@code FeatherToast}）。
 * <p>
 * 为什么不复用原版动作栏：动作栏那一条是在 {@code Gui.render} 里画的，相机取景器开着时
 * Exposure 会把整个 {@code Gui.render} cancel 掉，于是我们塞进动作栏的提示根本不出现；
 * 就算出现，它也只会待在屏幕最底部，被取景框压着（"被压在底下"就是这个观感）。
 * <p>
 * 所以凡是"拍照那一刻的关键反馈"（等级不足、封器未反转、胶卷用光……）都走这条专用通道：
 * 服务端直发本人 → 客户端 {@code FeatherToast}，在 {@code GameRenderer.render} 的
 * {@code GuiGraphics.flush()} 之后绘制，天然压在所有 HUD / 取景器之上。
 */
public class HudHintPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HudHintPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "hud_hint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudHintPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.STREAM_CODEC, HudHintPacket::text,
                    HudHintPacket::new);

    private final Component text;

    public HudHintPacket(Component text) {
        this.text = text;
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public Component text() {
        return text;
    }

    /** 服务端侧的唯一发送口：只发给这一位玩家 */
    public static void send(ServerPlayer player, Component text) {
        if (player == null || text == null) return;
        PacketDistributor.sendToPlayer(player, new HudHintPacket(text));
    }

    public static void handle(HudHintPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                com.plumejade.lensouls.client.ClientPacketHandlers.handleHudHint(packet);
            } catch (Throwable ignored) {
            }
        });
    }
}
