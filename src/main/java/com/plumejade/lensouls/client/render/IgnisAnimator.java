package com.plumejade.lensouls.client.render;

import com.github.L_Ender.cataclysm.client.model.entity.Ignis_Model;
import com.github.L_Ender.lionfishapi.client.model.Animations.ModelAnimator;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.github.L_Ender.lionfishapi.server.animation.Animation;
import com.plumejade.lensouls.entity.BossPhantomEntity;

/**
 * Ignis 模型动画器——双手握剑插地爆火焰球（模仿 REINFORCED_SMASH）。
 * <p>
 * 动画总时长：8起身+10插地+4横扫+20蓄力火球+10复位 = 52 ticks。
 */
public class IgnisAnimator {

    private final Ignis_Model model;
    private final ModelAnimator animator;

    private final AdvancedModelBox root, upperbody, right_arm_joint, right_arm, right_hand, right_fist;
    private final AdvancedModelBox head, left_arm_joint, left_arm, left_hand, sword, bodycore;

    public IgnisAnimator(Ignis_Model model) {
        this.model = model;
        this.animator = ModelAnimator.create();
        this.root = getField(model, "root");
        this.upperbody = getField(model, "upperbody");
        this.right_arm_joint = getField(model, "right_arm_joint");
        this.right_arm = getField(model, "right_arm");
        this.right_hand = getField(model, "right_hand");
        this.right_fist = getField(model, "right_fist");
        this.head = getField(model, "head");
        this.left_arm_joint = getField(model, "left_arm_joint");
        this.left_arm = getField(model, "left_arm");
        this.left_hand = getField(model, "left_hand");
        this.sword = getField(model, "sword");
        this.bodycore = getField(model, "bodycore");
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
        // ===== Phase 1: 举剑起身蓄力（8 ticks） =====
        animator.startKeyframe(8);
        animator.rotate(root, 0, rad(35F), 0);
        animator.rotate(upperbody, rad(-10F), rad(-10F), rad(-22.5F));
        animator.rotate(right_arm_joint, rad(-27.5F), rad(35F), rad(20F));
        animator.rotate(right_arm, 0, 0, 0);
        animator.rotate(right_hand, rad(-20F), rad(-25F), rad(22.5F));
        animator.rotate(right_fist, rad(12.5F), 0, 0);
        animator.rotate(sword, 0, 0, 0);
        animator.rotate(head, rad(7.5F), 0, rad(20F));
        animator.rotate(left_arm_joint, rad(-20F), rad(-42.5F), rad(-17.5F));
        animator.rotate(left_hand, rad(-17.5F), rad(-15F), rad(-15F));
        animator.endKeyframe();

        // ===== Phase 2: 插地震地（10 ticks） =====
        // 身体前压，右臂向下插，剑尖朝地
        animator.startKeyframe(10);
        animator.rotate(root, rad(-2.5F), rad(12.5F), 0);
        animator.rotate(upperbody, rad(-22.5F), rad(7.5F), rad(-5F));
        animator.rotate(right_arm_joint, rad(-67.5F), rad(2.5F), rad(35F));
        animator.rotate(right_arm, rad(-30F), 0, 0);
        animator.rotate(right_hand, rad(-50F), rad(20F), rad(-45F));
        animator.rotate(right_fist, rad(-22.5F), rad(-27.5F), 0);
        animator.rotate(sword, rad(180F), 0, 0);
        animator.rotate(head, rad(2.5F), rad(2.5F), rad(2.5F));
        animator.rotate(left_arm_joint, rad(60F), rad(-55F), rad(-2.5F));
        animator.rotate(left_hand, rad(-17.5F), rad(-15F), rad(-15F));
        animator.endKeyframe();
        animator.setStaticKeyframe(10);  // 插地冲击保持（火球蓄力）

        // ===== Phase 3: 火球爆发回摆（6 ticks） =====
        animator.startKeyframe(6);
        animator.rotate(root, 0, rad(-35F), 0);
        animator.rotate(upperbody, 0, rad(-15F), rad(-12F));
        animator.rotate(right_arm_joint, rad(-50F), rad(-40F), rad(60F));
        animator.rotate(right_arm, rad(-10F), rad(10F), 0);
        animator.rotate(right_hand, rad(-25F), rad(70F), rad(15F));
        animator.rotate(right_fist, rad(-20F), rad(25F), 0);
        animator.rotate(sword, rad(180F), 0, 0);
        animator.rotate(head, rad(5F), rad(40F), rad(-10F));
        animator.rotate(left_arm_joint, rad(60F), rad(-60F), rad(-20F));
        animator.rotate(left_hand, rad(-30F), rad(-10F), rad(-10F));
        animator.endKeyframe();
        animator.setStaticKeyframe(8);  // 维持结束姿势

        // ===== Phase 4: 复位（10 ticks） =====
        animator.resetKeyframe(10);
    }

    private static float rad(float deg) { return (float) Math.toRadians(deg); }

    private static AdvancedModelBox getField(Ignis_Model model, String name) {
        try {
            java.lang.reflect.Field f = Ignis_Model.class.getDeclaredField(name);
            f.setAccessible(true);
            return (AdvancedModelBox) f.get(model);
        } catch (Exception e) {
            com.plumejade.lensouls.LenSouls.LOGGER.warn("[IgnisAnimator] 无法获取字段: {}", name);
            return null;
        }
    }
}
