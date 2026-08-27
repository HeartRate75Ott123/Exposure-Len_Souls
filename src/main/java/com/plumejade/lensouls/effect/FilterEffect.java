package com.plumejade.lensouls.effect;

import com.plumejade.lensouls.particle.ModParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

public class FilterEffect extends MobEffect {

    public FilterEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    /** 关闭实体身上转圈的可见粒子：返回非 null 的隐藏粒子类型（客户端 provider 返回 null 不渲染），避免 null 导致效果包编码崩溃。 */
    @Override
    public ParticleOptions createParticleOptions(MobEffectInstance instance) {
        return ModParticleTypes.FILTER_HIDDEN.get();
    }
}
