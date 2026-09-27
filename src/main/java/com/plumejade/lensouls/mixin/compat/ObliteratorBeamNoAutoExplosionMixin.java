package com.plumejade.lensouls.mixin.compat;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 湮灭构造体照片激光：**不要**那一串附赠爆点。
 * <p>
 * 传奇怪物的 {@code AnnihilationBeamEntity.onAddedToLevel()} 在非 quad 分支里会调
 * {@code spawnExplosions(8, 2, distanceToVec(position(), endPos()) / π)}：
 * <ul>
 *   <li>对 <b>Player 施法者</b>还有额外条件 {@code getXRot() ∈ (-20°, 10°)}——平视时几乎必定成立；</li>
 *   <li>{@code endPos()} <b>每次调用都会重算</b>（内部先 {@code calculateEndPos()}），
 *       所以爆点数量随射线长度线性增长：射线 30 格 ⇒ {@code 30/π ≈ 9.5} ⇒ <b>10 个爆点</b>，
 *       每个硬编码 {@code 8.0} 伤害、再各射 2 发硬编码 {@code 6.0} 的小弹
 *       （{@code AnnihilationExplosionEntity} 里的伤害写死，没有 setter 可缩放）；</li>
 *   <li>而且这些爆点**不带我们的 {@code lensouls:photo_proj} 标记**，既不受
 *       {@code PhotoPercentDamageThrottleHandler} 节流，也不受任何“弹幕伤害”口径约束。</li>
 * </ul>
 * 所以给「我们标记过的」湮灭激光掐掉这一次调用：射线本体（面板 + 目标最大生命 2%）照常，
 * 爆点不再出现。用 {@code @Inject} 打在唯一那个 {@code spawnExplosions} 调用点上并
 * {@code ci.cancel()}（该调用是方法体最后一段，等价于只跳过它，{@code super.onAddedToLevel()}
 * 与 quad 分支都已经跑完）。{@code require = 0} + 配置 {@code required=false}：传奇怪物缺席或改版都不影响启动。
 */
@Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.AnnihilationBeamEntity",
        remap = false)
public class ObliteratorBeamNoAutoExplosionMixin {

    @Inject(method = "onAddedToLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/miauczel/legendary_monsters/entity/AnimatedMonster/Projectile/AnnihilationBeamEntity;spawnExplosions(FID)V"),
            cancellable = true, remap = false, require = 0)
    private void lensouls$skipAutoExplosions(CallbackInfo ci) {
        if (((Entity) (Object) this).getPersistentData().getBoolean("lensouls:photo_proj")) {
            ci.cancel();
        }
    }
}
