package com.plumejade.lensouls.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code invulnerableTime} 声明在 {@link Entity}（父类），
 * 不能直接在 {@code LivingEntity} mixin 中 {@code @Shadow}——用挂 Entity 的 accessor。
 */
@Mixin(Entity.class)
public interface EntityInvulnerableTimeAccessor {

    @Accessor("invulnerableTime")
    void lensouls$setInvulnerableTime(int value);
}