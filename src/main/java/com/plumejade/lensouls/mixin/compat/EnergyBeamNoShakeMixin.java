package com.plumejade.lensouls.mixin.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 云筑魔像照片激光：去除屏幕晃动。
 * <p>
 * {@code EnergyBeamEntity.tick()} 每 tick 调用
 * {@code CameraShakeEntity.cameraShake(...)} 制造屏幕震动（boss 本体演出需要）；
 * 玩家通过照片技能施放的光束同样触发，导致使用时镜头一直晃、很难受。
 * <p>
 * 本 mixin 在 tick 内 redirect 该调用：当光束施放者是 {@link Player}（照片施放）时跳过震动，
 * 云筑魔像 boss 自身（caster 为 Cloud_GolemEntity）不受影响。
 */
@Mixin(value = net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.EnergyBeamEntity.class, remap = false)
public abstract class EnergyBeamNoShakeMixin {

    @Shadow
    public net.minecraft.world.entity.LivingEntity caster;

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/miauczel/legendary_monsters/entity/AnimatedMonster/Effect/CameraShakeEntity;cameraShake(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;FFII)V"),
            remap = false, require = 0)
    private void lensouls$skipShakeForPlayerCast(Level world, Vec3 position, float radius,
                                                 float magnitude, int duration, int fadeDuration) {
        if (this.caster instanceof Player) return;
        net.miauczel.legendary_monsters.entity.AnimatedMonster.Effect.CameraShakeEntity.cameraShake(
                world, position, radius, magnitude, duration, fadeDuration);
    }
}
