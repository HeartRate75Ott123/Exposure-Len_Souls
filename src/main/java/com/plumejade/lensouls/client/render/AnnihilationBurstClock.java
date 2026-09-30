package com.plumejade.lensouls.client.render;

/**
 * 「湮灭激光命中地面那一坨爆点」的<b>共享时钟帧</b>。
 *
 * <h3>为什么要它（LM 2.1.20 实机 jar 逐字节核实）</h3>
 * <ul>
 *   <li>{@code AnnihilationBeamEntity.tick()}：只要光束还插在方块上（{@code blockSide != null}），
 *       就<b>每 tick</b> 调一次 {@code spawnExplosionParticles(5)}；</li>
 *   <li>{@code spawnExplosionParticles} 是 5 次循环，5 个 {@code annihilation_explosion} 粒子
 *       <b>全生成在同一个命中点</b>（后三个参数是速度，而 {@code AnnihilationExplosion} 构造器
 *       把速度丢了 ⇒ 粒子<b>完全不移动</b>）；</li>
 *   <li>该粒子 {@code lifetime = 12}、{@code quadSize = 2.0}，每 tick 用 {@code setSpriteFromAge()}
 *       按<b>自己的年龄</b>挑帧（8 张贴图里只走得到 0~6）。</li>
 * </ul>
 * ⇒ 命中点同时叠着 ~60 个「年龄各不相同」的粒子，画面在统计上是一个<b>恒定混合</b>，
 * 所以看着像定格；光束一消失、生成停下，剩下的粒子才把最后几帧走完（用户原话：
 * 「只在消失时有动画，前边的生命都是静帧」）。
 * <p>
 * 修法：让这些粒子不再按<b>各自年龄</b>挑帧，而是按<b>同一个时钟</b>挑帧 ——
 * 同一时刻整团粒子显示同一帧，于是整坨穹顶会一起在
 * 「实心环 → 虚线环 → 翻滚消散 → 回到实心环」之间循环。
 * <p>
 * 纯客户端类：只被 {@code mixin/client/AnnihilationExplosionClockMixin} 引用。
 */
public final class AnnihilationBurstClock {

    /** LM 的爆点粒子类名（LM 是可选的：解析不到就整条放行） */
    private static final String TARGET_CLASS =
            "net.miauczel.legendary_monsters.Particle.custom.AnnihilationExplosion";

    /** 每帧停留毫秒数：5 帧一轮 = 0.6 秒（用户选定口径） */
    private static final long FRAME_MILLIS = 120L;

    /** 一轮（把 5 帧走完）= 600 ms = 12 tick；与帧时钟同源，所以轮边界正好落在帧 2 上 */
    public static final long ROUND_MILLIS = FRAME_MILLIS * 5L;

    /**
     * 该粒子的真实寿命（LM 构造器里的 {@code bipush 12}，逐字节核实）。
     * 收尾时把它还给「年龄 ≥ 寿命」那道判断，让粒子按原口径判死 —— <b>但只在轮边界上判</b>。
     */
    public static final int BURST_LIFETIME = 12;

    /**
     * 每一「步」对应的<b>伪年龄</b>（不是真的改粒子年龄，只喂给 {@code SpriteSet.get}）。
     * <p>
     * 反解自原版 {@code ParticleEngine.MutableSpriteSet.get(age, lifetime)} 的
     * {@code index = age × (size − 1) ÷ lifetime}（整数除法）：该粒子 {@code lifetime = 12}、
     * {@code annihilation_explosion.json} 有 8 张贴图 ⇒ {@code index(age) = age × 7 ÷ 12}，
     * 于是 age 4/5 → 帧 2、6 → 帧 3、7/8 → 帧 4、9/10 → 帧 5、11 → 帧 6。
     * 跳过帧 0/1（一个亮点、一个星芒）：同步后整团一起换帧，落在那两帧上会让穹顶几乎看不见。
     */
    private static final int[] AGE_BY_STEP = {4, 6, 7, 9, 11};

    private static Class<?> targetClass;
    private static boolean resolved;

    private AnnihilationBurstClock() {
    }

    /** 只认 LM 的那个爆点粒子；LM 没装时永远 false（帧号原样放行） */
    public static boolean isBurst(Object particle) {
        if (particle == null) {
            return false;
        }
        Class<?> type = resolve();
        return type != null && type.isInstance(particle);
    }

    private static Class<?> resolve() {
        if (!resolved) {
            resolved = true;
            try {
                targetClass = Class.forName(TARGET_CLASS, false,
                        AnnihilationBurstClock.class.getClassLoader());
            } catch (Throwable ignored) {
                targetClass = null;
            }
        }
        return targetClass;
    }

    /** 同一时刻所有爆点粒子拿到同一个伪年龄 ⇒ 整团一起换帧 */
    public static int sharedClockAge() {
        int step = (int) ((System.currentTimeMillis() / FRAME_MILLIS) % AGE_BY_STEP.length);
        if (step < 0) {
            step = 0;
        }
        return AGE_BY_STEP[step];
    }

    /** 当前是第几「轮」爆点动画（轮 = 0.6 秒）。收尾对齐就靠它。 */
    public static long currentRound() {
        return System.currentTimeMillis() / ROUND_MILLIS;
    }
}
