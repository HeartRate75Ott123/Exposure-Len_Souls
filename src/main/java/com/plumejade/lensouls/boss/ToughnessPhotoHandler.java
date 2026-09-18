package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.item.LensItem;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent;
import io.github.mortuusars.exposure.world.item.component.StoredItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

public class ToughnessPhotoHandler {

    @SubscribeEvent
    public static void onFrameAdded(FrameAddedEvent event) {
        try {
            ItemStack camera = event.getCamera();
            int lensTier = 0;
            StoredItemStack stored = camera.get(Exposure.DataComponents.LENS);
            if (stored != null && stored.getForReading().getItem() instanceof LensItem lens) {
                lensTier = lens.getTier();
            }

            var entities = event.getEntitiesInFrame();
            if (entities == null || entities.isEmpty()) return;

            Entity holder = event.getCameraHolderEntity();
            net.minecraft.server.level.ServerPlayer hitter = holder instanceof net.minecraft.server.level.ServerPlayer p ? p : null;
            BossToughnessManager manager = BossToughnessManager.getInstance();
            boolean playedSound = false;

            for (LivingEntity entity : entities) {
                // 幻灵（借体 BOSS 及其召唤物）不参与削韧：玩家对幻灵不造成削韧伤害，也不出韧性条/音效
                if (com.plumejade.lensouls.entity.PhantomDamageHandler.isPhantomEntity(entity)) continue;
                boolean hasTier = BossTierLoader.getTier(entity) > 0;
                boolean inManager = manager.has(entity);

                if (hasTier && lensTier < 1) {
                    if (!playedSound) failSound(entity, holder);
                    playedSound = true;
                    continue;
                }

                if (hasTier && !inManager && ToughnessDamageHandler.isBoss(entity)) {
                    manager.register(entity);
                    inManager = true;
                }
                if (!inManager) continue;

                int bossTier = BossTierLoader.getTier(entity);
                if (bossTier < 1) { manager.hit(entity, hitter); continue; }
                if (lensTier >= bossTier) { manager.hit(entity, hitter); continue; }
                if (!playedSound) failSound(entity, holder);
                playedSound = true;
            }
        } catch (Exception e) {
            LenSouls.LOGGER.error("[ToughnessPhoto] fail", e);
        }
    }

    private static void failSound(LivingEntity entity, Entity holder) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(entity,
                new ToughnessHitSoundPacket(entity.getId(), true));
        if (holder instanceof net.minecraft.server.level.ServerPlayer player) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.lensouls.lens_tier_insufficient",
                            BossTierLoader.getTier(entity)),
                    true);
        }
    }
}
