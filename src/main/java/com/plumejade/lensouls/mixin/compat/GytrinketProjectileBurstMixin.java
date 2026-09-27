package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.util.PhotoProjMarker;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * gytrinket「弹射物点射（连击复制）」兼容：照片弹幕不参与连击复制。
 * <p>
 * 该模组的 {@code ProjectileBurstManager.onEntityJoinLevel} 监听 {@code EntityJoinLevelEvent}：
 * 只要「归属玩家的弹射物」进入世界、且玩家 {@code combo} 属性 &gt; 0，就快照该弹幕并在随后每刻
 * 按视线复制一份，复制份数 = 连击段数。我们的照片弹幕全部以玩家为 owner，于是在装了该模组的
 * 整合包里，一次挥砍触发的水波/箭雨/火球会被成倍复制 → 屏幕被弹幕淹没、帧率暴跌。
 * <p>
 * 处理方式：带 {@code lensouls:photo_proj} 标记的弹幕直接不进它的复制流程（HEAD 取消）。
 * 刻意<b>不</b>给它打上对方的 {@code ProjectileBurstCopy} 标记来"骗过"判定——那个标记还会让
 * 对方在命中时清目标无敌帧、并在碰撞后 1 刻把弹幕 discard，等于换一种方式改我们的弹幕行为。
 * <p>
 * 目标类不在编译期依赖里（该模组是可选兼容），因此用 {@code targets} 字符串 +
 * {@code remap = false}；配置 {@code required=false}，模组未安装时整条 mixin 被跳过。
 */
@Mixin(targets = "com.gytrinket.gytrinket.core.attack_mode.burst_fire.ProjectileBurstManager", remap = false)
public class GytrinketProjectileBurstMixin {

    @Inject(method = "onEntityJoinLevel", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void lensouls$skipPhotoBarrage(EntityJoinLevelEvent event, CallbackInfo ci) {
        if (event.getEntity() instanceof Projectile projectile && PhotoProjMarker.isBarrage(projectile)) {
            ci.cancel();
        }
    }
}
