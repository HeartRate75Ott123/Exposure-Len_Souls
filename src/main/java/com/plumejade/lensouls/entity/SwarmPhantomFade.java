package com.plumejade.lensouls.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Phantom;

/**
 * 幻术师照片召唤的「幻影幻翼」的存续时长与渐隐不透明度。
 *
 * <p>时长与渐隐<b>都在服务端算</b>，逐 tick 写进 {@link Phantom} 的同步数据槽，
 * 客户端渲染时直接读——不再用 {@code tickCount} 之类客户端推算，因为：
 * <ul>
 *   <li>{@code getPersistentData()} <b>不会同步到客户端</b>，客户端无法据此认出「这是自家召唤的幻翼」；</li>
 *   <li>客户端实体在区块重载后会被重建，{@code tickCount} 归零 ⇒ 推算出来的淡入淡出会中途复活成不透明；</li>
 *   <li>同步数据槽随生成包一起下发，重载后也能拿到当前值，任何时刻都与服务端的真实余命一致。</li>
 * </ul>
 *
 * <p>{@code SynchedEntityData.set} 内部会先比对旧值、只在变化时才标脏，
 * 所以「每 tick 都写」不会产生 100×N 个包——不透明阶段一个包都不发，
 * 真正会同步的只有末尾 {@link #FADE_TICKS} 个 tick。
 *
 * <p>数据槽的两个 {@code defineId} 放在本类（普通类，不是 mixin）里是刻意的：
 * mixin 类本身不会作为运行时类被初始化，静态字段引用不到；
 * 而注册动作由 {@code PhantomFadeSyncMixin} 注入 {@code defineSynchedData} 完成。
 */
public final class SwarmPhantomFade {

    /** 存续时长：5 秒 */
    public static final int DURATION_TICKS = 100;
    /** 末尾渐隐窗口：1.5 秒，从完全不透明线性降到完全透明 */
    public static final int FADE_TICKS = 30;
    /** 不透明 */
    public static final float OPAQUE = 1.0F;

    /** 是否本模组召唤的幻翼（同步） */
    public static final EntityDataAccessor<Boolean> SWARM =
            SynchedEntityData.defineId(Phantom.class, EntityDataSerializers.BOOLEAN);
    /** 当前不透明度 0~1（同步） */
    public static final EntityDataAccessor<Float> ALPHA =
            SynchedEntityData.defineId(Phantom.class, EntityDataSerializers.FLOAT);

    private SwarmPhantomFade() {}

    /** 打标记并置为不透明。必须在 {@code addFreshEntity} 之前调用，让生成包就带上这两个槽。 */
    public static void mark(Phantom phantom) {
        phantom.getEntityData().set(SWARM, true);
        phantom.getEntityData().set(ALPHA, OPAQUE);
    }

    public static boolean isSwarm(Phantom phantom) {
        return phantom.getEntityData().get(SWARM);
    }

    /**
     * 供渲染层查询：<b>不是</b>自家幻翼时返回 {@code null}。
     * <p>
     * 必须用 {@code null} 而不是 {@code 1.0F} 之外的值来表达「不是我们的」——
     * 否则原版幻翼也会被套上半透明渲染管线（把 cutout 换成 translucent 会改变混合与排序行为）。
     */
    public static Float alphaOrNull(Entity entity) {
        if (!(entity instanceof Phantom phantom)) return null;
        return isSwarm(phantom) ? phantom.getEntityData().get(ALPHA) : null;
    }

    /** 服务端每 tick 写入：余命进入 {@link #FADE_TICKS} 后线性渐隐，之前保持完全不透明。 */
    public static void tickAlpha(Phantom phantom, int remainingTicks) {
        float alpha = remainingTicks >= FADE_TICKS
                ? OPAQUE
                : Math.max(0.0F, remainingTicks / (float) FADE_TICKS);
        phantom.getEntityData().set(ALPHA, alpha);
    }
}
