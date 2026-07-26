package com.plumejade.lensouls.mixin.ability;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 空间扭曲服务端把关。
 * <p>
 * 不修改属性值。在 {@code canInteractWithBlock/Entity} 中检查：
 * 目标在玩家自身触及球 或 目标在扭曲球内 → 允许，否则拒绝。
 * <p>
 * 注意：{@link net.minecraft.server.network.ServerGamePacketListenerImpl#handleInteract}
 * 调用的是 {@code canInteractWithEntity(AABB, double)} 重载（padding=1.0），
 * 而非 {@code (Entity, double)} 重载。以下两个重载均有注入。
 */
@Mixin(Player.class)
public class PlayerInteractionMixin {

    @Inject(method = "canInteractWithBlock", at = @At("RETURN"), cancellable = true)
    private void lensouls$gateBlock(BlockPos pos, double distanceTo, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return; // 玩家自身范围已够
        Player self = (Player) (Object) this;
        if (!AbilityManager.getInstance().isSpatialWarpActive(self)) return;
        if (!dimensionMatches(self)) return;

        Vec3 blockCenter = Vec3.atCenterOf(pos);
        Vec3 center = AbilityManager.getInstance().getWarpCenter(self);
        double range = self.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        boolean inWarpSphere = blockCenter.distanceToSqr(center) <= range * range;

        if (inWarpSphere) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 实体重载 {@code (Entity, double)}。
     * 由 {@link net.minecraft.server.level.ServerPlayerGameMode} 等路径调用。
     */
    @Inject(method = "canInteractWithEntity(Lnet/minecraft/world/entity/Entity;D)Z", at = @At("RETURN"), cancellable = true)
    private void lensouls$gateEntity(Entity target, double distanceTo, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        Player self = (Player) (Object) this;
        if (!AbilityManager.getInstance().isSpatialWarpActive(self)) return;
        if (!dimensionMatches(self)) return;

        Vec3 center = AbilityManager.getInstance().getWarpCenter(self);
        double range = self.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        boolean inWarpSphere = target.position().distanceToSqr(center) <= range * range;

        if (inWarpSphere) {
            cir.setReturnValue(true);
        }
    }

    /**
     * AABB 重载 {@code (AABB, double)}。
     * <p>
     * 由 {@link net.minecraft.server.network.ServerGamePacketListenerImpl#handleInteract}
     * 调用（padding=1.0）。Better Combat 的 {@code useVanillaPacket=true} 路径也经过这里。
     * <p>
     * 使用全限定描述符避免与方法名相同的 Entity 重载混淆。
     */
    @Inject(method = "canInteractWithEntity(Lnet/minecraft/world/phys/AABB;D)Z", at = @At("RETURN"), cancellable = true)
    private void lensouls$gateEntityAABB(AABB boundingBox, double padding, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        Player self = (Player) (Object) this;
        if (!AbilityManager.getInstance().isSpatialWarpActive(self)) return;
        if (!dimensionMatches(self)) return;

        Vec3 center = AbilityManager.getInstance().getWarpCenter(self);
        double range = self.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        // AABB.distanceToSqr 返回边界框到球心的最短距离平方
        boolean inWarpSphere = boundingBox.distanceToSqr(center) <= range * range;

        if (inWarpSphere) {
            cir.setReturnValue(true);
        }
    }

    private static boolean dimensionMatches(Player player) {
        String warpDim = AbilityManager.getInstance().getWarpDimension(player);
        if (warpDim == null) return false;
        ResourceLocation playerDim = player.level().dimension().location();
        return playerDim.toString().equals(warpDim);
    }
}
