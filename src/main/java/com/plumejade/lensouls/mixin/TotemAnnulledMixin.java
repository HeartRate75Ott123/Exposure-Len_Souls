package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.handler.curse.TimecoreCurse;
import com.plumejade.lensouls.handler.curse.TotemGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ⑤ e21 无援（诅咒态）：**不死图腾彻底不成立**，且不消耗图腾。
 * <p>
 * 为什么不能只靠 {@code LivingUseTotemEvent}：反编译 {@code LivingEntity.checkTotemDeathProtection}
 * （1.21.1 重编译 jar 逐字节核对）流程是
 * <pre>
 *   stack = getItemInHand(hand)
 *   if (!stack.is(TOTEM)) continue;
 *   if (!CommonHooks.onLivingUseTotem(...)) continue;   // ← 取消事件在这里，跳过 shrink
 *   copy = stack.copy(); stack.shrink(1);               // ← 消耗
 * </pre>
 * 取消事件本身确实能阻止消耗，但那是「原版手持图腾」这一条路径。第三方图腾模组（如
 * 《更好的图腾》inventorytotem）在 {@code LivingDeathEvent} 阶段自己背包取物 + 自行复活，
 * 根本不走这里 ⇒ 会出现「图腾被吃掉、诅咒却没拦住」。
 * <p>
 * 因此在入口直接返回 false：原版那条路径连 {@code onLivingUseTotem} 都不会触发、物品也不会被动。
 * 第三方路径由 {@code mixin/compat/InventoryTotemCurseMixin} 负责。
 */
@Mixin(LivingEntity.class)
public abstract class TotemAnnulledMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void lensouls$annulTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player) {
            String why = TotemGuard.reason(player);
            if (why != null) {
                TimecoreCurse.logTotemBlock(player, "vanilla/" + why);
                cir.setReturnValue(false);
            }
        }
    }
}
