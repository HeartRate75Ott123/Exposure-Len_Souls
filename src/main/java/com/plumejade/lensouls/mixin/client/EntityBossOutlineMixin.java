package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玩家 BOSS 镜魂 → 复用原版 outline（Apotheosis 同款，完全原版 glowing 单色描边）：
 * <ul>
 *   <li>{@code isCurrentlyGlowing}：玩家有 Boss 镜魂 effect → true → 原版把整个模型
 *       （含盔甲/饰品/手持物 layer）画进 OutlineBufferSource</li>
 *   <li>{@code getTeamColor}：返回镜魂元素主色（0xRRGGBB），原版 outline 直接以该单色上屏</li>
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
        if ((Object) this instanceof LivingEntity le) {
            BossOutlineColors colors = BossOutlineColors.fromEntity(le);
            if (colors != null) {
                cir.setReturnValue(colors.primaryColor());
            }
        }
    }
}
