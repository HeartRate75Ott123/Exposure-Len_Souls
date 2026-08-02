package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.ability.AbilityManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 空间扭曲服务端攻击把关（Better Combat 非原版路径）。
 * <p>
 * {@code ServerNetwork.handleAttackRequest} 用 {@code squaredDistanceTo} 与
 * {@code validationRangeSquared}（基于 {@code getRangeForItem}）校验目标距离，校验通过后
 * 直接 {@code player.attack(entity)}，不走 {@code Player.canInteractWithEntity}。
 * 因此仅改 PlayerInteractionMixin 拦不住该路径。
 * <p>
 * 修复：客户端不再膨胀服务端 {@code getRangeForItem}（见 BetterCombatRangeMixin），此处
 * 在距离校验处放行「空间扭曲球内」的目标——超距攻击只允许球内实体，球外实体回归
 * Better Combat 正常武器范围校验。
 */
@Mixin(targets = "net.bettercombat.network.ServerNetwork", remap = false)
public class BetterCombatServerAttackMixin {

    @Redirect(method = "handleAttackRequest",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;squaredDistanceTo(Lnet/minecraft/world/entity/Entity;)D"))
    private static double lensouls$warpRange(Player player, Entity entity) {
        double dist = player.distanceToSqr(entity);
        if (isWarpTarget(player, entity)) {
            return 0;
        }
        return dist;
    }

    /** 目标在空间扭曲球内（同维度，距球心 ≤ 实体交互范围） */
    private static boolean isWarpTarget(Player player, Entity entity) {
        AbilityManager am = AbilityManager.getInstance();
        if (!am.isSpatialWarpActive(player)) return false;
        String dim = am.getWarpDimension(player);
        if (dim == null || !player.level().dimension().location().toString().equals(dim)) return false;
        Vec3 center = am.getWarpCenter(player);
        double range = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        return entity.position().distanceToSqr(center) <= range * range;
    }
}
