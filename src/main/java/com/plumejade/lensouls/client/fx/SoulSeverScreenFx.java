package com.plumejade.lensouls.client.fx;

import net.minecraft.world.phys.Vec3;

/**
 * 夺魂索命的绿色魂雾屏幕特效：暗角泛绿（呼吸脉冲）+ 从目标屏幕位置扩散的绿环。
 * <p>
 * 目标位置由 S2C 包给出（目标可能立刻死亡，不能依赖实体查找）。
 * <p>
 * <b>环心取「身体中心」而不是脚下</b>：包里的坐标是 {@code entity.position()}，也就是<b>脚底</b>，
 * 而世界那边的魂丝/魂柱/上升环全在胸口往上（{@code feet + height*0.42 … feet + height*1.5}）。
 * 直接用脚底投影，屏幕绿环会挂在目标脚下方 1~3 格处，看起来像"环飘在别的地方/没对上"
 * ——实机反馈就是"错位"。这里按身高抬到一半高度再投影。
 */
public final class SoulSeverScreenFx implements ScreenFxEffect {

    private static final float DURATION = 0.62F;

    private final Vec3 targetPos;
    /** 目标身高（用于把环心从脚底抬到身体中心） */
    private final float height;
    private final boolean success;
    private float t;

    public SoulSeverScreenFx(Vec3 targetPos, float height, boolean success) {
        this.targetPos = targetPos;
        this.height = Math.max(0.9F, height);
        this.success = success;
    }

    @Override
    public boolean update(float dt) {
        t += dt;
        return t < DURATION;
    }

    @Override
    public void apply(ScreenFxManager.State s) {
        float u = Math.min(1.0F, t / DURATION);
        // 快升慢落
        float pulse = (float) Math.sin(Math.min(1.0F, u * 1.15F) * Math.PI);

        // 成功 = 绿色魂飞魄散；失败 = 紫色（能力被挡/目标太弱）
        float strength = success ? 0.30F : 0.22F;
        s.addTint(success ? 0.24F : 0.62F, success ? 1.00F : 0.28F,
                success ? 0.52F : 1.00F, strength * pulse);

        Vec3 chest = targetPos.add(0.0, height * 0.5, 0.0);
        float[] p = ScreenProjector.project(chest);
        if (p[2] > 0.5F) {
            s.rippleX = p[0];
            s.rippleY = p[1];
            s.rippleRadius = 0.05F + u * 0.42F;
            s.rippleStrength = (1.0F - u) * (1.0F - u) * (success ? 0.55F : 0.30F);
            s.rippleR = success ? 0.35F : 0.72F;
            s.rippleG = success ? 1.00F : 0.35F;
            s.rippleB = success ? 0.60F : 1.00F;
        }

        // 成功瞬间一层极短的绿闪
        if (u < 0.16F) {
            float f = (1.0F - u / 0.16F) * (success ? 0.20F : 0.16F);
            s.flashR = success ? 0.30F : 0.65F;
            s.flashG = success ? 0.95F : 0.30F;
            s.flashB = success ? 0.55F : 1.00F;
            s.flash = Math.max(s.flash, f);
        }
    }
}
