package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.effect.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;

/**
 * 元素抑制效果运行时逻辑。
 * <p>
 * 抑制规则（压制 ≤）：若某生物持有 {@code suppress_<元素>} 效果，等级为 N（amplifier+1），
 * 则：
 * <ol>
 *   <li>施加前拦截（{@link #onEffectApplicable}）：活性效果上身时若已持有同级或更高级抑制，
 *       直接拒绝施加——覆盖照片套装 / 照片饰品 / 羽毛等所有 tick 级活性来源，
 *       避免「事后移除 → 同 tick 重加」导致的活性不断闪烁。</li>
 *   <li>事后兜底（{@link #onLivingTick}）：目标已持有活性、后被打上抑制时移除其等级 ≤ N 的
 *       同元素活性灌注（fire/water/earth/ender_infusion）。</li>
 * </ol>
 */
public class SuppressHandler {

    private static final Map<Holder<MobEffect>, Holder<MobEffect>> SUPPRESS_TO_INFUSION = Map.of(
            ModEffects.SUPPRESS_WATER, ModEffects.WATER_INFUSION,
            ModEffects.SUPPRESS_EARTH, ModEffects.EARTH_INFUSION,
            ModEffects.SUPPRESS_FIRE, ModEffects.FIRE_INFUSION,
            ModEffects.SUPPRESS_ENDER, ModEffects.ENDER_INFUSION
    );

    /**
     * 活性施加前拦截：目标已持有同级或更高级的同元素抑制时，拒绝活性上身。
     * <p>
     * 判定方向与事后移除一致（抑制等级 ≥ 活性等级 则压制）。
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        MobEffectInstance inst = event.getEffectInstance();
        if (inst == null) return;

        for (Map.Entry<Holder<MobEffect>, Holder<MobEffect>> entry : SUPPRESS_TO_INFUSION.entrySet()) {
            Holder<MobEffect> suppress = entry.getKey();
            Holder<MobEffect> infusion = entry.getValue();
            if (infusion != inst.getEffect()) continue;

            var supInst = entity.getEffect(suppress);
            if (supInst == null) continue;
            int suppressLevel = supInst.getAmplifier() + 1;
            int activityLevel = inst.getAmplifier() + 1;
            if (activityLevel <= suppressLevel) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                return;
            }
        }
    }

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
