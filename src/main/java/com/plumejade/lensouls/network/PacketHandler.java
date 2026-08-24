package com.plumejade.lensouls.network;

import com.plumejade.lensouls.ability.network.AbilityOpenGuiPacket;
import com.plumejade.lensouls.ability.network.AbilitySelectPacket;
import com.plumejade.lensouls.ability.network.AbilitySyncPacket;
import com.plumejade.lensouls.ability.network.FreezeSyncPacket;
import com.plumejade.lensouls.ability.network.SpatialWarpActivatePacket;
import com.plumejade.lensouls.ability.network.TemporalRecallTriggerPacket;
import com.plumejade.lensouls.boss.FreezeRejectParticlePacket;
import com.plumejade.lensouls.boss.ToughnessHitSoundPacket;
import com.plumejade.lensouls.boss.ToughnessParticlePacket;
import com.plumejade.lensouls.boss.ToughnessSyncPacket;
import com.plumejade.lensouls.client.phantom.ClientPhantomHandler;
import com.plumejade.lensouls.entity.BossPhantomType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册中心。
 * <p>
 * 通过 {@link #register(IEventBus)} 绑定到 Mod 事件总线，
 * 避免使用已弃用的 {@code @EventBusSubscriber(bus = Bus.MOD)}。
 * <p>
 * 所有 payload 通道（含 S2C 的 {@code playToClient}）必须在此双端注册，
 * 否则专用服务器协议中缺失通道，客户端连接时协商失败。
 * S2C 的 handler 仅在客户端被调用，服务端注册只是声明通道。
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

        // ---- 照片挥击信号 C2S（空手/空挥触发 Boss 弹幕） ----
        registrar.playToServer(
                PhotoSwingPacket.TYPE,
                PhotoSwingPacket.STREAM_CODEC,
                PhotoSwingPacket::handle
        );

        // ---- 转换器镜魂选择菜单 C2S ----
        registrar.playToServer(
                ConverterMenuRequestPacket.TYPE,
                ConverterMenuRequestPacket.STREAM_CODEC,
                ConverterMenuRequestPacket::handle
        );
        registrar.playToServer(
                ConverterMenuActivatePacket.TYPE,
                ConverterMenuActivatePacket.STREAM_CODEC,
                ConverterMenuActivatePacket::handle
        );

        // ---- 能力系统 C2S 包 ----
        registrar.playToServer(
                AbilityOpenGuiPacket.TYPE,
                AbilityOpenGuiPacket.STREAM_CODEC,
                AbilityOpenGuiPacket::handle
        );

        registrar.playToServer(
                AbilitySelectPacket.TYPE,
                AbilitySelectPacket.STREAM_CODEC,
                AbilitySelectPacket::handle
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

        // ---- S2C 包（双端注册；handler 惰性加载，仅客户端执行）----

        // 虚影降临 S2C
        registrar.playToClient(
                PhantomStartPacket.TYPE,
                PhantomStartPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    BossPhantomType[] values = BossPhantomType.values();
                    if (packet.getBossTypeOrdinal() < 0 || packet.getBossTypeOrdinal() >= values.length) return;
                    BossPhantomType type = values[packet.getBossTypeOrdinal()];
                    ClientPhantomHandler.getInstance().startPhantom(
                            packet.getPlayerId(), type, packet.getLifetimeTicks(),
                            packet.getPhantomX(), packet.getPhantomY(), packet.getPhantomZ(),
                            packet.getPhantomYaw());
                    ClientPhantomHandler.addPhantomEntity(packet.getPhantomEntityId());
                })
        );

        // 虚影技能 S2C
        registrar.playToClient(
                PhantomSkillPacket.TYPE,
                PhantomSkillPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    BossPhantomType[] values = BossPhantomType.values();
                    if (packet.getBossTypeOrdinal() < 0 || packet.getBossTypeOrdinal() >= values.length) return;
                    BossPhantomType type = values[packet.getBossTypeOrdinal()];
                    ClientPhantomHandler.getInstance().playSkill(type);
                })
        );

        // 虚影消失 S2C
        registrar.playToClient(
                PhantomStopPacket.TYPE,
                PhantomStopPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    ClientPhantomHandler.getInstance().stopPhantom();
                })
        );

        // 虚影阶段切换 S2C
        registrar.playToClient(
                PhantomTickPacket.TYPE,
                PhantomTickPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    BossPhantomType[] values = BossPhantomType.values();
                    if (packet.getBossTypeOrdinal() < 0 || packet.getBossTypeOrdinal() >= values.length) return;
                    BossPhantomType type = values[packet.getBossTypeOrdinal()];
                    ClientPhantomHandler.getInstance().playPhase(type, packet.getPhase());
                })
        );

        // ---- 能力系统 S2C ----
        registrar.playToClient(
                AbilitySyncPacket.TYPE,
                AbilitySyncPacket.STREAM_CODEC,
                AbilitySyncPacket::handle
        );

        // ---- 冻结状态同步 S2C ----
        registrar.playToClient(
                FreezeSyncPacket.TYPE,
                FreezeSyncPacket.STREAM_CODEC,
                FreezeSyncPacket::handle
        );

        // ---- BOSS 韧性同步 S2C ----
        registrar.playToClient(
                ToughnessSyncPacket.TYPE,
                ToughnessSyncPacket.STREAM_CODEC,
                ToughnessSyncPacket::handle
        );

        // ---- BOSS 韧性削减音效 S2C ----
        registrar.playToClient(
                ToughnessHitSoundPacket.TYPE,
                ToughnessHitSoundPacket.STREAM_CODEC,
                ToughnessHitSoundPacket::handle
        );

        // ---- BOSS 韧性削减粒子 S2C ----
        registrar.playToClient(
                ToughnessParticlePacket.TYPE,
                ToughnessParticlePacket.STREAM_CODEC,
                ToughnessParticlePacket::handle
        );

        // ---- 时间定格拒绝粒子 S2C ----
        registrar.playToClient(
                FreezeRejectParticlePacket.TYPE,
                FreezeRejectParticlePacket.STREAM_CODEC,
                FreezeRejectParticlePacket::handle
        );

        // ---- 元素弱点螺旋粒子 S2C ----
        registrar.playToClient(
                ElementSpiralPacket.TYPE,
                ElementSpiralPacket.STREAM_CODEC,
                ElementSpiralPacket::handle
        );

        // ---- 扭曲值同步 S2C（左侧 bar） ----
        registrar.playToClient(
                TwistSyncPacket.TYPE,
                TwistSyncPacket.STREAM_CODEC,
                TwistSyncPacket::handle
        );
    }
}