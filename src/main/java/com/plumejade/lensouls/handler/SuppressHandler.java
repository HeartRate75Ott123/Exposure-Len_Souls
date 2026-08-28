package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.effect.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Map;

/**
 * 元素抑制效果运行时逻辑。
 * <p>
 * 抑制规则（压制 ≤）：若某生物持有 {@code suppress_<元素>} 效果，等级为 N（amplifier+1），
 * 则移除其身上等级 ≤ N 的同元素活性灌注（fire/water/earth/ender_infusion）。
 */
public class SuppressHandler {

    private static final Map<Holder<MobEffect>, Holder<MobEffect>> SUPPRESS_TO_INFUSION = Map.of(
            ModEffects.SUPPRESS_WATER, ModEffects.WATER_INFUSION,
            ModEffects.SUPPRESS_EARTH, ModEffects.EARTH_INFUSION,
            ModEffects.SUPPRESS_FIRE, ModEffects.FIRE_INFUSION,
            ModEffects.SUPPRESS_ENDER, ModEffects.ENDER_INFUSION
    );

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity.level().isClientSide()) return;

        for (Map.Entry<Holder<MobEffect>, Holder<MobEffect>> entry : SUPPRESS_TO_INFUSION.entrySet()) {
            Holder<MobEffect> suppress = entry.getKey();
            Holder<MobEffect> infusion = entry.getValue();
            var supInst = entity.getEffect(suppress);
            if (supInst == null) continue;

            int suppressLevel = supInst.getAmplifier() + 1;
            var infInst = entity.getEffect(infusion);
            if (infInst != null && (infInst.getAmplifier() + 1) <= suppressLevel) {
                entity.removeEffect(infusion);
            }
        }
    }
}
