package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.util.FreezeTracker;
import com.plumejade.lensouls.boss.BossToughnessData;
import com.plumejade.lensouls.boss.BossToughnessManager;
import com.plumejade.lensouls.boss.FreezeRejectParticlePacket;
import io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class TimeFreezeHandler {

    @SubscribeEvent
    public static void onFrameAdded(FrameAddedEvent event) {
        try {
            Entity entity = event.getCameraHolderEntity();
            if (!(entity instanceof ServerPlayer player)) return;
            if (AbilityManager.getInstance().getEnabled(player) != AbilityType.TIME_STOP) return;
            if (!hasSoulPhotography(player)) return;
            triggerTimeFreeze(player);
        } catch (Exception ex) {
            LenSouls.LOGGER.error("[TimeFreezeHandler] error", ex);
        }
    }

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
            if (tracker.hasLiveFrozenEntities(player)) {
                showFloatingText(player, net.minecraft.network.chat.Component.translatable("message.lensouls.freeze_already_active"));
                return;
            }
            tracker.forceUnfreeze(player);
        }

        Set<Entity> targets = getEntitiesInFrustum(player);
        targets.remove(player);
        targets.removeIf(target -> !(target instanceof net.minecraft.world.entity.Mob));

        BossToughnessManager toughnessMgr = BossToughnessManager.getInstance();
        Iterator<Entity> it = targets.iterator();
        while (it.hasNext()) {
            Entity target = it.next();
            if (!(target instanceof LivingEntity living)) continue;
            if (!toughnessMgr.has(living)) continue;
            BossToughnessData data = toughnessMgr.get(living);
            if (data == null) continue;
            if (data.isBroken()) {
                it.remove();
                PacketDistributor.sendToPlayersTrackingEntity(target, new FreezeRejectParticlePacket(target.getId()));
                continue;
            }
            if (living.getRandom().nextFloat() >= 0.2f) {
                it.remove();
                PacketDistributor.sendToPlayersTrackingEntity(target, new FreezeRejectParticlePacket(target.getId()));
            }
        }

        if (targets.isEmpty()) return;
        tracker.freeze(player, targets, 100);
    }

    private static void showFloatingText(ServerPlayer player, net.minecraft.network.chat.Component text) {
        var level = player.serverLevel();
        var look = player.getLookAngle();
        var pos = player.getEyePosition().add(look.scale(3.0));
        var cloud = new net.minecraft.world.entity.AreaEffectCloud(level, pos.x, pos.y, pos.z);
        cloud.setCustomName(text.copy().withStyle(s -> s.withBold(true).withColor(net.minecraft.ChatFormatting.RED)));
        cloud.setCustomNameVisible(true);
        cloud.setRadius(0f);
        cloud.setDuration(60);
        cloud.setWaitTime(0);
        cloud.setNoGravity(true);
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
