package com.plumejade.lensouls.client;

import com.plumejade.lensouls.boss.FreezeRejectParticlePacket;
import com.plumejade.lensouls.boss.ToughnessFeedbackPacket;
import com.plumejade.lensouls.boss.ToughnessHitSoundPacket;
import com.plumejade.lensouls.boss.ToughnessParticlePacket;
import com.plumejade.lensouls.client.phantom.SwarmPhantomClient;
import com.plumejade.lensouls.network.SwarmPhantomPacket;
import com.plumejade.lensouls.particle.ModParticleTypes;
import com.plumejade.lensouls.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * S2C 包客户端处理（音效/粒子）。
 * <p>
 * 与 Exposure 的 ClientPacketsHandler 同模式：S2C packet 类会被服务端加载（通道双端注册），
 * 其 handle 只能委托到这里——本类虽引用 Minecraft 等 {@code @OnlyIn(CLIENT)} 类，
 * 但自身无 {@code @OnlyIn} 注解，服务端加载 packet 类时不会触发本类加载；
 * 仅在客户端收到包执行 handle 时才加载本类。
 */
public class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    /**
     * 自家幻翼的时效（见 {@link SwarmPhantomClient}）：只存一份「还剩多少刻」，
     * 渐隐曲线由渲染层按时间本地推算——不逐 tick 同步。
     */
    public static void handleSwarmPhantom(SwarmPhantomPacket packet) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        SwarmPhantomClient.put(packet.getEntityId(), packet.getRemainingTicks(), level.getGameTime());
    }

    /**
     * 削韧命中的屏幕反馈（只发给出手的玩家）：屏幕抖动 + 镜面炸碎。
     * 走到这里时玩家一定在客户端上，不需要再判 entity——实体可能已因这一下进入定身。
     */
    public static void handleToughnessFeedback(ToughnessFeedbackPacket packet) {
        com.plumejade.lensouls.client.fx.ToughnessImpactScreenFx.trigger(packet.isStun());
    }

    /**
     * 滤镜成功施加：在被施加者身上播「对焦收束」。
     * <p>
     * 只靠 entityId 找实体——位置由 {@code WorldFxBursts} 每帧现取（平滑跟随），
     * 所以这里丢包/实体暂时不在视野里都不影响已开播的那一份。
     */
    public static void handleFilterAppliedFx(com.plumejade.lensouls.network.FilterAppliedFxPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (!(level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel)) return;
        Entity target = clientLevel.getEntity(packet.getEntityId());
        if (target == null) return;
        com.plumejade.lensouls.client.fx.WorldFxBursts.filterApplied(
                clientLevel, target, packet.getR(), packet.getG(), packet.getB());
    }

    /**
     * 最上层提示弹窗（等级不足 / 封器未反转 / 胶卷用光……）。
     * <p>
     * 走 {@code FeatherToast}：由 {@code GameRenderer.render} 里 {@code GuiGraphics.flush()} 之后绘制，
     * 压在所有 HUD 与取景器之上；同时清掉原版动作栏那份（这些提示以前走过动作栏，会重复）。
     */
    public static void handleHudHint(com.plumejade.lensouls.network.HudHintPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        com.plumejade.lensouls.LenSouls.LOGGER.info("[HudHint] 最上层提示: {}", packet.text().getString());
        com.plumejade.lensouls.client.feather.FeatherToast.show(packet.text());
        if (mc.gui != null) mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false);
    }

    public static void handleToughnessHitSound(ToughnessHitSoundPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        Entity entity = level.getEntity(packet.getEntityId());
        if (!(entity instanceof LivingEntity le) || !le.isAlive()) return;

        SoundEvent sound = packet.isFail() ? ModSounds.TOUGHNESS_FAIL.get() : ModSounds.TOUGHNESS_CHANGE.get();
        Random rng = new Random();
        float volume = 0.7f + rng.nextFloat() * 0.5f;
        float pitch = 0.8f + rng.nextFloat() * 0.4f;

        level.playLocalSound(
                le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(),
                sound,
                SoundSource.PLAYERS,
                volume, pitch,
                false
        );
    }

    /**
     * 要害打击没削到韧性（白霸体）：整屏故障叉号，压在最上层。
     * <p>
     * 按用户定稿："只用叠加叉号部分" —— 不压暗、不加文字、不带任何底层，只把一支巨大的故障 ×
     * 叠在取景框之上（见 {@link com.plumejade.lensouls.client.hud.VitalDenyOverlay}）。
     * 定身中 / 倍率被扣成 0 的情况服务端根本不发包，所以这里不需要再分支。
     */
    public static void handleVitalStrikeReject(com.plumejade.lensouls.network.VitalStrikeRejectPacket packet) {
        com.plumejade.lensouls.client.hud.VitalDenyOverlay.show();
    }

    /**
     * 要害打击命中：受击者身上「血线贯穿」。
     * <p>
     * 与既有削韧反馈（抖屏 / 通用削韧粒子）共存——这里只多画一层红色世界特效，不占屏幕通道。
     */
    public static void handleVitalStrikeFx(com.plumejade.lensouls.network.VitalStrikeFxPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (!(level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel)) return;
        Entity target = clientLevel.getEntity(packet.getEntityId());
        if (!(target instanceof LivingEntity le)) return;
        com.plumejade.lensouls.client.fx.WorldFxBursts.vitalStrike(clientLevel, le);
    }

    public static void handleToughnessParticle(ToughnessParticlePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        Entity entity = level.getEntity(packet.getEntityId());
        if (!(entity instanceof LivingEntity le) || !le.isAlive()) return;

        var player = mc.player;
        if (player == null) return;

        // 从实体中心指向玩家本地坐标
        Vec3 entityCenter = le.position().add(0, le.getBbHeight() * 0.5, 0);
        Vec3 toPlayer = player.position().add(0, player.getEyeHeight() * 0.5, 0)
                .subtract(entityCenter).normalize();

        if (packet.isBreak()) {
            // 破碎：十字架（5 条 120 度锥角，加速）+ 冲击波（1 个环形扩散）
            spawnConeParticles(level, entityCenter, toPlayer, 120.0,
                    5, ModParticleTypes.TOUGHNESS_BREAK.get(), 0.8, 1.2);
            // 韧性圆环扩散（固定时间实体中心，非锥形散射）
            level.addParticle(ModParticleTypes.TOUGHNESS_SHOCKWAVE.get(),
                    entityCenter.x, entityCenter.y, entityCenter.z,
                    0, 0, 0);
        } else {
            // 击中：平面四边形粒子（向玩家扩散）
            spawnBurstParticles(level, entityCenter,
                    10, ModParticleTypes.TOUGHNESS_HIT.get(), 0.3, 0.9);
        }
    }

    public static void handleFreezeRejectParticle(FreezeRejectParticlePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        Entity entity = level.getEntity(packet.getEntityId());
        if (!(entity instanceof LivingEntity le) || !le.isAlive()) return;

        var player = mc.player;
        if (player == null) return;

        Vec3 center = le.position().add(0, le.getBbHeight() * 0.5, 0);
        Vec3 toPlayer = player.position().add(0, player.getEyeHeight() * 0.5, 0)
                .subtract(center).normalize();

        // 平面四边形方式向玩家方向锥形散射（120 度锥角，5 条加速）
        spawnConeParticles(level, center, toPlayer, 120.0,
                5, ModParticleTypes.FREEZE_REJECT.get(), 0.15, 0.45);
    }

    /**
     * 锥角范围内朝目标方向散射粒子。
     *
     * @param level     客户端世界
     * @param origin    粒子原点（实体中心）
     * @param direction 朝向玩家方向（单位向量）
     * @param coneDeg   锥角（度）
     * @param count     粒子数量
     * @param type      粒子类型
     * @param minSpeed  最小速度
     * @param maxSpeed  最大速度
     */
    private static void spawnConeParticles(Level level, Vec3 origin, Vec3 direction,
                                           double coneDeg, int count, SimpleParticleType type,
                                           double minSpeed, double maxSpeed) {
        var random = level.random;
        double halfRad = Math.toRadians(coneDeg * 0.5);

        // 局部坐标系（direction = Z 轴）
        Vec3 zAxis = direction;
        Vec3 xAxis;
        if (Math.abs(zAxis.y) < 0.99) {
            xAxis = new Vec3(1, 0, 0).cross(zAxis).normalize();
        } else {
            xAxis = new Vec3(0, 0, 1).cross(zAxis).normalize();
        }
        Vec3 yAxis = zAxis.cross(xAxis).normalize();

        for (int i = 0; i < count; i++) {
            // 锥体球坐标均匀分布
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = random.nextDouble() * halfRad;

            double speed = minSpeed + random.nextDouble() * (maxSpeed - minSpeed);

            double sinPhi = Math.sin(phi);
            // 局部方向
            double lx = sinPhi * Math.cos(theta);
            double ly = sinPhi * Math.sin(theta);
            double lz = Math.cos(phi);

            // 转换到世界空间
            double vx = (lx * xAxis.x + ly * yAxis.x + lz * zAxis.x) * speed;
            double vy = (lx * xAxis.y + ly * yAxis.y + lz * zAxis.y) * speed;
            double vz = (lx * xAxis.z + ly * yAxis.z + lz * zAxis.z) * speed;

            level.addParticle(type, origin.x, origin.y, origin.z, vx, vy, vz);
        }
    }

    /**
     * 从实体中心向玩家方向散射粒子（全方向均匀分布）。
     *
     * @param level     客户端世界
     * @param origin    粒子原点（实体中心）
     * @param count     粒子数量
     * @param type      粒子类型
     * @param minSpeed  最小速度
     * @param maxSpeed  最大速度
     */
    private static void spawnBurstParticles(Level level, Vec3 origin,
                                            int count, SimpleParticleType type,
                                            double minSpeed, double maxSpeed) {
        var random = level.random;
        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;          // 水平全角度
            double phi = (random.nextDouble() - 0.5) * Math.PI * 0.6;  // -54°~+54°
            double speed = minSpeed + random.nextDouble() * (maxSpeed - minSpeed);

            double vx = Math.cos(phi) * Math.cos(theta) * speed;
            double vy = Math.sin(phi) * speed;
            double vz = Math.cos(phi) * Math.sin(theta) * speed;

            level.addParticle(type, origin.x, origin.y, origin.z, vx, vy, vz);
        }
    }
}