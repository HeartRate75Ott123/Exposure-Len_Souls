package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.entity.SwarmPhantomFade;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给原版 {@link Phantom} 追加两个同步数据槽，用于「本模组召唤的幻翼」标记与渐隐不透明度。
 *
 * <p>{@code Entity} 的构造器里就会调 {@code this.defineSynchedData(builder)}（虚方法），
 * 所以注入 {@code Phantom.defineSynchedData} 的 TAIL 能保证每个幻翼实例都注册到这两个槽。
 *
 * <p><b>为什么不直接改原版行为</b>：这两个槽只被本模组写入（只有
 * {@link SwarmPhantomFade#mark} 会置 true），原版与其它模组的幻翼永远读到
 * {@code false} / {@code 1.0F}，渲染管线也不会被替换——零副作用。
 */
@Mixin(Phantom.class)
public abstract class PhantomFadeSyncMixin {

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void lensouls$defineSwarmFadeSlots(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(SwarmPhantomFade.SWARM, false);
        builder.define(SwarmPhantomFade.ALPHA, SwarmPhantomFade.OPAQUE);
    }
}
