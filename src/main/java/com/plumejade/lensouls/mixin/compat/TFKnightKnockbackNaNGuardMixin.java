package com.plumejade.lensouls.mixin.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 暮色幻影骑士击退里的 0/0：把退化方向换成原版那套「随机一个极小方向」。
 *
 * <h2>崩服的真根因（对实机 jar 4.8.3345 的 {@code javap -c} 逐条核对）</h2>
 * {@code KnightPhantom} 自己重写了 {@code knockback}，而且<b>不调 super</b>：
 * <pre>
 *   this.hasImpulse = true;
 *   float f = Mth.sqrt((float)(x*x + z*z));     // 水平距离
 *   setDeltaMovement(getDeltaMovement().scale(0.5));
 *   setDeltaMovement(new Vec3(delta.x - x / f * 0.2,   // ← x=z=0 ⇒ 0/0 = NaN
 *                             delta.y + 0.2,
 *                             delta.z - z / f * 0.2));
 * </pre>
 * 「不调 super」意味着<b>原版 {@code LivingEntity.knockback} 里
 * {@code x*x + z*z < 1.0E-5 → 随机一个极小方向} 的保护被绕开</b>，NeoForge 的
 * {@code LivingKnockBackEvent} 也不会派发（所以监听那个事件完全兜不住）。
 * <p>
 * 什么时候水平方向是 {@code (0,0)}：<b>不带坐标的伤害源</b>。fdbosses 的
 * {@code GEBURAH_LASER_STRIKE_SOURCE} 是裸 {@code new DamageSource(holder)}、
 * {@code minecraft:generic} 同理 —— {@code LivingEntity.hurt} 里
 * {@code getSourcePosition()} 返回 null，跳过方向计算，直接 {@code knockback(0.4, 0, 0)}。
 * 其它生物都没事（原版会把零方向随机化），只有这个骑士把速度写成 NaN →
 * 下一 tick {@code move()} 写进坐标 → 它自己的包围盒 NaN → 编队 AI
 * {@code getEntitiesOfClass} 连自己都查不到 → {@code nextInt(0)} 崩服。
 *
 * <h2>为什么这不是「跳过选中」</h2>
 * 骑士的击退、掉血、编队、选中全部照常；只有<b>暮色那行会除 0 的算式</b>被换成本意等价的写法：
 * 速度减半 + 沿「水平方向」推 0.2（方向退化时按原版做法随机一个 1e-2 量级的方向）。
 * 也就是说：原来该发生的击退依然发生，只是不再变成 NaN。
 *
 * <h2>开销</h2>
 * 本注入被编译进 {@code KnightPhantom.knockback} 方法内部，<b>不是</b>伤害事件监听器：
 * 别的实体受伤、别的生物被击退，都不会执行这里，也没有 {@code instanceof} 判断。
 * 骑士每次被击退只付出「一次乘法 + 一次比较」；方向正常时立刻返回，暮色原公式原样执行。
 */
@Mixin(targets = "twilightforest.entity.boss.KnightPhantom", remap = false)
public abstract class TFKnightKnockbackNaNGuardMixin {

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lensouls$avoidDivideByZero(double strength, double x, double z, CallbackInfo ci) {
        double len2 = x * x + z * z;
        // 正常情况：一次乘法 + 一次比较就放行，暮色原公式一行不改
        if (Double.isFinite(len2) && len2 > 1.0E-8D) {
            return;
        }

        // 退化情况：照原版 LivingEntity.knockback 的做法先随机一个极小方向，避免 0/0
        double rx = (Math.random() - Math.random()) * 0.01D;
        double rz = (Math.random() - Math.random()) * 0.01D;
        double d = Math.sqrt(rx * rx + rz * rz);
        if (!(d > 1.0E-8D) || !Double.isFinite(d)) {
            rx = 0.01D;
            rz = 0.0D;
            d = 0.01D;
        }

        Entity self = (Entity) (Object) this;
        Vec3 half = self.getDeltaMovement().scale(0.5D);
        self.hasImpulse = true;
        self.setDeltaMovement(half.x - rx / d * 0.2D, half.y + 0.2D, half.z - rz / d * 0.2D);
        ci.cancel();
    }
}
