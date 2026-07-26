package com.plumejade.lensouls.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 空 mixin，仅用于触发 NeoForge moddev 的 mixin 注解处理器。
 * <p>
 * 当 {@code lensouls.mixins.json} 的 {@code mixins} 数组非空时，
 * NeoForge moddev 自动配置 Mixin 注解处理器并为整个配置（含 {@code client} 数组条目）
 * 生成 refmap 文件。此 mixin 不注入任何逻辑。
 */
@Mixin(Entity.class)
public abstract class SoulOutlineMixinTrigger {
    // NO-OP: 仅触发注解处理器
}
