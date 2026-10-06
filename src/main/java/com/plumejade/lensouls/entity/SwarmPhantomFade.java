package com.plumejade.lensouls.entity;

import net.minecraft.world.entity.monster.Phantom;

/**
 * 幻术师照片召唤的「幻影幻翼」的存续时长与渐隐不透明度。
 *
 * <p><b>为什么不写进原版幻翼的同步数据槽（1.6.15 起废弃旧做法）</b>：
 * 旧实现用 {@code SynchedEntityData.defineId(Phantom.class, …)} 往<b>原版</b> {@code Phantom} 上挂
 * 两个槽（Boolean 标记 + Float 不透明度）。{@code defineId} 的 id 是<b>按「谁先初始化」现场分配</b>的，
 * 而本类在两端被首次触碰的时机不同（客户端是渲染 {@code alphaOrNull} 时，服务端是拍照处理器加载读常量时），
 * 于是同一个槽在两端的 id 不一致 ⇒ 客户端收到「id=17 的 Integer」却发现本地 17 是 Boolean，
 * 直接抛 {@code Invalid entity data item type for field … on entity Phantom} 把玩家踢出存档
 * （2026-10-05 服务器环境实测崩溃）。
 * 这还只是我们两支 jar 的时序差异；换成「客户端装了几个纯客户端模组」同样会错位。
 * <b>铁律：不要往原版实体（尤其原版 {@code Phantom}）的同步数据表里加东西。</b>
 *
 * <p><b>现在的口径</b>：标记与时效全在<b>服务端</b>（{@code persistentData} 本来就是服务端专属），
 * 渐隐不透明度改由 S2C 包（{@code SwarmPhantomPacket}）把「还剩多少刻」发给追踪该实体的客户端，
 * 客户端用 {@link #alphaFor(long)} 自己算——区块重载 / 后加入 / 传送都由
 * {@code PlayerEvent.StartTracking} 重发，一个同步槽都不需要。
 */
public final class SwarmPhantomFade {

    /** 存续时长：5 秒 */
    public static final int DURATION_TICKS = 100;
    /** 末尾渐隐窗口：1.5 秒，从完全不透明线性降到完全透明 */
    public static final int FADE_TICKS = 30;
    /** 不透明 */
    public static final float OPAQUE = 1.0F;

    /** 到期刻（{@code level.getServer().getTickCount()} 口径）——服务端专属，不同步 */
    private static final String TAG_EXPIRE = "lensouls:swarm_expire";

    private SwarmPhantomFade() {}

    /** 打上「本模组召唤 + 何时到期」的服务端标记。必须在 {@code addFreshEntity} 之前调用。 */
    public static void mark(Phantom phantom, long expireTick) {
        phantom.getPersistentData().putLong(TAG_EXPIRE, expireTick);
    }

    /** 是否本模组召唤的幻翼。<b>仅服务端可读</b>（persistentData 不同步），客户端走缓存。 */
    public static boolean isSwarm(Phantom phantom) {
        return phantom.getPersistentData().contains(TAG_EXPIRE);
    }

    /** 服务端：该幻翼的到期刻 */
    public static long expireTick(Phantom phantom) {
        return phantom.getPersistentData().getLong(TAG_EXPIRE);
    }

    /**
     * 余命（刻）→ 不透明度。<b>双端同一口径</b>：服务端在发包那一刻算余命，客户端拿到余命后本地自算，
     * 所以整段渐隐（1.5 秒）一个 tick 一个包都不需要发。
     */
    public static float alphaFor(long remainingTicks) {
        return remainingTicks >= FADE_TICKS
                ? OPAQUE
                : Math.max(0.0F, remainingTicks / (float) FADE_TICKS);
    }
}
