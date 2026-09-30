package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.client.render.AnnihilationBurstClock;
import net.minecraft.client.particle.TextureSheetParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 湮灭激光命中地面那一坨「爆点」不再定格：把粒子挑帧用的<b>年龄</b>换成<b>共享时钟</b>。
 *
 * <h3>为什么（LM 2.1.20 实机 jar 逐字节核实，详见 {@link AnnihilationBurstClock}）</h3>
 * 光束只要还插在方块上，就每 tick 在命中点丢 5 个 {@code annihilation_explosion} 粒子，
 * 粒子自己不移动、12 tick 生命里按<b>自己的年龄</b>挑帧 ⇒ 命中点同时叠着 ~60 个不同年龄的粒子，
 * 统计上是个恒定混合（看着定格）；停生成后才把最后几帧走完。
 *
 * <h3>落点</h3>
 * 原版 {@code TextureSheetParticle.setSpriteFromAge(SpriteSet)} 的方法体（已 javap 核实）就是：
 * <pre>if (!this.removed) this.setSprite(sprites.get(this.age, this.lifetime));</pre>
 * 这里只改 {@code SpriteSet.get(int age, int lifetime)} 的 <b>index 0（age）</b>：
 * 我们的爆点粒子返回共享时钟算出的伪年龄（同一时刻全团同一帧），其余粒子原样放行。
 * 粒子的真实 {@code age} 不动 ⇒ 生命周期/移除时机/数量/位置/伤害全不受影响。
 *
 * <p>混进原版类（不是 LM 的类）是为了不必碰 protected 的 {@code setSprite}：
 * 判定按<b>类名</b>惰性解析（{@link AnnihilationBurstClock#isBurst}），LM 缺席时永远 false。
 * 因此这条 mixin 放在主配置 {@code lensouls.mixins.json} 的 client 数组里，
 * 与其它原版客户端 mixin 同处。
 */
@Mixin(TextureSheetParticle.class)
public class AnnihilationExplosionClockMixin {

    @ModifyArg(method = "setSpriteFromAge", require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/SpriteSet;get(II)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"),
            index = 0)
    private int lensouls$sharedClockAge(int age) {
        return AnnihilationBurstClock.isBurst(this) ? AnnihilationBurstClock.sharedClockAge() : age;
    }
}
