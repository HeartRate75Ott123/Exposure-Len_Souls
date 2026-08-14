package com.plumejade.lensouls.network;

import com.plumejade.lensouls.ability.network.AbilityOpenGuiPacket;
import com.plumejade.lensouls.ability.network.SpatialWarpActivatePacket;
import com.plumejade.lensouls.ability.network.TemporalRecallTriggerPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册中心。
 * <p>
 * 通过 {@link #register(IEventBus)} 绑定到 Mod 事件总线，
 * 避免使用已弃用的 {@code @EventBusSubscriber(bus = Bus.MOD)}。
 */
public class PacketHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(PacketHandler::onRegisterPayloadHandlers);
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(
                ConverterTriggerPacket.TYPE,
                ConverterTriggerPacket.STREAM_CODEC,
                ConverterTriggerPacket::handle
        );

        registrar.playToServer(
                PhotoOpenPacket.TYPE,
                PhotoOpenPacket.STREAM_CODEC,
                PhotoOpenPacket::handle
        );

        registrar.playToServer(
                DimensionalGunCyclePacket.TYPE,
                DimensionalGunCyclePacket.STREAM_CODEC,
                DimensionalGunCyclePacket::handle
        );

        registrar.playToServer(
                DimensionalGunTogglePacket.TYPE,
                DimensionalGunTogglePacket.STREAM_CODEC,
                DimensionalGunTogglePacket::handle
        );

        // ---- 能力系统 C2S 包 ----
        registrar.playToServer(
                AbilityOpenGuiPacket.TYPE,
                AbilityOpenGuiPacket.STREAM_CODEC,
                AbilityOpenGuiPacket::handle
        );

        registrar.playToServer(
                SpatialWarpActivatePacket.TYPE,
                SpatialWarpActivatePacket.STREAM_CODEC,
                SpatialWarpActivatePacket::handle
        );

        registrar.playToServer(
                TemporalRecallTriggerPacket.TYPE,
                TemporalRecallTriggerPacket.STREAM_CODEC,
                TemporalRecallTriggerPacket::handle
        );
    }
}
