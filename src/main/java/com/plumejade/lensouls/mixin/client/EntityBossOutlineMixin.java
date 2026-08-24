package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家 BOSS 镜魂 → 复用原版 outline（边缘逻辑全由原版完成，无噪点）：
 * <ul>
 *   <li>{@code isCurrentlyGlowing}：玩家有 Boss 镜魂 effect → true → 原版把整个模型
 *       （含盔甲/饰品/手持物 layer）画进 OutlineBufferSource</li>
 *   <li>{@code getTeamColor}：返回 {@link BossOutlineColors#MARKER_COLOR} 标记色，
 *       供渐变 composite shader 识别玩家轮廓并替换为四色渐变</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class EntityBossOutlineMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void lensouls$bossGlow(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LivingEntity le && BossOutlineColors.fromEntity(le) != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
    private void lensouls$bossTeamColor(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof LivingEntity le && BossOutlineColors.fromEntity(le) != null) {
            cir.setReturnValue(BossOutlineColors.MARKER_COLOR);
        }
    }
}
