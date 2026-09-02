package com.plumejade.lensouls.mixin.compat;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 焰魔（灾变）照片火球：不再点燃方块。
 * <p>
 * {@code Ignis_Fireball_Entity} 命中（onHitEntity/onHitBlock）时构造
 * {@code IgnisExplosion(..., fire=true, KEEP)}：KEEP 不会炸毁方块，但
 * {@code IgnisExplosion.finalizeExplosion()} 的 {@code if (this.fire)} 仍会向爆心附近
 * 空气位放置火焰 → 玩家带炎魔照片在家挥砍误发火球时烧毁建筑。
 * <p>
 * 本 mixin 把两处 {@code IgnisExplosion.<init>} 的 {@code fire} 实参（索引 8）改为 false，
 * 仅当该火球带 {@code lensouls:photo_proj} 标记（照片弹幕）时生效；boss 自身火球不受影响。
 */
@Mixin(value = com.github.L_Ender.cataclysm.entity.projectile.Ignis_Fireball_Entity.class, remap = false)
public abstract class IgnisFireballNoFireMixin {

    private static final String CTOR = "Lcom/github/L_Ender/cataclysm/util/CustomExplosion/IgnisExplosion;" +
            "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;" +
            "Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;" +
            "DDDFZLnet/minecraft/world/level/Explosion$BlockInteraction;)V";

    @ModifyArg(method = "onHitEntity", at = @At(value = "INVOKE", target = CTOR),
            index = 8, remap = false, require = 0)
    private boolean lensouls$noFireOnHitEntity(boolean fire) {
        return lensouls$resolveFire(fire);
    }

    @ModifyArg(method = "onHitBlock", at = @At(value = "INVOKE", target = CTOR),
            index = 8, remap = false, require = 0)
    private boolean lensouls$noFireOnHitBlock(boolean fire) {
        return lensouls$resolveFire(fire);
    }

    /** 照片弹幕（lensouls:photo_proj）→ 熄灭爆炸放火；否则保持原样 */
    private boolean lensouls$resolveFire(boolean fire) {
        Entity self = (Entity) (Object) this;
        if (self.getPersistentData().getBoolean("lensouls:photo_proj")) {
            return false;
        }
        return fire;
    }
}
