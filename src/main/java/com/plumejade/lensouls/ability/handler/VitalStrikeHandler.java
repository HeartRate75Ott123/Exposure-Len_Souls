package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.boss.BossToughnessManager;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 要害打击（VITAL_STRIKE）处理器。
 * <p>
 * VITAL_STRIKE 能力激活、取景器已打开的情况下右键相机，不消耗相纸，
 * 对瞄准的 BOSS 实体造成 1 点削韧伤害，并播放快门音效。
 */
public class VitalStrikeHandler {

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

        // ── 检查当前能力 ──
        if (AbilityManager.getInstance().getEnabled(player) != AbilityType.VITAL_STRIKE) return;

        // ── 检查附魔 ──
        if (ModEnchantments.getSoulPhotographyLevel(player.registryAccess(), stack) <= 0) return;

        // ── 检查取景器是否已打开（第一次右键激活取景器，第二次才是快门） ──
        if (!isViewfinderOpen(stack)) return;

        // ── 取消事件 + 不消耗相纸 + 直接削韧 ──
        event.setCanceled(true);

        // ── 寻找瞄准的 BOSS ──
        LivingEntity boss = findBossInSight(player);
        if (boss == null) return;

        // ── 削韧 ──
        BossToughnessManager manager = BossToughnessManager.getInstance();
        if (!manager.has(boss)) manager.register(boss);
        // 要害打击白霸体 15 tick（0.75s）
        var data = manager.hit(boss);
        if (data != null) data.setInvincibleTicks(15);
    }

    // ========== 工具方法 ==========

    private static boolean isCamera(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CAMERA_ID.equals(id) || POLAROID_ID.equals(id);
    }

    /** 遍历物品的 DataComponent，查找 exposure:camera_active */
    private static boolean isViewfinderOpen(ItemStack stack) {
        for (var entry : stack.getComponents()) {
            ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.type());
            if (CAMERA_ACTIVE_KEY.equals(id)) {
                return entry.value() instanceof Boolean b && b;
            }
        }
        return false;
    }

    private static LivingEntity findBossInSight(ServerPlayer player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        double maxDistSqr = MAX_RANGE * MAX_RANGE;

        AABB searchBox = player.getBoundingBox().inflate(MAX_RANGE);
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : player.level().getEntities(player, searchBox)) {
            if (entity == player || !entity.isAlive()) continue;
            if (!(entity instanceof LivingEntity living)) continue;

            Vec3 toTarget = living.position().subtract(eyePos).normalize();
            if (lookVec.dot(toTarget) < Math.cos(Math.toRadians(30.0))) continue;
            if (entity.distanceToSqr(player) > maxDistSqr) continue;

            BossToughnessManager manager = BossToughnessManager.getInstance();
            if (!manager.has(living) && !com.plumejade.lensouls.boss.ToughnessDamageHandler.isBoss(living)) continue;

            if (entity.distanceToSqr(player) < closestDist) {
                closestDist = entity.distanceToSqr(player);
                closest = living;
            }
        }
        return closest;
    }
}
