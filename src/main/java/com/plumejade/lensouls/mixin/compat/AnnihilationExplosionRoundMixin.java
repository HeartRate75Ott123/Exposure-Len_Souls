package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.client.render.AnnihilationBurstClock;
import net.miauczel.legendary_monsters.Particle.custom.AnnihilationExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 湮灭激光命中地面那一坨爆点：<b>收尾必须落在整轮边界上</b>（「至少等这轮动画放完再结束生命」）。
 *
 * <h3>目标</h3>
 * 爆点动画一轮 = 5 帧 × 120 ms = 600 ms（见 {@link AnnihilationBurstClock}）。
 * 原本粒子出生后活满自己的 12 tick（= 恰好一轮）就死，出生时刻又是每 tick 一个 ⇒
 * 光束停止补粒子时，最后几批会在<b>一轮中途</b>逐个消失，看起来就是「动画被截断」。
 *
 * <h3>做法（只动 {@code tick()} 里那次 {@code lifetime} 读取）</h3>
 * <pre>
 * tick(): age++;  if (age >= lifetime) remove(); else setSpriteFromAge(sprites);
 * </pre>
 * 把那次 {@code lifetime} 读取换成哨兵：
 * <ul>
 *   <li>还在粒子出生那一轮 ⇒ 返回极大值：不许死（于是 {@code setSpriteFromAge} 照常每 tick 执行，
 *       帧继续跟着共享时钟走）；</li>
 *   <li>已经跨过轮边界 ⇒ 返回<b>真实寿命 12</b>：年龄 ≥ 12 的粒子这时才判死。</li>
 * </ul>
 * 合起来：粒子寿命落在 <b>12~24 tick</b>、且**只会在轮边界上消失** ⇒
 * 光束停止补粒子后，那一坨会把当前这一整轮（含最后的消散帧）放完，才在边界上一起消失；
 * 密度也不会出现「刚跨轮就空掉」的锯齿（上一轮的粒子还活着）。
 *
 * <h3>两个必须记住的字节码事实</h3>
 * <ul>
 *   <li><b>常量池 owner 是 LM 自己的类</b>：{@code #7 = Fieldref …AnnihilationExplosion.lifetime:I}、
 *       {@code #69 = Methodref …AnnihilationExplosion.remove:()V} —— LM 编译时把继承来的
 *       {@code Particle.lifetime}/{@code remove} 记成了自己的 owner。所以 {@code @At} 的 target
 *       <b>必须照抄</b>{@code Lnet/miauczel/legendary_monsters/Particle/custom/AnnihilationExplosion;lifetime:I}，
 *       写 {@code Particle;lifetime:I} 会一条都匹配不上（{@code require = 1} 会当场报错）。</li>
 *   <li>这只改 {@code tick()} 里的读取：vanilla {@code TextureSheetParticle.setSpriteFromAge}
 *       自己读的是真 {@code lifetime}（12）⇒ 帧映射 {@code age × 7 ÷ 12}
 *       （{@link AnnihilationBurstClock} 的伪年龄查表）完全不受影响。</li>
 * </ul>
 */
@Mixin(targets = "net.miauczel.legendary_monsters.Particle.custom.AnnihilationExplosion", remap = false)
public class AnnihilationExplosionRoundMixin {

    /** 本粒子出生时落在第几「轮」爆点动画 */
    @Unique
    private long lensouls$spawnRound;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void lensouls$rememberSpawnRound(CallbackInfo ci) {
        this.lensouls$spawnRound = AnnihilationBurstClock.currentRound();
    }

    @Redirect(method = "tick", require = 1, at = @At(value = "GETFIELD",
            target = "Lnet/miauczel/legendary_monsters/Particle/custom/AnnihilationExplosion;lifetime:I"))
    private int lensouls$holdUntilRoundEnd(AnnihilationExplosion self) {
        if (AnnihilationBurstClock.currentRound() <= this.lensouls$spawnRound) {
            // 还在出生那一轮：不许死（继续每 tick 更新 sprite）
            return Integer.MAX_VALUE - 1;
        }
        // 已跨过轮边界：交还真实寿命，年龄够了的粒子这时才一起消失
        return AnnihilationBurstClock.BURST_LIFETIME;
    }
}
