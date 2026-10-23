package com.plumejade.lensouls.client.fx;

import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 屏幕抖动（纯客户端）：直接抖<b>相机角度</b>，而不是在后处理里偏移采样。
 *
 * <h3>为什么改成抖相机</h3>
 * 第一版是在 {@code screen_fx} 后处理里给采样 UV 加偏移。实机反馈"看不出来"——原因很直接：
 * 那个偏移量只有 0.008 UV，而且按 {@code (1-t/T)^2} 衰减，除了头两帧（约 15px）之外每帧只有 2~5px，
 * 在这种幅度下人的眼睛基本感知不到"抖"，只会觉得画面糊了一下。
 * <b>抖相机</b>才是这类"撞击感"的标准做法：整个世界跟着转一点点，HUD 不动，
 * 而且幅度可以直接用"度"来描述，调起来直观。
 * <p>
 * 实现走 NeoForge 的 {@link ViewportEvent.ComputeCameraAngles}（Epic Fight 在 1.21 里也是拿它
 * 换成了自己的 BuildCameraTransform 事件，思路一致）：在 {@code Camera.setup} 里把本帧的
 * pitch/yaw/roll 各加一点衰减振荡。三个轴都用上，抖动才不像"固定频率的正弦摆动"。
 * <p>
 * 两档由 {@link ToughnessImpactScreenFx} 调用：普通削韧一记轻的，打出定身那一记明显更狠。
 */
@OnlyIn(Dist.CLIENT)
public final class CameraShake {

    private static boolean active;
    private static long startNanos;
    private static float durationSeconds;
    private static float ampPitch;
    private static float ampYaw;
    private static float ampRoll;

    private CameraShake() {
    }

    /**
     * 触发一次抖动。
     *
     * @param stun true = 打出定身那一次（幅度更大、持续更久）
     */
    public static void trigger(boolean stun) {
        active = true;
        startNanos = System.nanoTime();
        if (stun) {
            durationSeconds = 0.55F;
            ampPitch = 2.6F;   // 度
            ampYaw = 1.8F;
            ampRoll = 1.2F;
        } else {
            durationSeconds = 0.26F;
            ampPitch = 1.0F;
            ampYaw = 0.7F;
            ampRoll = 0.45F;
        }
    }

    public static void reset() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    /** 由 {@code LenSoulsClient} 注册到游戏总线；每帧（每次相机 setup）调用一次 */
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active) return;

        float t = (System.nanoTime() - startNanos) / 1.0E9F;
        if (t >= durationSeconds) {
            active = false;
            return;
        }

        // 二次衰减：前半段最猛，尾巴收干净，不会"抖完突然停"
        float decay = 1.0F - t / durationSeconds;
        decay *= decay;

        // 每个轴两个频率叠加 → 不规则抖动，看起来像撞击而不是正弦摆动
        float pitch = ampPitch * decay
                * (Mth.sin(t * 63.0F) * 0.70F + Mth.sin(t * 117.0F + 1.7F) * 0.30F);
        float yaw = ampYaw * decay
                * (Mth.sin(t * 71.0F + 0.9F) * 0.70F + Mth.sin(t * 143.0F + 2.3F) * 0.30F);
        float roll = ampRoll * decay
                * (Mth.sin(t * 57.0F + 2.1F) * 0.60F + Mth.sin(t * 101.0F + 0.4F) * 0.40F);

        event.setPitch(event.getPitch() + pitch);
        event.setYaw(event.getYaw() + yaw);
        event.setRoll(event.getRoll() + roll);
    }
}
