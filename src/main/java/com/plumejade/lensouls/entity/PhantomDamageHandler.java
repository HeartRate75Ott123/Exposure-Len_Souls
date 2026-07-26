package com.plumejade.lensouls.entity;

import com.plumejade.lensouls.config.DataPackLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 幻灵伤害补偿器 — 检测幻灵实体的攻击，按元素强度动态分配倍率 + 附加元素标签。
 * <p>
 * 补偿倍率 = itemMultiplier × 1.25（Ingis 2.0→2.5x 为基准）。
 * 元素标签 = DataPackLoader.getWeakness(target, element) 提供的元素弱点倍率。
 */
public class PhantomDamageHandler {

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getOriginalDamage() <= 0f) return;

        Entity attacker = event.getSource().getDirectEntity();
        if (attacker == null) return;

        if (!BossPhantomType.isPhantomClassName(attacker.getClass().getName())) return;
        if (!attacker.getPersistentData().getBoolean("lensouls:phantom")) return;

        BossPhantomType type = BossPhantomType.getTypeForClass(attacker.getClass().getName());
        if (type == null) return;

        float compensation = type.getDamageMultiplier() * 1.25f;

        LivingEntity target = event.getEntity();
        ResourceLocation targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        float weakness = DataPackLoader.getWeakness(targetId, type.getElement());

        float total = compensation + weakness;
        float original = event.getOriginalDamage();
        float boosted = original * Math.max(1.0f, total);
        event.setNewDamage(boosted);
    }
}
