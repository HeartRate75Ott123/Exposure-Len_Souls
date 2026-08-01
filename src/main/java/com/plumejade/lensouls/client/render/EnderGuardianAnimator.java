package com.plumejade.lensouls.client.render;

import com.github.L_Ender.cataclysm.client.model.entity.Ender_Guardian_Model;
import com.github.L_Ender.lionfishapi.client.model.Animations.ModelAnimator;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.github.L_Ender.lionfishapi.server.animation.Animation;
import com.plumejade.lensouls.entity.BossPhantomEntity;

/**
 * Ender Guardian 模型动画器——模仿 GUARDIAN_BLACKHOLE：
 * 下蹲蓄力（双手举起）→ 捶地 → 地面虚空漩涡。
 * 核心动作为双手砸地产生地面环形力场。
 */
public class EnderGuardianAnimator {

    private final Ender_Guardian_Model model;
    private final ModelAnimator animator;

    private final AdvancedModelBox root, lowerbody, upperbody;
    private final AdvancedModelBox right_arm, right_arm2, right_fist;
    private final AdvancedModelBox left_arm, left_arm2, left_fist;
    private final AdvancedModelBox helmet, head, armor;
    private final AdvancedModelBox right_leg, left_leg;

    public EnderGuardianAnimator(Ender_Guardian_Model model) {
        this.model = model;
        this.animator = ModelAnimator.create();
        this.root = getField(model, "root");
        this.lowerbody = getField(model, "lowerbody");
        this.upperbody = getField(model, "upperbody");
        this.right_arm = getField(model, "right_arm");
        this.right_arm2 = getField(model, "right_arm2");
        this.right_fist = getField(model, "right_fist");
        this.left_arm = getField(model, "left_arm");
        this.left_arm2 = getField(model, "left_arm2");
        this.left_fist = getField(model, "left_fist");
        this.helmet = getField(model, "helmet");
        this.head = getField(model, "head");
        this.armor = getField(model, "armor");
        this.right_leg = getField(model, "right_leg");
        this.left_leg = getField(model, "left_leg");
    }

    public void animate(BossPhantomEntity entity) {
        model.resetToDefaultPose();
        animator.update(LionfishAnimationAdapter.adapt(entity));

        Animation anim = (Animation) entity.getAnimation();
        if (anim == null) return;
        animator.setAnimation(anim);

        playKeyframes();
    }

    private void playKeyframes() {
        // ===== Phase 1: 深蹲蓄力（12 ticks） =====
        // 右腿弯曲 90°（膝盖朝前，小腿垂直），左腿后伸 60°（向后绷直）
        animator.startKeyframe(12);
        animator.rotate(root, 0, rad(15F), 0);
        animator.rotate(lowerbody, 0, rad(2.5F), 0);
        animator.rotate(upperbody, rad(-15F), rad(15F), rad(-10F));
        animator.move(right_leg, 0, -5, 6);                         // 右膝弯曲 90°（大腿上提，脚在体下）
        animator.rotate(left_leg, rad(60F), 0, 0);                  // 左腿向后伸直 60°
        animator.move(left_leg, 0, -3, -10);                        // 左脚后滑
        animator.rotate(right_arm, rad(-90F), rad(-5F), rad(45F));
        animator.rotate(left_arm, rad(-85F), 0, rad(-45F));
        animator.rotate(right_arm2, rad(-100F), 0, 0);
        animator.rotate(left_arm2, rad(-100F), 0, 0);
        animator.move(right_arm2, 0, 6, 0);
        animator.move(left_arm2, 0, 6, 3);
        animator.rotate(helmet, rad(-8F), rad(-25F), 0);
        animator.endKeyframe();
        animator.setStaticKeyframe(10);

        // ===== Phase 2: 双拳砸地（3 ticks） =====
        animator.startKeyframe(3);
        animator.rotate(root, 0, rad(15F), 0);
        animator.rotate(lowerbody, 0, rad(5F), 0);
        animator.rotate(upperbody, rad(25F), rad(15F), rad(10F));
        animator.move(right_leg, 0, -5, 6);
        animator.rotate(left_leg, rad(50F), 0, 0);                  // 左腿稍收但仍后伸
        animator.move(left_leg, 0, 0, -8);
        animator.rotate(right_arm, rad(55F), rad(-5F), rad(55F));
        animator.rotate(left_arm, rad(-15F), 0, rad(-45F));
        animator.rotate(right_arm2, rad(-100F), 0, 0);
        animator.rotate(left_arm2, rad(-100F), 0, 0);
        animator.move(right_arm2, 0, 6, 0);
        animator.endKeyframe();
        animator.setStaticKeyframe(10);

        // ===== Phase 3: 复位（10 ticks） =====
        animator.resetKeyframe(10);
    }

    private static float rad(float deg) { return (float) Math.toRadians(deg); }

    private static AdvancedModelBox getField(Ender_Guardian_Model model, String name) {
        try {
            java.lang.reflect.Field f = Ender_Guardian_Model.class.getDeclaredField(name);
            f.setAccessible(true);
            return (AdvancedModelBox) f.get(model);
        } catch (Exception e) {
            com.plumejade.lensouls.LenSouls.LOGGER.warn("[EnderGuardianAnimator] 无法获取字段: {}", name);
            return null;
        }
    }
}
