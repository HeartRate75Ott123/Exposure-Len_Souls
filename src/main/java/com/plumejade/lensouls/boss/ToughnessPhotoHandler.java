package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.item.LensItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

public class ToughnessPhotoHandler {

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        try {
            Class<?> eventClass = Class.forName("io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent");
            Method getEntitiesInFrame = eventClass.getMethod("getEntitiesInFrame");
            Method getCamera = eventClass.getMethod("getCamera");
            Method getCameraHolderEntity = eventClass.getMethod("getCameraHolderEntity");

            Class<?> dataComponentsClass = Class.forName("io.github.mortuusars.exposure.Exposure$DataComponents");
            java.lang.reflect.Field lensField = dataComponentsClass.getField("LENS");
            net.minecraft.core.component.DataComponentType<?> lensType =
                    (net.minecraft.core.component.DataComponentType<?>) lensField.get(null);
            Method itemStackGet = ItemStack.class.getMethod("get", net.minecraft.core.component.DataComponentType.class);

            Method addListener = NeoForge.EVENT_BUS.getClass()
                    .getMethod("addListener", EventPriority.class, boolean.class, Class.class, Consumer.class);

            addListener.invoke(NeoForge.EVENT_BUS, EventPriority.NORMAL, false, eventClass,
                    (Consumer<Object>) event -> {
                        try {
                            ItemStack camera = (ItemStack) getCamera.invoke(event);
                            int lensTier = 0;
                            Object stored = itemStackGet.invoke(camera, lensType);
                            if (stored != null) {
                                Object innerStack = stored.getClass().getMethod("getForReading").invoke(stored);
                                if (innerStack instanceof ItemStack lensStack
                                        && lensStack.getItem() instanceof LensItem lens) {
                                    lensTier = lens.getTier();
                                }
                            }

                            @SuppressWarnings("unchecked")
                            List<LivingEntity> entities = (List<LivingEntity>) getEntitiesInFrame.invoke(event);
                            if (entities == null || entities.isEmpty()) return;

                            Entity holder = (Entity) getCameraHolderEntity.invoke(event);
                            net.minecraft.server.level.ServerPlayer hitter = holder instanceof net.minecraft.server.level.ServerPlayer p ? p : null;
                            BossToughnessManager manager = BossToughnessManager.getInstance();
                            boolean playedSound = false;

                            for (LivingEntity entity : entities) {
                                boolean hasTier = BossTierLoader.getTier(entity) > 0;
                                boolean inManager = manager.has(entity);

                                if (hasTier && lensTier < 1) {
                                    if (!playedSound) failSound(entity, holder);
                                    playedSound = true;
                                    continue;
                                }

                                if (hasTier && !inManager) {
                                    manager.register(entity);
                                    inManager = true;
                                }
                                if (!inManager) continue;

                                int bossTier = BossTierLoader.getTier(entity);
                                if (bossTier < 1) { manager.hit(entity, hitter); break; }
                                if (lensTier >= bossTier) { manager.hit(entity, hitter); break; }
                                if (!playedSound) failSound(entity, holder);
                                playedSound = true;
                            }
                        } catch (Exception e) {
                            LenSouls.LOGGER.error("[ToughnessPhoto] fail", e);
                        }
                    });
        } catch (Exception e) {
            LenSouls.LOGGER.error("[ToughnessPhoto] 注册失败", e);
        }
    }

    private static void failSound(LivingEntity entity, Entity holder) {
        PacketDistributor.sendToPlayersTrackingEntity(entity,
                new ToughnessHitSoundPacket(entity.getId(), true));
        if (holder instanceof net.minecraft.server.level.ServerPlayer player) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.lensouls.lens_tier_insufficient",
                            BossTierLoader.getTier(entity)),
                    true);
        }
    }
}
