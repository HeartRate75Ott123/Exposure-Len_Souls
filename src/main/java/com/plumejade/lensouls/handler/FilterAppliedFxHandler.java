package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.effect.FilterEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * 特殊滤镜（16 个 {@link FilterEffect}）成功施加时的特效入口。
 * <p>
 * 选 {@code MobEffectEvent.Added} 而不是在 {@code FilterPhotoHandler} 里逐个 call site 补：
 * 施加点有自拍自己 / 队友 / 取景框内全部敌人 / 万象加持随机 buff 好几条路径，
 * 事件是唯一不会漏的口子。**只认 {@link FilterEffect}**——别把玩家喝药水、被其它模组上 buff
 * 也算成"滤镜施加"。
 * <p>
 * 药水滤镜（药水玻璃板）走的是原版药水效果，不经过这里：它由
 * {@code FilterPhotoHandler.applyPotionFilter} 在自己的施加循环里直接广播，
 * 这样"喝药水"不会触发滤镜特效。
 */
public class FilterAppliedFxHandler {

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide()) return;

        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) return;

        Holder<MobEffect> effect = instance.getEffect();
        if (!(effect.value() instanceof FilterEffect)) return;

        FilterAppliedFx.broadcast(entity, effect.value().getColor());
    }
}
