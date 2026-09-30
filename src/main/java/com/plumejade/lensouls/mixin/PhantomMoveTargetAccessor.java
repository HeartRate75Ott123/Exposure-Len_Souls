package com.plumejade.lensouls.mixin;

import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 写入口：幻翼的飞行目标点。
 * <p>
 * <b>为什么必须走这个字段</b>（{@code javap -c} 在实机 jar 上逐条核实）：
 * {@code Phantom} 的构造器装的是它<b>自己专属</b>的三个控制器，而不是 {@code FlyingMob} 默认那套——
 * <pre>
 *   this.moveControl = new Phantom$PhantomMoveControl(this, this);   // extends MoveControl
 *   this.lookControl = new Phantom$PhantomLookControl(this, this);   // tick() 是空实现
 *   createBodyControl() → new Phantom$PhantomBodyRotationControl(this, this);
 * </pre>
 * 其中 {@code PhantomMoveControl.tick()} 是<b>唯一</b>会设置 {@code setYRot} / {@code setXRot} /
 * {@code setDeltaMovement} 的地方：它读 {@code moveTargetPoint}，用
 * {@code atan2(dz, dx)} 求偏航（每 tick 只转 4°，所以幻翼是「盘旋着拐」而不是立刻对头）、
 * 用 {@code atan2(dy, 水平距离)} 求俯仰，再以 {@code speed} 合成目标速度并
 * {@code delta += (目标 − delta) × 0.2} 平滑趋近。{@code speed} 在构造器里初始化为 {@code 0.1f}，
 * 航向误差 &lt; 3° 时向 {@code 1.8f} 加速（俯冲），否则限向 {@code 0.2f}。
 * <p>
 * ⇒ <b>自己写 {@code setDeltaMovement} 会绕过唯一设置朝向的地方</b>，结果就是「偏航俯仰永远停在
 * 生成时的值、delta 却指向目标」——实机表现正是「固定朝向、身子朝上」。正确做法是只喂目标点，
 * 让原版控制器自己算位移与姿态。
 * <p>
 * 用 accessor 而不是 {@code @Shadow}：{@code Phantom} 不是本模组的类，且 {@code moveTargetPoint}
 * 是私有字段；本仓库已有 {@link EntityInvulnerableTimeAccessor} 的同类先例。
 */
@Mixin(Phantom.class)
public interface PhantomMoveTargetAccessor {

    @Accessor("moveTargetPoint")
    void lensouls$setMoveTargetPoint(Vec3 value);
}
