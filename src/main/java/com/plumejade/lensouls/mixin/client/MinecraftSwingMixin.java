package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.handler.curse.AttackDiag;
import com.plumejade.lensouls.network.PhotoSwingPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 捕获玩家每次左键攻击意图（含空手/对空气），发送挥击信号到服务端，
 * 触发 Boss 照片弹幕判定（覆盖原版空手连续左键）。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftSwingMixin {

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void lensouls$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        PacketDistributor.sendToServer(new PhotoSwingPacket());
        // 「空挥」诊断：客户端这一次左键**到底指到了什么**。
        // 只有这里指向了实体，服务端才会收到攻击包；指向空气 ⇒ 服务端连受伤事件都不会有。
        if (AttackDiag.enabled()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                var hit = mc.hitResult;
                if (hit instanceof EntityHitResult ehr && ehr.getEntity() != null) {
                    Entity target = ehr.getEntity();
                    double dist = Math.sqrt(mc.player.distanceToSqr(target));
                    AttackDiag.log("客户端左键命中目标 目标={} 距离={} 实体交互范围={} 主手={}",
                            target.getType(), String.format("%.2f", dist),
                            String.format("%.2f", mc.player.getAttributeValue(
                                    net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE)),
                            mc.player.getMainHandItem().getItem());
                } else {
                    AttackDiag.log("客户端左键【空挥】hitResult={} 实体交互范围={} 主手={}",
                            hit == null ? "null" : hit.getType(),
                            String.format("%.2f", mc.player.getAttributeValue(
                                    net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE)),
                            mc.player.getMainHandItem().getItem());
                }
            }
        }
    }
}
