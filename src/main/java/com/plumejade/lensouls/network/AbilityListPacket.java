package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 列出已获取能力（C2S）。
 * <p>
 * 客户端按下 ; 键发送此包，服务端遍历已解锁能力，
 * 将列表（含当前启用标记）发送到玩家消息栏。
 */
public class AbilityListPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AbilityListPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "ability_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityListPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {},
                    buf -> new AbilityListPacket()
            );

    @Override
    @NotNull
    public CustomPacketPayload.Type<AbilityListPacket> type() {
        return TYPE;
    }

    /** 服务端处理器 */
    public static void handle(AbilityListPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer sp)) return;

            AbilityManager manager = AbilityManager.getInstance();
            AbilityType enabled = manager.getEnabled(sp);

            sp.sendSystemMessage(
                    Component.translatable("message.lensouls.ability_list.header"));

            boolean any = false;
            for (AbilityType type : AbilityType.values()) {
                if (!manager.isUnlocked(sp, type)) continue;
                any = true;
                Component name = Component.translatable("ability.lensouls." + type.getId() + ".name");
                if (type == enabled) {
                    sp.sendSystemMessage(
                            Component.translatable("message.lensouls.ability_list.enabled", name));
                } else {
                    sp.sendSystemMessage(
                            Component.translatable("message.lensouls.ability_list.item", name));
                }
            }
            if (!any) {
                sp.sendSystemMessage(
                        Component.translatable("message.lensouls.ability_list.empty"));
            }
        });
    }
}
