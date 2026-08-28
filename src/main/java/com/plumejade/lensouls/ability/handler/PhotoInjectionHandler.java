package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.CameraAbilityStore;
import com.plumejade.lensouls.boss.ToughnessDamageHandler;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import com.plumejade.lensouls.handler.FeatherHardmanHandler;
import io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PhotoInjectionHandler {

    /** 帧标识符 → 拍摄时的能力（独立存储，互不覆盖） */
    private static final Map<String, AbilityType> pendingAbilities = new ConcurrentHashMap<>();
    private static final Map<String, String> stolenEntityCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> bossFlagCache = new ConcurrentHashMap<>();

    public static Boolean pollBoss(String exposureId) {
        return exposureId != null ? bossFlagCache.remove(exposureId) : null;
    }

    public static void cacheStolenEntity(String exposureId, String entityId) {
        if (exposureId != null && !exposureId.isEmpty()) stolenEntityCache.put(exposureId, entityId);
    }

    public static String pollStolenEntity(String exposureId) {
        return exposureId != null ? stolenEntityCache.remove(exposureId) : null;
    }

    /** 按帧标识符取能力并删除 */
    public static AbilityType pollAbility(String exposureId) {
        return exposureId != null ? pendingAbilities.remove(exposureId) : null;
    }

    @SubscribeEvent
    public static void onFrameAdded(FrameAddedEvent event) {
        try {
            if (!(event.getCameraHolderEntity() instanceof ServerPlayer player)) return;
            var frame = event.getFrame();
            if (frame == null) return;
            String exposureId = frame.identifier() != null ? frame.identifier().toString() : null;
            if (exposureId == null || exposureId.isEmpty()) return;

            // 羽·荒厄遗咒：残存魔力被吞噬，无法发动相机能力
            if (FeatherHardmanHandler.hasHardman(player)) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.lensouls.hardman.inject_fail"), true);
                return;
            }

            AbilityType ability = CameraAbilityStore.getSelected(player);
            if (ability == null) return;
            if (ability == AbilityType.TIME_STOP || ability == AbilityType.VITAL_STRIKE || ability == AbilityType.SOUL_SEVER) return;

            ItemStack hand = CameraInputHandler.getWieldedCamera(player);
            if (ModEnchantments.getSoulPhotographyLevel(player.registryAccess(), hand) <= 0) return;

            // 按帧 ID 存储能力，后续切换能力不影响已拍帧
            LenSouls.LOGGER.info("[PhotoInject] onFrameAdded: exposureId={} ability={}", exposureId, ability);
            pendingAbilities.put(exposureId, ability);

            // 能力窃取：缓存被窃取实体
            if (ability == AbilityType.ABILITY_STEAL) {
                var frameEntities = event.getEntitiesInFrame();
                if (frameEntities != null && !frameEntities.isEmpty()) {
                    LivingEntity first = frameEntities.get(0);
                    String stolenId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(first.getType()).toString();
                    cacheStolenEntity(exposureId, stolenId);
                    bossFlagCache.put(exposureId, ToughnessDamageHandler.isBoss(first));
                }
            }
        } catch (Exception e) {
            LenSouls.LOGGER.error("[PhotoInject] fail", e);
        }
    }
}
