package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.handler.curse.TimecoreCurse;
import com.plumejade.lensouls.handler.curse.TotemGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ⑤ e21 无援 ×《更好的图腾》(inventorytotem) 兼容：诅咒态下不允许它「从背包掏图腾复活」。
 * <p>
 * 反编译 {@code inventorytotem-1.21.1-3.4} 的真实实现：
 * <pre>
 *   TotemEvent.allowPlayerDeath(ServerLevel, ServerPlayer) -> boolean
 *       · 主手/副手拿着图腾 → return true（交给原版，原版由 TotemAnnulledMixin 拦）
 *       · 背包里找到图腾   → setHealth(1) + removeAllEffects() + 吸收/抗火/再生 + broadcastEntityEvent(35)
 *                          + found.shrink(1) → return false（→ 调用方 setCanceled(true) 取消死亡）
 * </pre>
 * 返回值语义是「**是否允许死亡**」：true = 什么都不做放行，false = 消耗图腾并把死亡取消。
 * 所以诅咒态下在 HEAD 直接返回 **true** —— 不取物品、不加效果、正常死亡。
 * <p>
 * 该模组不在编译依赖里 ⇒ {@code targets} 字符串 + {@code remap=false} + {@code require=0}，
 * 配置 `required=false`，模组缺席时整条跳过。
 */
@Mixin(targets = "com.natamus.inventorytotem_common_neoforge.events.TotemEvent", remap = false)
public abstract class InventoryTotemCurseMixin {

    @Inject(method = "allowPlayerDeath", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lensouls$blockInventoryTotem(ServerLevel level, ServerPlayer player,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (player != null) {
            String why = TotemGuard.reason(player);
            if (why != null) {
                TimecoreCurse.logTotemBlock(player, "inventorytotem/" + why);
                cir.setReturnValue(true); // 允许死亡 = 不消耗图腾、不复活
            }
        }
    }
}
