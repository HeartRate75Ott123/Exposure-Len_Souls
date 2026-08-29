package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.boss.BossGuardHelper;
import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 定身暂停实体刻：破刹（韧性清空）与时间定格期间，完全跳过 {@code LivingEntity.tick}。
 * <p>
 * 跳过内容：移动/重力/AI/持续效果结算/燃烧/溺水/tickCount 等全部停止；
 * 客户端同样跳过（服务端不发位置包，客户端也不自驱动，无拉扯瞬移）。
 * <p>
 * 死亡放行：{@code tickDeath}（掉落/移除/死亡动画）在 tick 内执行，
 * 若暂停将导致尸体永久残留。
 * <p>
 * 渲染插值基准同步：渲染器每帧按 {@code lerp(partialTick, O值, 当前值)} 插值
 * （如 {@code LivingEntityRenderer.setupRotations}、暮色 HydraNeckRenderer）。
 * tick 暂停后 O 值（上一刻值）冻结在暂停瞬间，与当前值存在差值，
 * partialTick 随帧率波动 → 姿态每帧来回摆动（抽搐）。
 * cancel 前把 O 值同步为当前值，差值归零 → 渲染完全静止。
 */
@Mixin(LivingEntity.class)
public class BossStunTickMixin {

    @Shadow
    protected float animStep;

    @Shadow
    protected float animStepO;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void lensouls$pauseTickOnStun(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isDeadOrDying()) return; // 死亡放行：tickDeath 需要执行
        // block_factorys_bosses 的 boss 不在此整段取消：其转阶段逻辑写在 tick 内（依赖血量），
        // 整段取消会卡阶段；改由 BlockFactorysBossStunMixin 选择性压制移动/攻击，tick 仍跑。
        if (StunPauseHelper.isStunPaused(self) && !BossGuardHelper.isBlockFactorysBoss(self)) {
            self.yRotO = self.getYRot();
            self.xRotO = self.getXRot();
            self.xOld = self.getX();
            self.yOld = self.getY();
            self.zOld = self.getZ();
            self.yBodyRotO = self.yBodyRot;
            self.yHeadRotO = self.yHeadRot;
            this.animStepO = this.animStep;
            ci.cancel();
        }
    }
}