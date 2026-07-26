package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.util.FreezeTracker;
import com.plumejade.lensouls.boss.BossToughnessData;
import com.plumejade.lensouls.boss.BossToughnessManager;
import com.plumejade.lensouls.boss.FreezeRejectParticlePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 时间定格快门检测处理器。
 * <p>
 * 通过反射监听 Exposure 的 FrameAddedEvent（按下快门时同步触发），
 * 避免编译期依赖。
 * <p>
 * 在 {@link com.plumejade.lensouls.LenSouls} 构造器中调用 {@link #register()}。
 */
public class TimeFreezeHandler {

    private static boolean registered = false;

    /** 注册 FrameAddedEvent 监听（反射） */
    public static void register() {
        if (registered) return;
        registered = true;

        try {
            Class<?> eventClass = Class.forName("io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent");
            Method getCameraHolderEntity = eventClass.getMethod("getCameraHolderEntity");

            Method addListener = NeoForge.EVENT_BUS.getClass()
                    .getMethod("addListener", EventPriority.class, boolean.class, Class.class, Consumer.class);

            addListener.invoke(NeoForge.EVENT_BUS, EventPriority.NORMAL, false, eventClass,
                    (Consumer<Object>) event -> {
                        try {
                            Entity entity = (Entity) getCameraHolderEntity.invoke(event);
                            if (!(entity instanceof ServerPlayer player)) return;

                            if (AbilityManager.getInstance().getEnabled(player) != AbilityType.TIME_STOP) return;
                            if (!hasSoulPhotography(player)) return;

                            triggerTimeFreeze(player);
                        } catch (Exception ex) {
                            LenSouls.LOGGER.error("[TimeFreezeHandler] error handling FrameAddedEvent", ex);
                        }
                    });

        } catch (Exception e) {
        }
    }

    // ========== 以下从 PhotoInjectionHandler 复制的工具方法 ==========

    private static boolean hasSoulPhotography(ServerPlayer player) {
        var enchReg = player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var soulPhoto = enchReg.get(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.ENCHANTMENT,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "soul_photography")));
        if (soulPhoto.isEmpty()) return false;
        var ench = soulPhoto.get();
        return player.getMainHandItem().getEnchantmentLevel(ench) > 0
                || player.getOffhandItem().getEnchantmentLevel(ench) > 0;
    }

    private static void triggerTimeFreeze(ServerPlayer player) {
        FreezeTracker tracker = FreezeTracker.getInstance();
        if (tracker.isPlayerSource(player)) {
            // 检查是否还有实际被冻结的活着的实体
            if (tracker.hasLiveFrozenEntities(player)) {
                showFloatingText(player,
                        net.minecraft.network.chat.Component.translatable("message.lensouls.freeze_already_active"));
                return;
            }
            // 冻结的实体都死了/没了 → 清理残留，允许重新定格
            tracker.forceUnfreeze(player);
        }

        Set<Entity> targets = getEntitiesInFrustum(player);
        targets.remove(player);

        // 只保留 Mob 类实体（排除经验球、掉落物、展示框等非生物）
        targets.removeIf(target -> !(target instanceof net.minecraft.world.entity.Mob));

        // BOSS 韧性免疫/概率检查，被拒绝的 BOSS 弹天青色粒子
        BossToughnessManager toughnessMgr = BossToughnessManager.getInstance();
        Iterator<Entity> it = targets.iterator();
        while (it.hasNext()) {
            Entity target = it.next();
            if (!(target instanceof LivingEntity living)) continue;
            if (!toughnessMgr.has(living)) continue;
            BossToughnessData data = toughnessMgr.get(living);
            if (data == null) continue;
            // 破韧期间：完全免疫 → 弹粒子
            if (data.isBroken()) {
                it.remove();
                PacketDistributor.sendToPlayersTrackingEntity(target,
                        new FreezeRejectParticlePacket(target.getId()));
                continue;
            }
            // 有韧性未破韧：20% 概率成功（80% 概率移除 → 弹粒子）
            if (living.getRandom().nextFloat() >= 0.2f) {
                it.remove();
                // 发送天青色粒子到追踪此实体的所有玩家
                PacketDistributor.sendToPlayersTrackingEntity(target,
                        new FreezeRejectParticlePacket(target.getId()));
            }
        }

        // 没有可冻结的实体 → 不进入冷却（BOSS 被拒已有粒子反馈）
        if (targets.isEmpty()) return;

        tracker.freeze(player, targets, 100);

    }

    // ========== 浮空文字提示 ==========

    /**
     * 在玩家头前方生成浮空文字（AreaEffectCloud + CustomName，世界空间渲染）。
     * 不会被任何 GUI 遮挡，3 秒后自动消失。
     */
    private static void showFloatingText(ServerPlayer player, net.minecraft.network.chat.Component text) {
        var level = player.serverLevel();

        // 放在玩家视线前方 3 格
        var look = player.getLookAngle();
        var pos = player.getEyePosition().add(look.scale(3.0));

        var cloud = new net.minecraft.world.entity.AreaEffectCloud(level, pos.x, pos.y, pos.z);
        cloud.setCustomName(text.copy().withStyle(
                s -> s.withBold(true).withColor(net.minecraft.ChatFormatting.RED)));
        cloud.setCustomNameVisible(true);
        cloud.setRadius(0f);
        cloud.setDuration(60);   // 3 秒
        cloud.setWaitTime(0);
        cloud.setNoGravity(true);
        // 最小化潜在碰撞影响
        cloud.setInvulnerable(true);

        level.addFreshEntity(cloud);
    }

    private static Set<Entity> getEntitiesInFrustum(ServerPlayer player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        double maxDist = 24.0;
        double fovCos = Math.cos(Math.toRadians(30.0));

        Set<Entity> result = new HashSet<>();
        AABB searchBox = player.getBoundingBox().inflate(maxDist);

        for (Entity entity : player.level().getEntities(player, searchBox)) {
            if (entity == player || !entity.isAlive()) continue;
            if (entity instanceof ServerPlayer) continue;
            Vec3 toTarget = entity.position().subtract(eyePos).normalize();
            if (lookVec.dot(toTarget) < fovCos) continue;
            if (entity.distanceToSqr(player) > maxDist * maxDist) continue;
            result.add(entity);
        }
        return result;
    }
}
