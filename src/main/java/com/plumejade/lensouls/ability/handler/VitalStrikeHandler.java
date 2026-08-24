package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.boss.BossTierLoader;
import com.plumejade.lensouls.boss.BossToughnessAttributes;
import com.plumejade.lensouls.boss.BossToughnessManager;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import com.plumejade.lensouls.item.LensItem;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.item.component.StoredItemStack;
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

import java.util.ArrayList;
import java.util.List;

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
        List<LivingEntity> bosses = findAllBossesInSight(player);
        if (bosses.isEmpty()) return;

        // ── 逐个削韧（视锥内全部 BOSS 均生效） ──
        BossToughnessManager manager = BossToughnessManager.getInstance();
        boolean tierFailedShown = false;
        for (LivingEntity boss : bosses) {
            // ── 镜头等级检测 ──
            int bossTier = BossTierLoader.getTier(boss);
            if (bossTier > 0) {
                int lensTier = getLensTier(stack);
                if (lensTier < bossTier) {
                    if (!tierFailedShown) {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("message.lensouls.lens_tier_insufficient", bossTier),
                                true);
                        tierFailedShown = true;
                    }
                    continue;
                }
            }

            // ── 削韧 ──
            if (!manager.has(boss)) manager.register(boss);
            var data = manager.hit(boss, player);
            if (data != null) {
                int interval = BossToughnessAttributes.getInvincibleTicks(boss);
                int playerInterval = com.plumejade.lensouls.integration.TrophyModifierHandler.applyInvincibleModifier(player, interval);
                data.setInvincibleTicks(Math.max(1, playerInterval / 2));
            }
        }
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

    private static List<LivingEntity> findAllBossesInSight(ServerPlayer player) {
        AABB searchBox = player.getBoundingBox().inflate(MAX_RANGE);
        List<LivingEntity> result = new ArrayList<>();

        for (Entity entity : player.level().getEntities(player, searchBox)) {
            if (entity == player || !entity.isAlive()) continue;
            if (!(entity instanceof LivingEntity living)) continue;

            // 锥角 + 射程判定，支持子部件（娜迦尾巴/九头蛇头等）追溯到本体
            if (!com.plumejade.lensouls.util.AimTargetUtil.isAimedAt(player, living, MAX_RANGE, 30.0)) continue;

            BossToughnessManager manager = BossToughnessManager.getInstance();
            if (!manager.has(living) && !com.plumejade.lensouls.boss.ToughnessDamageHandler.isBoss(living)) continue;

            result.add(living);
        }
        return result;
    }

    private static int getLensTier(ItemStack camera) {
        var stored = camera.get(io.github.mortuusars.exposure.Exposure.DataComponents.LENS);
        if (stored instanceof StoredItemStack s && s.getForReading().getItem() instanceof LensItem lens) {
            return lens.getTier();
        }
        return 0;
    }
}
