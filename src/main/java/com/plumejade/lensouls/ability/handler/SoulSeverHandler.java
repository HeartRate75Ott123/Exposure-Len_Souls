package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import com.plumejade.lensouls.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class SoulSeverHandler {

    private static final ResourceLocation CAMERA_ID = ResourceLocation.parse("exposure:camera");
    private static final ResourceLocation POLAROID_ID = ResourceLocation.parse("exposure_polaroid:instant_camera");
    private static final ResourceLocation CAMERA_ACTIVE_KEY = ResourceLocation.parse("exposure:camera_active");
    private static final double MAX_RANGE = 24.0;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickCamera(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        if (!isCamera(stack)) return;
        if (AbilityManager.getInstance().getEnabled(player) != AbilityType.SOUL_SEVER) return;
        if (ModEnchantments.getSoulPhotographyLevel(player.registryAccess(), stack) <= 0) return;
        if (!isViewfinderOpen(stack)) return;

        event.setCanceled(true);

        LivingEntity target = findTarget(player);
        if (target == null || target.isDeadOrDying()) {
            failSound(player, player.getX(), player.getY(), player.getZ());
            player.getCooldowns().addCooldown(stack.getItem(), 10);
            return;
        }

        if (player.getRandom().nextDouble() < 0.3) {
            float ratio = 0.1f + player.getRandom().nextFloat() * 0.1f;
            target.setHealth(Math.max(0f, target.getHealth() - target.getHealth() * ratio));
            spawnShockwave((ServerLevel) player.level(), target);
            player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.0f);
            player.getCooldowns().addCooldown(stack.getItem(), 200);
        } else {
            failSound(player, target.getX(), target.getY(), target.getZ());
            player.getCooldowns().addCooldown(stack.getItem(), 10);
        }
    }

    private static void failSound(ServerPlayer player, double x, double y, double z) {
        player.level().playSound(null, x, y, z,
                ModSounds.TOUGHNESS_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void spawnShockwave(ServerLevel level, LivingEntity target) {
        Vec3 pos = target.position();
        for (int ring = 0; ring < 3; ring++) {
            float radius = 1.5f + ring;
            for (int i = 0; i < 16; i++) {
                double angle = 2 * Math.PI * i / 16;
                level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        pos.x + Math.cos(angle) * radius, pos.y + 0.5 + ring * 0.3, pos.z + Math.sin(angle) * radius,
                        1, 0, 0, 0, 0);
            }
        }
        for (int h = 0; h < 8; h++) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    pos.x, pos.y + h * 0.4, pos.z, 3, 0.2, 0.1, 0.2, 0.05);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);
    }

    private static boolean isCamera(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CAMERA_ID.equals(id) || POLAROID_ID.equals(id);
    }

    private static boolean isViewfinderOpen(ItemStack stack) {
        for (var entry : stack.getComponents()) {
            ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.type());
            if (CAMERA_ACTIVE_KEY.equals(id)) return entry.value() instanceof Boolean b && b;
        }
        return false;
    }

    private static LivingEntity findTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double maxDistSqr = MAX_RANGE * MAX_RANGE;
        AABB box = player.getBoundingBox().inflate(MAX_RANGE);
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Entity e : player.level().getEntities(player, box)) {
            if (e == player || !e.isAlive() || !(e instanceof LivingEntity living)) continue;
            Vec3 dir = living.position().subtract(eye).normalize();
            if (look.dot(dir) < Math.cos(Math.toRadians(30.0))) continue;
            if (e.distanceToSqr(player) > maxDistSqr) continue;
            double d = e.distanceToSqr(player);
            if (d < closestDist) { closestDist = d; closest = living; }
        }
        return closest;
    }
}
