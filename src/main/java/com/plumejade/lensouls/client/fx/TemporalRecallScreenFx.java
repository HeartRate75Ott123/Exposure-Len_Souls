package com.plumejade.lensouls.client.fx;

/**
 * 时空回溯的「破碎穿梭」屏幕特效（只作用于触发者的第一人称视角）。
 * <p>
 * 时序（总寿命约 1.4s，两端都有明确淡入淡出）：
 * <ol>
 *   <li><b>CHARGE 0.42s</b>：镜面从中心裂开（裂纹 + 逐片错位）→ 径向塌缩 + 色散，
 *       全部强度由 {@code smoothstep(0, 0.18, t)} 淡入，末尾白闪；</li>
 *   <li><b>ARRIVE 0.95s</b>：裂片继续错位并<b>掉落</b>（shard 强度先保持、后半段才淡出），
 *       拉伸在这段时间里<b>慢慢淡掉</b>，中心同时扩散一圈彩虹冲击环，最后整体归零。</li>
 * </ol>
 * 刻意把"速度线"压得很低——它廉价感的来源，张力改由裂纹色散与镜片错位承担。
 */
public final class TemporalRecallScreenFx implements ScreenFxEffect {

    private static final float CHARGE_SECONDS = 0.50F;
    private static final float ARRIVE_SECONDS = 1.55F;
    /** 收不到 ARRIVE 时的兜底寿命 */
    private static final float ORPHAN_SECONDS = 2.10F;

    private static TemporalRecallScreenFx current;

    private float t;
    private boolean arriving;
    private float arriveT;

    private TemporalRecallScreenFx() {
    }

    /** 前摇开始 */
    public static void charge() {
        current = new TemporalRecallScreenFx();
        ScreenFxManager.push(current);
    }

    /** 落点（传送已完成） */
    public static void arrive() {
        if (current != null && !current.arriving) {
            current.arriving = true;
            current.arriveT = 0.0F;
        } else {
            current = new TemporalRecallScreenFx();
            current.arriving = true;
            ScreenFxManager.push(current);
        }
    }

    public static void reset() {
        current = null;
    }

    @Override
    public boolean update(float dt) {
        if (arriving) {
            arriveT += dt;
            return arriveT < ARRIVE_SECONDS;
        }
        t += dt;
        return t < ORPHAN_SECONDS;
    }

    @Override
    public void apply(ScreenFxManager.State s) {
        s.flashR = 0.96F;
        s.flashG = 0.99F;
        s.flashB = 1.00F;
        s.warpCenterX = 0.5F;
        s.warpCenterY = 0.5F;
        s.rippleR = 0.55F;
        s.rippleG = 0.92F;
        s.rippleB = 1.00F;

        if (!arriving) {
            // ── CHARGE：裂开 → 塌缩 → 末段白到全白（把传送那一帧彻底盖住） ──
            float k = Math.min(1.0F, t / CHARGE_SECONDS);
            float easeIn = smoothstep(0.0F, 0.16F, t);
            float ease = k * k;
            // 没等到 ARRIVE 时的兜底淡出（不再留突兀定格）
            float tail = t > CHARGE_SECONDS
                    ? Math.max(0.0F, 1.0F - smoothstep(CHARGE_SECONDS, CHARGE_SECONDS + 0.40F, t))
                    : 1.0F;

            s.warp = 0.55F * ease * easeIn * tail;
            s.shard = 0.95F * Math.min(1.0F, 0.35F + 0.65F * ease) * easeIn * tail;
            s.shardPhase = ease;                  // 前摇内就把镜面完全裂开（白闪前能看清）
            s.shardRestore = 0.0F;
            s.prism = 1.0F;
            s.chroma = 0.85F * ease * easeIn * tail;
            s.streak = 0.26F * ease * easeIn * tail;
            s.flash = smoothstep(0.70F, 1.0F, k) * tail;   // 末段拉到全白
            return;
        }

        // ── ARRIVE：从全白揭开 → 碎块继续飞出并溶解 → 拉伸最后慢慢收 ──
        // 各通道各有自己的收尾窗口，保证 u=1 全部为 0（不会硬切回普通画面）。
        float u = Math.min(1.0F, arriveT / ARRIVE_SECONDS);

        // 1) 白闪：传送那一帧是全白，0.22 内揭开新场景
        s.flash = 1.0F - smoothstep(0.0F, 0.22F, u);

        // 2) 碎块：先继续向外飘一点（0~0.30），随后"滑回原位"——
        //    错位偏移与裂缝由 restore 一起收拢（这才是"错位平滑撤走"），
        //    碎块彻底归位之后，裂纹强度才淡掉（0.55~0.95）。
        // 碎块节奏（重点：先"碎开 + 下坠"，最后才收回）：
        //   0.00~0.45  phase 1.0 → 1.45：碎块继续向外错开并整体下坠
        //   0.45~0.90  restore 0 → 1  ：错位/裂缝平滑收回原位
        //   0.62~0.96  shard  1 → 0   ：归位后残余裂纹淡掉
        float drift = smoothstep(0.0F, 0.45F, u);
        s.shardPhase = 1.0F + 0.45F * drift;
        s.shardRestore = smoothstep(0.45F, 0.90F, u);
        s.shard = 0.95F * (1.0F - smoothstep(0.62F, 0.96F, u));
        s.prism = (1.0F - u * 0.70F);

        // 3) 拉伸/色散：临界阻尼回收（单调、无过冲），后段再叠一层收尾窗
        float omega = 3.2F;
        float spring = (1.0F + omega * u) * (float) Math.exp(-omega * u);
        s.warp = 0.55F * spring * (1.0F - smoothstep(0.60F, 1.00F, u));
        s.chroma = 0.85F * spring * (1.0F - smoothstep(0.45F, 0.95F, u));
        s.streak = 0.26F * spring * (1.0F - smoothstep(0.35F, 0.85F, u));

        // 4) 冲击环：只在开头（把注意力从"世界换了"移开）
        s.rippleRadius = 0.05F + u * 1.20F;
        s.rippleStrength = 0.55F * (1.0F - smoothstep(0.0F, 0.40F, u));
    }

    private static float smoothstep(float e0, float e1, float x) {
        float v = Math.max(0.0F, Math.min(1.0F, (x - e0) / (e1 - e0)));
        return v * v * (3.0F - 2.0F * v);
    }
}
