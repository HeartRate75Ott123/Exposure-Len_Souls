package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.util.FreezeTracker;
import io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 时间定格触发（拍照触发）。
 * <p>
 * 全局 freeze 语义：不再收集视锥内实体、不做韧性拦截——冻结整个世界。
 */
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
        if (tracker.isFrozen()) {
            showFloatingText(player, Component.translatable("message.lensouls.freeze_already_active"));
            return;
        }
        tracker.freeze(player, 100);
    }

    private static void showFloatingText(ServerPlayer player, Component text) {
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
}