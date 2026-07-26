package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.client.ClientAbilityCache;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 空间扭曲兼容 Better Combat：膨胀 Better Combat 的攻击范围。
 * <p>
 * Better Combat 在 {@code PlayerAttackHelper.getRangeForItem()} 中直接读
 * {@code getAttributeValue(ENTITY_INTERACTION_RANGE)}，不走 {@link Player#entityInteractionRange()}，
 * 所以我们的客户端范围膨胀对它无效。此 Mixin 在结果上附加扭曲球所需距离。
 * <p>
 * 状态来源优先级：
 * <ol>
 *   <li>{@link AbilityManager} — 服务端权威状态（服务端线程、单机客户端线程均可）</li>
 *   <li>{@link ClientAbilityCache} — S2C 同步状态（远程客户端兜底）</li>
 * </ol>
 */
@Mixin(targets = "net.bettercombat.logic.PlayerAttackHelper", remap = false)
public class BetterCombatRangeMixin {

    @Inject(method = "getRangeForItem", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void lensouls$extendBetterCombatRange(Player player, ItemStack stack,
                                                         CallbackInfoReturnable<Double> cir) {
        double needed = resolveWarpReach(player);
        if (needed == 0) return;

        if (needed > cir.getReturnValue()) {
            cir.setReturnValue(needed);
        }
    }

    /**
     * 从两个来源之一获取空间扭曲所需的攻击触及距离：
     * ① AbilityManager（服务端权威，UUID 匹配时客户线程也可查到）
     * ② ClientAbilityCache（远程客户端 S2C 缓存）
     */
    private static double resolveWarpReach(Player player) {
        // 优先级 1：服务端权威数据（单机整合服双线程通用）
        AbilityManager am = AbilityManager.getInstance();
        if (am.isSpatialWarpActive(player)) {
            String dim = am.getWarpDimension(player);
            if (dim == null) return 0;
            if (!player.level().dimension().location().toString().equals(dim)) return 0;

            Vec3 center = am.getWarpCenter(player);
            double playerDist = player.position().distanceTo(center);
            double range = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            return playerDist + range;
        }

        // 优先级 2：客户端 S2C 缓存（远程客户端连接专用服务器时）
        if (ClientAbilityCache.isSpatialWarpActive()) {
            return ClientAbilityCache.getWarpEntityReachDistance();
        }

        return 0;
    }
}
