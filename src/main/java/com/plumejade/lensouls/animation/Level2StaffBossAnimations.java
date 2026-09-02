package com.plumejade.lensouls.animation;

import net.minecraft.util.RandomSource;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * N公司2级员工动画集合。
 * <p>
 * 对应 {@code assets/lensouls/animations/entity/level2_staff_boss.animation.json} 中的动画键：
 * walk / hit1 / hit2 / hit3 / spike_hit / spike_hit2 / camera_shoot。
 */
public final class Level2StaffBossAnimations {

    private Level2StaffBossAnimations() {}

    /** 行走（循环） */
    public static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");

    /** 攻击动画键名：命中时随机取一个，经 triggerAnim 触发 */
    public static final String[] ATTACK_NAMES = {
            "hit1", "hit2", "hit3", "spike_hit", "spike_hit2", "camera_shoot"
    };

    /** 近战变体：动画键 + 伤害/音效判定窗口（动画秒，闭区间） + 动画总时长（动画秒） */
    public record MeleeAnim(String name, float startSec, float endSec, float lengthSec) {}

    public static final MeleeAnim[] MELEE_ANIMS = {
            new MeleeAnim("hit1", 0.33f, 0.50f, 1.0f),
            new MeleeAnim("hit2", 0.38f, 0.63f, 1.0f),
            new MeleeAnim("hit3", 0.42f, 0.67f, 1.25f)
    };

    /** spike 变体：动画键 + 远程/音效释放时刻（动画秒） + 总时长（动画秒） */
    public record SpikeAnim(String name, float releaseSec, float lengthSec) {}

    public static final SpikeAnim[] SPIKE_ANIMS = {
            new SpikeAnim("spike_hit", 0.75f, 1.5f),
            new SpikeAnim("spike_hit2", 0.90f, 1.5f)
    };

    /** 相机射击（远程骚扰），总时长（动画秒） */
    public static final String CAMERA_SHOOT = "camera_shoot";
    public static final float CAMERA_SHOOT_LENGTH_SEC = 2.0f;

    /** 随机取一个近战变体 */
    public static MeleeAnim randomMelee(RandomSource random) {
        return MELEE_ANIMS[random.nextInt(MELEE_ANIMS.length)];
    }

    /** 随机取一个 spike 变体 */
    public static SpikeAnim randomSpike(RandomSource random) {
        return SPIKE_ANIMS[random.nextInt(SPIKE_ANIMS.length)];
    }
}
