package com.plumejade.lensouls.event;

import com.plumejade.lensouls.entity.GunBulletEntity;
import com.plumejade.lensouls.entity.PhantomDamageHandler;
import com.plumejade.lensouls.item.DimensionalGunItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class GunKillHandler {

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();

        // 1. 子弹击杀：按枪 UUID 在玩家背包里找回同一把枪（射击后切物品/换手也能记对）
        if (direct instanceof GunBulletEntity bullet) {
            java.util.UUID gunId = bullet.getGunId();
            if (gunId != null && bullet.getOwner() instanceof ServerPlayer player) {
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.getItem() instanceof DimensionalGunItem gun && gunId.equals(gun.getGunId(stack))) {
                        gun.addKill(stack);
                        return;
                    }
                }
                ItemStack off = player.getOffhandItem();
                if (off.getItem() instanceof DimensionalGunItem gun && gunId.equals(gun.getGunId(off))) {
                    gun.addKill(off);
                }
            }
            return;
        }

        // 2. 幻灵击杀：借体本体 / 它召出的随从 / 它们射出的弹幕，都按 persistentData 里的
        //    归属 UUID 找召唤者玩家。
        //    旧写法只认「直接实体带 lensouls:phantom 且能按实体 ID 反查活跃幻灵」，因此
        //    随从与幻灵射出的弹一律不计数——那些实体根本不在 activePhantoms 里，ID 反查必落空。
        ServerPlayer phantomOwner = PhantomDamageHandler.resolveOwnerPlayer(source);
        if (phantomOwner != null) {
            creditKill(phantomOwner);
            return;
        }

        // 3. 玩家直接击杀（近战/其他武器）且持有次元枪
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            creditKill(player);
        }
    }

    private static void creditKill(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof DimensionalGunItem gun) {
                gun.addKill(stack);
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer) {
            DimensionalGunItem.checkDimensionUnlocks(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer) {
            DimensionalGunItem.checkDimensionUnlocks(event.getEntity());
        }
    }
}
