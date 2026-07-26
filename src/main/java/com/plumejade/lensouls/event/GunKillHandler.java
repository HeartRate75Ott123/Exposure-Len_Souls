package com.plumejade.lensouls.event;

import com.plumejade.lensouls.entity.GunBulletEntity;
import com.plumejade.lensouls.item.DimensionalGunItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class GunKillHandler {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getDirectEntity() instanceof GunBulletEntity bullet) {
            Entity owner = bullet.getOwner();
            if (owner instanceof ServerPlayer player) {
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.getItem() instanceof DimensionalGunItem gun) {
                        gun.addKill(stack);
                        break;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            DimensionalGunItem.checkDimensionUnlocks(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            DimensionalGunItem.checkDimensionUnlocks(event.getEntity());
        }
    }
}
