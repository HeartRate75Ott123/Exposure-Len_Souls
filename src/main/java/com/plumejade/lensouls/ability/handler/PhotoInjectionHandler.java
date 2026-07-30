package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

public class PhotoInjectionHandler {

    private static final Map<UUID, LinkedList<AbilityType>> pendingQueue = new HashMap<>();
    private static final Map<String, String> stolenEntityCache = new HashMap<>();

    public static void cacheStolenEntity(String exposureId, String entityId) {
        if (exposureId != null && !exposureId.isEmpty()) stolenEntityCache.put(exposureId, entityId);
    }

    public static String pollStolenEntity(String exposureId) {
        return exposureId != null ? stolenEntityCache.remove(exposureId) : null;
    }

    @SubscribeEvent
    public static void onFrameAdded(FrameAddedEvent event) {
        try {
            if (!(event.getCameraHolderEntity() instanceof ServerPlayer player)) return;
            AbilityManager am = AbilityManager.getInstance();
            AbilityType ability = am.getEnabled(player);
            if (ability == null || ability == AbilityType.TIME_STOP || ability == AbilityType.VITAL_STRIKE || ability == AbilityType.SOUL_SEVER) return;

            ItemStack hand = player.getMainHandItem();
            if (ModEnchantments.getSoulPhotographyLevel(player.registryAccess(), hand) <= 0) return;

            CompoundTag cameraTag = hand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            cameraTag.putString("lensouls:capture_ability", ability.getId());

            var frameEntities = event.getEntitiesInFrame();
            if (frameEntities != null && !frameEntities.isEmpty()) {
                LivingEntity first = frameEntities.get(0);
                String stolenId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(first.getType()).toString();
                cameraTag.putString("lensouls:stolen_entity", stolenId);
                var frame = event.getFrame();
                if (frame != null) {
                    var expId = frame.identifier();
                    if (expId != null) cacheStolenEntity(expId.toString(), stolenId);
                }
            }

            hand.set(DataComponents.CUSTOM_DATA, CustomData.of(cameraTag));

            ResourceLocation camId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hand.getItem());
            if (!"exposure_polaroid:instant_camera".equals(camId.toString())) {
                enqueue(player.getUUID(), ability);
            }
        } catch (Exception e) {
            LenSouls.LOGGER.error("[PhotoInject] fail", e);
        }
    }

    public static void enqueue(UUID playerUuid, AbilityType ability) {
        pendingQueue.computeIfAbsent(playerUuid, k -> new LinkedList<>()).addLast(ability);
    }

    public static AbilityType pollAbility(UUID playerUuid) {
        LinkedList<AbilityType> queue = pendingQueue.get(playerUuid);
        if (queue == null || queue.isEmpty()) return null;
        AbilityType ability = queue.pollFirst();
        if (queue.isEmpty()) pendingQueue.remove(playerUuid);
        return ability;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (pendingQueue.isEmpty()) return;
        Iterator<Map.Entry<UUID, LinkedList<AbilityType>>> it = pendingQueue.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LinkedList<AbilityType>> entry = it.next();
            ServerPlayer player = getPlayerByUUID(entry.getKey());
            LinkedList<AbilityType> queue = entry.getValue();
            if (player == null || !player.isAlive()) { it.remove(); continue; }
            if (queue.isEmpty()) { it.remove(); }
        }
    }

    private static ServerPlayer getPlayerByUUID(UUID uuid) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.getPlayerList().getPlayer(uuid);
        return null;
    }
}
