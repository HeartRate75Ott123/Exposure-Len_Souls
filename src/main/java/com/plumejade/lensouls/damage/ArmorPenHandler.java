package com.plumejade.lensouls.damage;

import com.plumejade.lensouls.entity.GunBulletEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 次元枪穿甲：在 {@link LivingDamageEvent.Pre} 中将护甲削减量按穿甲比例加回。
 * <p>
 * NeoForge 1.21.1 的护甲削减在 Pre 事件之前已结算（{@link DamageContainer.Reduction#ARMOR}），
 * 因此只需补偿：finalReduction = armorReduction × (1 - pen)。
 */
public class ArmorPenHandler {

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getDirectEntity() instanceof GunBulletEntity bullet)) return;

        double pen = bullet.getArmorPen() / 100.0;
        if (pen <= 0) return;

        float armorReduction = event.getContainer().getReduction(DamageContainer.Reduction.ARMOR);
        if (armorReduction <= 0) return;

        event.setNewDamage(event.getNewDamage() + armorReduction * (float) pen);
    }
}
